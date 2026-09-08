import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import '../config/constants.dart';
import '../config/theme.dart';
import '../models/enums.dart';
import '../providers/auth_provider.dart';
import '../providers/reports_provider.dart';
import '../services/image_service.dart';
import '../widgets/photo_upload_widget.dart';

/// Missing Person Report Submission Form Screen
/// Implements Architectural Fix #18: Atomic multipart submission (metadata + 1-5 photos)
class ReportFormScreen extends ConsumerStatefulWidget {
  const ReportFormScreen({super.key});

  @override
  ConsumerState<ReportFormScreen> createState() => _ReportFormScreenState();
}

class _ReportFormScreenState extends ConsumerState<ReportFormScreen> {
  final _formKey = GlobalKey<FormState>();

  // Text Form Controllers
  final _nameController = TextEditingController();
  final _ageController = TextEditingController();
  final _heightController = TextEditingController();
  final _descriptionController = TextEditingController();
  final _locationController = TextEditingController();
  final _contactController = TextEditingController();

  // Form State
  Gender _selectedGender = Gender.male;
  DateTime _lastSeenDateTime = DateTime.now().subtract(const Duration(hours: 2));
  List<CompressedPhotoItem> _pickedPhotos = [];
  String? _photosError;
  bool _isSubmitting = false;

  @override
  void dispose() {
    _nameController.dispose();
    _ageController.dispose();
    _heightController.dispose();
    _descriptionController.dispose();
    _locationController.dispose();
    _contactController.dispose();
    super.dispose();
  }

  Future<void> _pickLastSeenDate() async {
    final pickedDate = await showDatePicker(
      context: context,
      initialDate: _lastSeenDateTime,
      firstDate: DateTime.now().subtract(const Duration(days: 365)),
      lastDate: DateTime.now(),
      builder: (context, child) {
        return Theme(
          data: AppTheme.darkTheme.copyWith(
            colorScheme: const ColorScheme.dark(
              primary: AppTheme.primaryLight,
              surface: AppTheme.darkSurface,
            ),
          ),
          child: child!,
        );
      },
    );

    if (pickedDate != null) {
      if (!mounted) return;
      final pickedTime = await showTimePicker(
        context: context,
        initialTime: TimeOfDay.fromDateTime(_lastSeenDateTime),
        builder: (context, child) {
          return Theme(
            data: AppTheme.darkTheme.copyWith(
              colorScheme: const ColorScheme.dark(
                primary: AppTheme.primaryLight,
                surface: AppTheme.darkSurface,
              ),
            ),
            child: child!,
          );
        },
      );

      if (pickedTime != null) {
        setState(() {
          _lastSeenDateTime = DateTime(
            pickedDate.year,
            pickedDate.month,
            pickedDate.day,
            pickedTime.hour,
            pickedTime.minute,
          );
        });
      }
    }
  }

  Future<void> _submitReport(bool isHindi) async {
    // 1. Validate form fields
    if (!_formKey.currentState!.validate()) {
      return;
    }

    // 2. Validate photos (minimum 1 required)
    if (_pickedPhotos.isEmpty) {
      setState(() {
        _photosError = isHindi
            ? 'कृपया कम से कम एक तस्वीर संलग्न करें।'
            : 'Please attach at least one photo of the missing person.';
      });
      return;
    } else {
      setState(() {
        _photosError = null;
      });
    }

    setState(() => _isSubmitting = true);

    // Show loading progress modal
    _showProgressDialog(isHindi);

    try {
      // 3. Assemble JSON metadata payload
      final metadata = {
        'full_name': _nameController.text.trim(),
        'age': int.tryParse(_ageController.text.trim()),
        'gender': _selectedGender.value,
        if (_heightController.text.trim().isNotEmpty)
          'height_cm': int.tryParse(_heightController.text.trim()),
        if (_descriptionController.text.trim().isNotEmpty)
          'description': _descriptionController.text.trim(),
        'last_seen_location': _locationController.text.trim(),
        'last_seen_time': _lastSeenDateTime.toUtc().toIso8601String(),
        'contact_info': _contactController.text.trim(),
      };

      // 4. Send atomic multipart request via Riverpod provider
      final createdReport = await ref
          .read(reportsProvider.notifier)
          .submitReportAtomic(
            metadata: metadata,
            photos: _pickedPhotos,
          );

      if (mounted) {
        Navigator.of(context, rootNavigator: true).pop(); // Close progress dialog

        // Show success snackbar
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Row(
              children: [
                const Icon(Icons.check_circle, color: AppTheme.successGreen),
                const SizedBox(width: 10),
                Expanded(
                  child: Text(
                    isHindi
                        ? 'रिपोर्ट दर्ज हो गई! एआई निगरानी शुरू।'
                        : 'Report submitted! Edge AI biometric monitoring activated.',
                  ),
                ),
              ],
            ),
            backgroundColor: AppTheme.darkCard,
            duration: const Duration(seconds: 4),
          ),
        );

        // Navigate to created report detail
        context.pushReplacement(AppRoutes.reportDetailPath(createdReport.id));
      }
    } catch (e) {
      if (mounted) {
        Navigator.of(context, rootNavigator: true).pop(); // Close progress dialog

        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Row(
              children: [
                const Icon(Icons.error_outline, color: AppTheme.errorRed),
                const SizedBox(width: 10),
                Expanded(
                  child: Text(
                    isHindi
                        ? 'सबमिशन विफल: $e'
                        : 'Report submission failed: $e',
                  ),
                ),
              ],
            ),
            backgroundColor: AppTheme.darkCard,
            action: SnackBarAction(
              label: isHindi ? 'पुनः प्रयास' : 'Retry',
              textColor: AppTheme.primaryLight,
              onPressed: () => _submitReport(isHindi),
            ),
          ),
        );
      }
    } finally {
      if (mounted) {
        setState(() => _isSubmitting = false);
      }
    }
  }

  void _showProgressDialog(bool isHindi) {
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (ctx) {
        return PopScope(
          canPop: false,
          child: AlertDialog(
            backgroundColor: AppTheme.darkSurface,
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(20),
              side: const BorderSide(color: AppTheme.darkCardBorder),
            ),
            content: Padding(
              padding: const EdgeInsets.symmetric(vertical: 16),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const SizedBox(
                    width: 48,
                    height: 48,
                    child: CircularProgressIndicator(
                      strokeWidth: 3.5,
                      color: AppTheme.primaryLight,
                    ),
                  ),
                  const SizedBox(height: 20),
                  Text(
                    isHindi ? 'रिपोर्ट सबमिट हो रही है...' : 'Submitting Report...',
                    style: const TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.bold,
                      color: AppTheme.darkTextPrimary,
                    ),
                  ),
                  const SizedBox(height: 8),
                  Text(
                    isHindi
                        ? 'तस्वीरों को कंप्रेस कर 512-डी फेस एम्बेडिंग निकाली जा रही है...'
                        : 'Compressing photos (<1MB) & extracting 512-D facial embeddings...',
                    textAlign: TextAlign.center,
                    style: const TextStyle(
                      fontSize: 12,
                      color: AppTheme.darkTextSecondary,
                    ),
                  ),
                ],
              ),
            ),
          ),
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    final currentLang = ref.watch(currentLanguageProvider);
    final isHindi = currentLang == 'hi';

    return Scaffold(
      appBar: AppBar(
        title: Text(
          isHindi ? 'लापता व्यक्ति रिपोर्ट दर्ज करें' : 'File Missing Person Report',
        ),
        leading: IconButton(
          icon: const Icon(Icons.arrow_back),
          onPressed: () => context.pop(),
        ),
      ),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(16),
          child: Form(
            key: _formKey,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // Banner Note on AI Facial Recognition
                _buildAiBanner(isHindi),
                const SizedBox(height: 20),

                // ── SECTION 1: PHOTOGRAPHS UPLOAD ──
                Container(
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(
                    color: AppTheme.darkCard,
                    borderRadius: BorderRadius.circular(16),
                    border: Border.all(
                      color: _photosError != null
                          ? AppTheme.errorRed
                          : AppTheme.darkCardBorder,
                      width: _photosError != null ? 1.5 : 1.0,
                    ),
                  ),
                  child: PhotoUploadWidget(
                    initialPhotos: _pickedPhotos,
                    isHindi: isHindi,
                    errorText: _photosError,
                    onPhotosChanged: (photos) {
                      setState(() {
                        _pickedPhotos = photos;
                        if (photos.isNotEmpty) {
                          _photosError = null;
                        }
                      });
                    },
                  ),
                ),
                const SizedBox(height: 24),

                // ── SECTION 2: PERSONAL DETAILS ──
                _buildSectionTitle(
                  icon: Icons.person_outline,
                  title: isHindi ? 'व्यक्तिगत जानकारी' : 'Personal Details',
                ),
                const SizedBox(height: 12),

                // Full Name
                TextFormField(
                  controller: _nameController,
                  textCapitalization: TextCapitalization.words,
                  decoration: InputDecoration(
                    labelText: isHindi ? 'पूरा नाम *' : 'Full Name *',
                    hintText: isHindi ? 'उदा. रोहन गुप्ता' : 'e.g. Rohan Gupta',
                    prefixIcon: const Icon(Icons.person, color: AppTheme.darkTextSecondary),
                  ),
                  validator: (val) {
                    if (val == null || val.trim().isEmpty) {
                      return isHindi ? 'पूरा नाम आवश्यक है' : 'Full name is required';
                    }
                    if (val.trim().length < 2) {
                      return isHindi ? 'कम से कम 2 अक्षर' : 'Minimum 2 characters';
                    }
                    return null;
                  },
                ),
                const SizedBox(height: 14),

                // Age & Height Row
                Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    // Age
                    Expanded(
                      flex: 1,
                      child: TextFormField(
                        controller: _ageController,
                        keyboardType: TextInputType.number,
                        decoration: InputDecoration(
                          labelText: isHindi ? 'उम्र (वर्ष) *' : 'Age (years) *',
                          hintText: '14',
                          prefixIcon: const Icon(Icons.cake_outlined, color: AppTheme.darkTextSecondary),
                        ),
                        validator: (val) {
                          if (val == null || val.trim().isEmpty) {
                            return isHindi ? 'उम्र आवश्यक' : 'Age required';
                          }
                          final num = int.tryParse(val.trim());
                          if (num == null || num < 1 || num > 120) {
                            return isHindi ? '1-120 मान्य' : 'Valid (1-120)';
                          }
                          return null;
                        },
                      ),
                    ),
                    const SizedBox(width: 12),

                    // Height (cm)
                    Expanded(
                      flex: 1,
                      child: TextFormField(
                        controller: _heightController,
                        keyboardType: TextInputType.number,
                        decoration: InputDecoration(
                          labelText: isHindi ? 'कद (सेमी)' : 'Height (cm)',
                          hintText: '165',
                          prefixIcon: const Icon(Icons.height, color: AppTheme.darkTextSecondary),
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 14),

                // Gender Selection
                Text(
                  isHindi ? 'लिंग *' : 'Gender *',
                  style: const TextStyle(
                    fontSize: 13,
                    fontWeight: FontWeight.w600,
                    color: AppTheme.darkTextSecondary,
                  ),
                ),
                const SizedBox(height: 8),
                SegmentedButton<Gender>(
                  segments: [
                    ButtonSegment<Gender>(
                      value: Gender.male,
                      label: Text(isHindi ? 'पुरुष' : 'Male'),
                      icon: const Icon(Icons.male),
                    ),
                    ButtonSegment<Gender>(
                      value: Gender.female,
                      label: Text(isHindi ? 'महिला' : 'Female'),
                      icon: const Icon(Icons.female),
                    ),
                    ButtonSegment<Gender>(
                      value: Gender.other,
                      label: Text(isHindi ? 'अन्य' : 'Other'),
                      icon: const Icon(Icons.transgender),
                    ),
                  ],
                  selected: {_selectedGender},
                  onSelectionChanged: (newSet) {
                    setState(() => _selectedGender = newSet.first);
                  },
                  style: ButtonStyle(
                    backgroundColor: MaterialStateProperty.resolveWith((states) {
                      if (states.contains(MaterialState.selected)) {
                        return AppTheme.primaryBlue;
                      }
                      return AppTheme.darkSurface;
                    }),
                    foregroundColor: MaterialStateProperty.resolveWith((states) {
                      if (states.contains(MaterialState.selected)) {
                        return Colors.white;
                      }
                      return AppTheme.darkTextSecondary;
                    }),
                  ),
                ),
                const SizedBox(height: 14),

                // Description
                TextFormField(
                  controller: _descriptionController,
                  maxLines: 3,
                  decoration: InputDecoration(
                    labelText: isHindi ? 'पहचान चिह्न एवं कपड़े' : 'Distinctive Features & Clothing',
                    hintText: isHindi
                        ? 'पहने हुए कपड़े, तिल, निशान, आभूषण, भाषा...'
                        : 'Clothing worn, birthmarks, scars, accessories, language...',
                    alignLabelWithHint: true,
                    prefixIcon: const Padding(
                      padding: EdgeInsets.only(bottom: 40),
                      child: Icon(Icons.notes, color: AppTheme.darkTextSecondary),
                    ),
                  ),
                ),
                const SizedBox(height: 24),

                // ── SECTION 3: INCIDENT DETAILS ──
                _buildSectionTitle(
                  icon: Icons.access_time_filled,
                  title: isHindi ? 'घटना का विवरण' : 'Incident Details',
                ),
                const SizedBox(height: 12),

                // Last Seen Location
                TextFormField(
                  controller: _locationController,
                  textCapitalization: TextCapitalization.words,
                  decoration: InputDecoration(
                    labelText: isHindi ? 'अंतिम बार देखे जाने का स्थान *' : 'Last Seen Location *',
                    hintText: isHindi
                        ? 'उदा. सेक्टर 18 मेट्रो स्टेशन'
                        : 'e.g. Sector 18 Metro Station, Gate 2',
                    prefixIcon: const Icon(Icons.location_on, color: AppTheme.darkTextSecondary),
                  ),
                  validator: (val) {
                    if (val == null || val.trim().isEmpty) {
                      return isHindi ? 'स्थान आवश्यक है' : 'Last seen location is required';
                    }
                    return null;
                  },
                ),
                const SizedBox(height: 14),

                // Last Seen Date & Time Picker Button
                InkWell(
                  onTap: _pickLastSeenDate,
                  borderRadius: BorderRadius.circular(12),
                  child: Container(
                    padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
                    decoration: BoxDecoration(
                      color: AppTheme.darkSurface,
                      borderRadius: BorderRadius.circular(12),
                      border: Border.all(color: AppTheme.darkCardBorder),
                    ),
                    child: Row(
                      children: [
                        const Icon(Icons.calendar_today, color: AppTheme.primaryLight, size: 20),
                        const SizedBox(width: 12),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                isHindi ? 'अंतिम देखे जाने का समय व तारीख *' : 'Last Seen Date & Time *',
                                style: const TextStyle(
                                  fontSize: 11,
                                  color: AppTheme.darkTextSecondary,
                                ),
                              ),
                              const SizedBox(height: 2),
                              Text(
                                DateFormat('EEEE, dd MMMM yyyy, hh:mm a').format(_lastSeenDateTime),
                                style: const TextStyle(
                                  fontSize: 14,
                                  fontWeight: FontWeight.w600,
                                  color: AppTheme.darkTextPrimary,
                                ),
                              ),
                            ],
                          ),
                        ),
                        const Icon(Icons.edit, size: 16, color: AppTheme.darkTextSecondary),
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: 24),

                // ── SECTION 4: EMERGENCY CONTACT ──
                _buildSectionTitle(
                  icon: Icons.phone_in_talk,
                  title: isHindi ? 'आपातकालीन संपर्क' : 'Emergency Contact',
                ),
                const SizedBox(height: 12),

                // Contact Phone Number
                TextFormField(
                  controller: _contactController,
                  keyboardType: TextInputType.phone,
                  decoration: InputDecoration(
                    labelText: isHindi ? 'संपर्क नंबर *' : 'Emergency Contact Phone *',
                    hintText: '+91 9876543210',
                    prefixIcon: const Icon(Icons.phone, color: AppTheme.darkTextSecondary),
                  ),
                  validator: (val) {
                    if (val == null || val.trim().isEmpty) {
                      return isHindi ? 'संपर्क नंबर आवश्यक है' : 'Contact number is required';
                    }
                    if (val.trim().length < 8) {
                      return isHindi ? 'वैध फोन नंबर दर्ज करें' : 'Enter a valid phone number';
                    }
                    return null;
                  },
                ),
                const SizedBox(height: 32),

                // ── SUBMIT BUTTON ──
                ElevatedButton.icon(
                  style: ElevatedButton.styleFrom(
                    minimumSize: const Size(double.infinity, 54),
                    backgroundColor: AppTheme.primaryBlue,
                    elevation: 4,
                    shadowColor: AppTheme.primaryBlue.withOpacity(0.4),
                  ),
                  icon: _isSubmitting
                      ? const SizedBox(
                          width: 20,
                          height: 20,
                          child: CircularProgressIndicator(
                            strokeWidth: 2,
                            color: Colors.white,
                          ),
                        )
                      : const Icon(Icons.send_rounded),
                  label: Text(
                    isHindi ? 'रिपोर्ट दर्ज करें (एआई मॉनिटरिंग)' : 'Submit & Activate AI Scan',
                    style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
                  ),
                  onPressed: _isSubmitting ? null : () => _submitReport(isHindi),
                ),
                const SizedBox(height: 24),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildAiBanner(bool isHindi) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: AppTheme.primaryBlue.withOpacity(0.12),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: AppTheme.primaryLight.withOpacity(0.3)),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Icon(Icons.auto_awesome, color: AppTheme.primaryLight, size: 22),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              isHindi
                  ? 'रिपोर्ट सबमिट होते ही एम्बेडिंग्स जनरेट होंगी और सभी कनेक्टेड सीसीटीवी कैमरा नोड्स पर लाइव स्कैनिंग शुरू हो जाएगी।'
                  : 'Upon submission, facial biometric vectors are extracted and immediately deployed to active Edge CCTV nodes for live recognition.',
              style: const TextStyle(
                fontSize: 12.5,
                color: AppTheme.darkTextPrimary,
                height: 1.4,
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSectionTitle({required IconData icon, required String title}) {
    return Row(
      children: [
        Icon(icon, size: 18, color: AppTheme.primaryLight),
        const SizedBox(width: 8),
        Text(
          title,
          style: const TextStyle(
            fontSize: 15,
            fontWeight: FontWeight.bold,
            color: AppTheme.darkTextPrimary,
            letterSpacing: -0.2,
          ),
        ),
      ],
    );
  }
}
