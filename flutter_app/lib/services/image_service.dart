import 'dart:developer' as developer;
import 'dart:io';
import 'dart:typed_data';
import 'package:dio/dio.dart';
import 'package:flutter_image_compress/flutter_image_compress.dart';
import 'package:image_picker/image_picker.dart';
import 'package:path/path.dart' as p;

/// Represents a processed and compressed photo ready for multipart upload
class CompressedPhotoItem {
  final String id;
  final String path;
  final String name;
  final Uint8List bytes;
  final int sizeBytes;
  final int originalSizeBytes;
  bool isPrimary;

  CompressedPhotoItem({
    required this.id,
    required this.path,
    required this.name,
    required this.bytes,
    required this.sizeBytes,
    required this.originalSizeBytes,
    this.isPrimary = false,
  });

  /// Human-readable file size format
  String get formattedSize {
    if (sizeBytes < 1024) {
      return '$sizeBytes B';
    } else if (sizeBytes < 1024 * 1024) {
      return '${(sizeBytes / 1024).toStringAsFixed(1)} KB';
    } else {
      return '${(sizeBytes / (1024 * 1024)).toStringAsFixed(2)} MB';
    }
  }

  /// Whether image is strictly under 1MB (1,048,576 bytes)
  bool get isUnder1MB => sizeBytes <= 1024 * 1024;

  /// Convert to Dio MultipartFile for atomic multipart upload
  MultipartFile toMultipartFile() {
    return MultipartFile.fromBytes(
      bytes,
      filename: name,
    );
  }
}

/// Abstract Interface for Image Service
abstract class IImageService {
  Future<CompressedPhotoItem?> pickAndCompressFromCamera();
  Future<List<CompressedPhotoItem>> pickAndCompressFromGallery({int maxPhotos = 5});
  Future<CompressedPhotoItem> compressFile(
    File file, {
    int targetMaxBytes = 1024 * 1024,
    int initialQuality = 85,
  });
  Future<CompressedPhotoItem> compressBytes(
    Uint8List bytes, {
    String filename = 'photo.jpg',
    int targetMaxBytes = 1024 * 1024,
    int initialQuality = 85,
  });
}

/// Concrete Image Service implementing client-side compression (< 1MB)
class ImageService implements IImageService {
  final ImagePicker _picker;

  ImageService({ImagePicker? picker}) : _picker = picker ?? ImagePicker();

  static const int maxUploadSizeBytes = 1024 * 1024; // 1 MB (1048576 bytes)
  static const int maxImageDimension = 1920;

  @override
  Future<CompressedPhotoItem?> pickAndCompressFromCamera() async {
    try {
      final picked = await _picker.pickImage(
        source: ImageSource.camera,
        maxWidth: maxImageDimension.toDouble(),
        maxHeight: maxImageDimension.toDouble(),
        imageQuality: 90,
      );

      if (picked == null) return null;

      final file = File(picked.path);
      return await compressFile(file);
    } catch (e) {
      developer.log('Error capturing photo from camera: $e', name: 'ImageService');
      rethrow;
    }
  }

  @override
  Future<List<CompressedPhotoItem>> pickAndCompressFromGallery({
    int maxPhotos = 5,
  }) async {
    try {
      final pickedList = await _picker.pickMultiImage(
        maxWidth: maxImageDimension.toDouble(),
        maxHeight: maxImageDimension.toDouble(),
        imageQuality: 90,
      );

      if (pickedList.isEmpty) return [];

      final limitedList = pickedList.take(maxPhotos).toList();
      final List<CompressedPhotoItem> results = [];

      for (int i = 0; i < limitedList.length; i++) {
        final file = File(limitedList[i].path);
        final item = await compressFile(file);
        if (i == 0) {
          item.isPrimary = true;
        }
        results.add(item);
      }

      return results;
    } catch (e) {
      developer.log('Error picking photos from gallery: $e', name: 'ImageService');
      rethrow;
    }
  }

  @override
  Future<CompressedPhotoItem> compressFile(
    File file, {
    int targetMaxBytes = maxUploadSizeBytes,
    int initialQuality = 85,
  }) async {
    final originalBytes = await file.readAsBytes();
    final originalSize = originalBytes.lengthInBytes;
    final filename = p.basename(file.path);

    // If file is already under target size and below max dimension, check if compression needed
    if (originalSize <= targetMaxBytes && originalSize > 0) {
      try {
        final compressedBytes = await _tryNativeCompression(
          file.path,
          quality: initialQuality,
        );
        final finalBytes = compressedBytes ?? originalBytes;
        return CompressedPhotoItem(
          id: DateTime.now().microsecondsSinceEpoch.toString(),
          path: file.path,
          name: filename,
          bytes: finalBytes,
          sizeBytes: finalBytes.lengthInBytes,
          originalSizeBytes: originalSize,
        );
      } catch (_) {
        return CompressedPhotoItem(
          id: DateTime.now().microsecondsSinceEpoch.toString(),
          path: file.path,
          name: filename,
          bytes: originalBytes,
          sizeBytes: originalSize,
          originalSizeBytes: originalSize,
        );
      }
    }

    // Iteratively compress with decreasing quality until size < 1MB
    int currentQuality = initialQuality;
    Uint8List? bestBytes;

    while (currentQuality >= 30) {
      try {
        final result = await _tryNativeCompression(
          file.path,
          quality: currentQuality,
        );
        if (result != null) {
          bestBytes = result;
          if (result.lengthInBytes <= targetMaxBytes) {
            break;
          }
        }
      } catch (e) {
        developer.log('Native compression error: $e', name: 'ImageService');
        break;
      }
      currentQuality -= 15;
    }

    final finalBytes = bestBytes ?? originalBytes;

    return CompressedPhotoItem(
      id: DateTime.now().microsecondsSinceEpoch.toString(),
      path: file.path,
      name: filename,
      bytes: finalBytes,
      sizeBytes: finalBytes.lengthInBytes,
      originalSizeBytes: originalSize,
    );
  }

  @override
  Future<CompressedPhotoItem> compressBytes(
    Uint8List bytes, {
    String filename = 'photo.jpg',
    int targetMaxBytes = maxUploadSizeBytes,
    int initialQuality = 85,
  }) async {
    final originalSize = bytes.lengthInBytes;

    if (originalSize <= targetMaxBytes) {
      return CompressedPhotoItem(
        id: DateTime.now().microsecondsSinceEpoch.toString(),
        path: '',
        name: filename,
        bytes: bytes,
        sizeBytes: originalSize,
        originalSizeBytes: originalSize,
      );
    }

    int currentQuality = initialQuality;
    Uint8List bestBytes = bytes;

    while (currentQuality >= 30) {
      try {
        final result = await FlutterImageCompress.compressWithList(
          bytes,
          minWidth: maxImageDimension,
          minHeight: maxImageDimension,
          quality: currentQuality,
          format: CompressFormat.jpeg,
        );

        bestBytes = result;
        if (result.lengthInBytes <= targetMaxBytes) {
          break;
        }
      } catch (e) {
        developer.log('compressBytes error: $e', name: 'ImageService');
        break;
      }
      currentQuality -= 15;
    }

    return CompressedPhotoItem(
      id: DateTime.now().microsecondsSinceEpoch.toString(),
      path: '',
      name: filename,
      bytes: bestBytes,
      sizeBytes: bestBytes.lengthInBytes,
      originalSizeBytes: originalSize,
    );
  }

  Future<Uint8List?> _tryNativeCompression(
    String filePath, {
    required int quality,
  }) async {
    try {
      return await FlutterImageCompress.compressWithFile(
        filePath,
        minWidth: maxImageDimension,
        minHeight: maxImageDimension,
        quality: quality,
        format: CompressFormat.jpeg,
      );
    } catch (e) {
      return null;
    }
  }
}
