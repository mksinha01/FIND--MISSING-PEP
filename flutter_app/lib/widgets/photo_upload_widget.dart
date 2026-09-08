import 'dart:typed_data';
import 'package:flutter/material.dart';
import '../config/theme.dart';
import '../services/image_service.dart';

/// Interactive Multi-Photo Upload & Compression Widget (1 to 5 Photos)
class PhotoUploadWidget extends StatefulWidget {
  final List<CompressedPhotoItem> initialPhotos;
  final ValueChanged<List<CompressedPhotoItem>> onPhotosChanged;
  final IImageService? imageService;
  final bool isHindi;
  final String? errorText;

  const PhotoUploadWidget({
    super.key,
    this.initialPhotos = const [],
    required this.onPhotosChanged,
    this.imageService,
    this.isHindi = false,
    this.errorText,
  });

  @override
  State<PhotoUploadWidget> createState() => _PhotoUploadWidgetState();
}

class _PhotoUploadWidgetState extends State<PhotoUploadWidget> {
  late final IImageService _imageService;
  late List<CompressedPhotoItem> _photos;
  bool _isCompressing = false;

  static const int maxPhotos = 5;

  @override
  void initState() {
    super.initState();
    _imageService = widget.imageService ?? ImageService();
    _photos = List<CompressedPhotoItem>.from(widget.initialPhotos);
    _ensurePrimaryPhoto();
  }

  void _ensurePrimaryPhoto() {
    if (_photos.isNotEmpty && !_photos.any((p) => p.isPrimary)) {
      _photos.first.isPrimary = true;
    }
  }

  void _setPrimary(int index) {
    setState(() {
      for (int i = 0; i < _photos.length; i++) {
        _photos[i].isPrimary = (i == index);
      }
    });
    widget.onPhotosChanged(_photos);
  }

  void _removePhoto(int index) {
    setState(() {
      final removed = _photos.removeAt(index);
      if (removed.isPrimary && _photos.isNotEmpty) {
        _photos.first.isPrimary = true;
      }
    });
    widget.onPhotosChanged(_photos);
  }

  Future<void> _pickFromCamera() async {
    if (_photos.length >= maxPhotos) return;

    setState(() => _isCompressing = true);
    try {
      final photo = await _imageService.pickAndCompressFromCamera();
      if (photo != null) {
        setState(() {
          if (_photos.isEmpty) {
            photo.isPrimary = true;
          }
          _photos.add(photo);
        });
        widget.onPhotosChanged(_photos);
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              widget.isHindi
                  ? 'कैमरा से फोटो लेने में त्रुटि हुई'
                  : 'Failed to capture photo from camera: $e',
            ),
            backgroundColor: AppTheme.errorRed,
          ),
        );
      }
    } finally {
      if (mounted) {
        setState(() => _isCompressing = false);
      }
    }
  }

  Future<void> _pickFromGallery() async {
    final remainingSlots = maxPhotos - _photos.length;
    if (remainingSlots <= 0) return;

    setState(() => _isCompressing = true);
    try {
      final newPhotos = await _imageService.pickAndCompressFromGallery(
        maxPhotos: remainingSlots,
      );

      if (newPhotos.isNotEmpty) {
        setState(() {
          if (_photos.isEmpty && newPhotos.isNotEmpty) {
            newPhotos.first.isPrimary = true;
          }
          _photos.addAll(newPhotos);
        });
        widget.onPhotosChanged(_photos);
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              widget.isHindi
                  ? 'गैलरी से फोटो चुनने में त्रुटि हुई'
                  : 'Failed to pick photos from gallery: $e',
            ),
            backgroundColor: AppTheme.errorRed,
          ),
        );
      }
    } finally {
      if (mounted) {
        setState(() => _isCompressing = false);
      }
    }
  }

  void _showPickerBottomSheet() {
    showModalBottomSheet(
      context: context,
      backgroundColor: AppTheme.darkSurface,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (ctx) {
        return SafeArea(
          child: Padding(
            padding: const EdgeInsets.symmetric(vertical: 20, horizontal: 16),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Container(
                  width: 40,
                  height: 4,
                  margin: const EdgeInsets.only(bottom: 20),
                  decoration: BoxDecoration(
                    color: AppTheme.darkCardBorder,
                    borderRadius: BorderRadius.circular(2),
                  ),
                ),
                Text(
                  widget.isHindi ? 'फोटो जोड़ें' : 'Add Photo',
                  style: const TextStyle(
                    fontSize: 18,
                    fontWeight: FontWeight.bold,
                    color: AppTheme.darkTextPrimary,
                  ),
                ),
                const SizedBox(height: 16),
                ListTile(
                  leading: Container(
                    padding: const EdgeInsets.all(10),
                    decoration: BoxDecoration(
                      color: AppTheme.primaryBlue.withOpacity(0.2),
                      borderRadius: BorderRadius.circular(10),
                    ),
                    child: const Icon(Icons.camera_alt, color: AppTheme.primaryLight),
                  ),
                  title: Text(
                    widget.isHindi ? 'कैमरे से फोटो लें' : 'Take Photo with Camera',
                    style: const TextStyle(fontWeight: FontWeight.w600),
                  ),
                  subtitle: Text(
                    widget.isHindi
                        ? 'लाइव फोटो खींचें और ऑटो-कंप्रेस करें'
                        : 'Capture portrait & auto-compress to <1MB',
                    style: const TextStyle(color: AppTheme.darkTextSecondary, fontSize: 12),
                  ),
                  onTap: () {
                    Navigator.pop(ctx);
                    _pickFromCamera();
                  },
                ),
                const SizedBox(height: 8),
                ListTile(
                  leading: Container(
                    padding: const EdgeInsets.all(10),
                    decoration: BoxDecoration(
                      color: AppTheme.secondaryCyan.withOpacity(0.2),
                      borderRadius: BorderRadius.circular(10),
                    ),
                    child: const Icon(Icons.photo_library, color: AppTheme.secondaryCyan),
                  ),
                  title: Text(
                    widget.isHindi ? 'गैलरी से चुनें' : 'Choose from Gallery',
                    style: const TextStyle(fontWeight: FontWeight.w600),
                  ),
                  subtitle: Text(
                    widget.isHindi
                        ? '1 से ${maxPhotos - _photos.length} तस्वीरें चुनें'
                        : 'Select up to ${maxPhotos - _photos.length} photos',
                    style: const TextStyle(color: AppTheme.darkTextSecondary, fontSize: 12),
                  ),
                  onTap: () {
                    Navigator.pop(ctx);
                    _pickFromGallery();
                  },
                ),
              ],
            ),
          ),
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        // Section Header Row
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Row(
              children: [
                const Icon(Icons.photo_camera_back, size: 20, color: AppTheme.primaryLight),
                const SizedBox(width: 8),
                Text(
                  widget.isHindi ? 'तस्वीरें (1 से 5 फोटो)' : 'Photographs (1 to 5 Photos)',
                  style: const TextStyle(
                    fontSize: 15,
                    fontWeight: FontWeight.bold,
                    color: AppTheme.darkTextPrimary,
                  ),
                ),
              ],
            ),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
              decoration: BoxDecoration(
                color: _photos.isEmpty
                    ? AppTheme.errorRed.withOpacity(0.2)
                    : AppTheme.primaryBlue.withOpacity(0.2),
                borderRadius: BorderRadius.circular(12),
              ),
              child: Text(
                '${_photos.length} / $maxPhotos',
                style: TextStyle(
                  fontSize: 12,
                  fontWeight: FontWeight.w700,
                  color: _photos.isEmpty ? AppTheme.errorRed : AppTheme.primaryLight,
                ),
              ),
            ),
          ],
        ),
        const SizedBox(height: 6),
        Text(
          widget.isHindi
              ? 'स्पष्ट सामने की तस्वीरें अपलोड करें। स्टार (★) वाली तस्वीर प्राथमिक संदर्भ होगी।'
              : 'Upload 1-5 clear frontal photos. Starred (★) image is the primary biometric reference.',
          style: const TextStyle(
            fontSize: 12,
            color: AppTheme.darkTextSecondary,
            height: 1.3,
          ),
        ),
        const SizedBox(height: 14),

        // Photo Thumbnails Strip
        SizedBox(
          height: 140,
          child: ListView.separated(
            scrollDirection: Axis.horizontal,
            itemCount: _photos.length + (_photos.length < maxPhotos ? 1 : 0),
            separatorBuilder: (context, index) => const SizedBox(width: 12),
            itemBuilder: (context, index) {
              if (index < _photos.length) {
                return _buildPhotoThumbnail(index);
              } else {
                return _buildAddPhotoButton();
              }
            },
          ),
        ),

        // Error text if validation fails
        if (widget.errorText != null) ...[
          const SizedBox(height: 8),
          Row(
            children: [
              const Icon(Icons.error_outline, size: 14, color: AppTheme.errorRed),
              const SizedBox(width: 6),
              Expanded(
                child: Text(
                  widget.errorText!,
                  style: const TextStyle(color: AppTheme.errorRed, fontSize: 12),
                ),
              ),
            ],
          ),
        ],

        // Compression Spinner Indicator
        if (_isCompressing) ...[
          const SizedBox(height: 10),
          Row(
            children: [
              const SizedBox(
                width: 16,
                height: 16,
                child: CircularProgressIndicator(strokeWidth: 2, color: AppTheme.primaryLight),
              ),
              const SizedBox(width: 10),
              Text(
                widget.isHindi
                    ? 'फोटो कंप्रेस की जा रही है (<1MB)...'
                    : 'Compressing image to <1MB...',
                style: const TextStyle(color: AppTheme.primaryLight, fontSize: 12),
              ),
            ],
          ),
        ],
      ],
    );
  }

  Widget _buildPhotoThumbnail(int index) {
    final photo = _photos[index];

    return Container(
      width: 110,
      decoration: BoxDecoration(
        color: AppTheme.darkCard,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(
          color: photo.isPrimary ? AppTheme.primaryLight : AppTheme.darkCardBorder,
          width: photo.isPrimary ? 2.0 : 1.0,
        ),
      ),
      clipBehavior: Clip.antiAlias,
      child: Stack(
        fit: StackFit.expand,
        children: [
          // Image Preview
          Image.memory(
            photo.bytes,
            fit: BoxFit.cover,
            errorBuilder: (_, __, ___) => Container(
              color: AppTheme.darkSurface,
              child: const Icon(Icons.broken_image, color: AppTheme.darkTextSecondary),
            ),
          ),

          // Top Gradient Overlay for action buttons
          Positioned(
            top: 0,
            left: 0,
            right: 0,
            height: 36,
            child: Container(
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  colors: [Colors.black.withOpacity(0.7), Colors.transparent],
                  begin: Alignment.topCenter,
                  end: Alignment.bottomCenter,
                ),
              ),
            ),
          ),

          // Primary Photo Star / Selector Button
          Positioned(
            top: 4,
            left: 4,
            child: GestureDetector(
              onTap: () => _setPrimary(index),
              child: Container(
                padding: const EdgeInsets.all(4),
                decoration: BoxDecoration(
                  color: photo.isPrimary ? AppTheme.accentAmber : Colors.black54,
                  shape: BoxShape.circle,
                ),
                child: Icon(
                  photo.isPrimary ? Icons.star : Icons.star_border,
                  size: 14,
                  color: photo.isPrimary ? Colors.black : Colors.white,
                ),
              ),
            ),
          ),

          // Delete Button
          Positioned(
            top: 4,
            right: 4,
            child: GestureDetector(
              onTap: () => _removePhoto(index),
              child: Container(
                padding: const EdgeInsets.all(4),
                decoration: const BoxDecoration(
                  color: Colors.black54,
                  shape: BoxShape.circle,
                ),
                child: const Icon(Icons.close, size: 14, color: Colors.white),
              ),
            ),
          ),

          // Bottom Bar with File Size & Primary Label
          Positioned(
            bottom: 0,
            left: 0,
            right: 0,
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 3),
              decoration: BoxDecoration(
                color: Colors.black.withOpacity(0.75),
              ),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  if (photo.isPrimary)
                    Text(
                      widget.isHindi ? '★ मुख्य संदर्भ' : '★ PRIMARY',
                      style: const TextStyle(
                        color: AppTheme.accentAmber,
                        fontSize: 9,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                  Text(
                    photo.formattedSize,
                    style: const TextStyle(
                      color: Colors.white70,
                      fontSize: 10,
                      fontWeight: FontWeight.w500,
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildAddPhotoButton() {
    return InkWell(
      onTap: _isCompressing ? null : _showPickerBottomSheet,
      borderRadius: BorderRadius.circular(14),
      child: Container(
        width: 110,
        decoration: BoxDecoration(
          color: AppTheme.darkCard.withOpacity(0.5),
          borderRadius: BorderRadius.circular(14),
          border: Border.all(
            color: AppTheme.primaryBlue.withOpacity(0.5),
            style: BorderStyle.solid,
            width: 1.5,
          ),
        ),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Container(
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(
                color: AppTheme.primaryBlue.withOpacity(0.2),
                shape: BoxShape.circle,
              ),
              child: const Icon(Icons.add_a_photo_outlined, color: AppTheme.primaryLight, size: 22),
            ),
            const SizedBox(height: 8),
            Text(
              widget.isHindi ? 'फोटो जोड़ें' : 'Add Photo',
              style: const TextStyle(
                color: AppTheme.primaryLight,
                fontSize: 12,
                fontWeight: FontWeight.w600,
              ),
            ),
            const SizedBox(height: 2),
            Text(
              widget.isHindi ? 'अधिकतम 5' : 'Max 5',
              style: const TextStyle(
                color: AppTheme.darkTextSecondary,
                fontSize: 10,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
