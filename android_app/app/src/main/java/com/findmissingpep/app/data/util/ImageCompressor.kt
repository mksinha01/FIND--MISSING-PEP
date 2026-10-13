package com.findmissingpep.app.data.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.max

/**
 * Robust image compressor with:
 * - Single-pass stream reading into cache (eliminates closed/pipe stream crashes)
 * - Proportional downscaling to max 1280px
 * - 85% JPEG compression quality
 * - EXIF orientation correction
 * - Memory-safe decoding using inSampleSize
 * - Bulletproof fallback: returns raw file if bitmap decompression fails
 */
object ImageCompressor {

    private const val TAG = "ImageCompressor"
    private const val MAX_DIMENSION = 1280
    private const val QUALITY = 85

    suspend fun compress(context: Context, uri: Uri): File = withContext(Dispatchers.IO) {
        // Step 1: Read content URI ONCE into a local temporary file in cacheDir
        val rawTempFile = File.createTempFile("raw_photo_", ".jpg", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(rawTempFile).use { output ->
                    input.copyTo(output)
                }
            } ?: throw IOException("Cannot open image stream for $uri")

            if (rawTempFile.length() == 0L) {
                throw IOException("Copied image file is empty")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy image stream for $uri", e)
            rawTempFile.delete()
            throw e
        }

        // Step 2: Compress from the local file
        return@withContext compressLocalFile(context, rawTempFile)
    }

    suspend fun compressLocalFile(context: Context, rawFile: File): File = withContext(Dispatchers.IO) {
        try {
            // Read EXIF orientation directly from local file
            val orientation = try {
                val exif = ExifInterface(rawFile.absolutePath)
                exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } catch (e: Exception) {
                Log.w(TAG, "Could not read EXIF orientation", e)
                ExifInterface.ORIENTATION_NORMAL
            }

            // Decode bounds only
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(rawFile.absolutePath, options)

            val origWidth = options.outWidth
            val origHeight = options.outHeight
            if (origWidth <= 0 || origHeight <= 0) {
                Log.w(TAG, "Invalid dimensions from bounds decode: ${origWidth}x${origHeight}. Returning raw file.")
                return@withContext rawFile
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
            val sampledBitmap = BitmapFactory.decodeFile(rawFile.absolutePath, decodeOptions)
                ?: run {
                    Log.w(TAG, "BitmapFactory returned null for ${rawFile.name}. Returning raw file fallback.")
                    return@withContext rawFile
                }

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

            // Delete raw temp file if different
            if (rawFile.exists() && rawFile.absolutePath != outputFile.absolutePath) {
                rawFile.delete()
            }

            outputFile
        } catch (e: Exception) {
            Log.e(TAG, "Compression error on ${rawFile.name}: ${e.message}. Using raw file fallback.", e)
            // Never fail upload: return raw file if compression encountered any issue
            rawFile
        }
    }
}
