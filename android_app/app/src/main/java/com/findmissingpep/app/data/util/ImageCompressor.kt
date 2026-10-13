package com.findmissingpep.app.data.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.max

/**
 * Image compressor matching the web app's imageService.ts:
 * - Proportional downscaling to max 1280px
 * - 85% JPEG compression quality
 * - EXIF orientation correction
 * - Memory-safe decoding using inSampleSize
 */
object ImageCompressor {

    private const val MAX_DIMENSION = 1280
    private const val QUALITY = 85

    suspend fun compress(context: Context, uri: Uri): File = withContext(Dispatchers.IO) {
        // Read EXIF orientation
        val orientation = try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }

        // Decode bounds only
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        } ?: throw IOException("Cannot open image stream for $uri")

        val origWidth = options.outWidth
        val origHeight = options.outHeight
        if (origWidth <= 0 || origHeight <= 0) {
            throw IOException("Invalid image dimensions")
        }

        // Calculate sample size for memory-safe decoding
        var sampleSize = 1
        val maxDim = max(origWidth, origHeight)
        while (maxDim / (sampleSize * 2) >= MAX_DIMENSION) {
            sampleSize *= 2
        }

        // Decode sampled bitmap
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
        }
        val sampledBitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: throw IOException("Failed to decode bitmap")

        // Exact scaling & rotation matrix
        val currentMax = max(sampledBitmap.width, sampledBitmap.height)
        val scale = if (currentMax > MAX_DIMENSION) {
            MAX_DIMENSION.toFloat() / currentMax.toFloat()
        } else {
            1.0f
        }

        val matrix = Matrix().apply {
            if (scale < 1.0f) {
                postScale(scale, scale)
            }
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
            }
        }

        val finalBitmap = Bitmap.createBitmap(
            sampledBitmap, 0, 0, sampledBitmap.width, sampledBitmap.height, matrix, true
        )
        if (finalBitmap != sampledBitmap) {
            sampledBitmap.recycle()
        }

        // Save compressed file to cache directory
        val outputFile = File.createTempFile("comp_photo_", ".jpg", context.cacheDir)
        FileOutputStream(outputFile).use { out ->
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
        }
        finalBitmap.recycle()

        outputFile
    }
}
