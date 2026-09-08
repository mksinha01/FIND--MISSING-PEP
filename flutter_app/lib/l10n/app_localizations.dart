import 'package:flutter/material.dart';

/// AppLocalizations provides strongly-typed bilingual access to all UI strings
class AppLocalizations {
  final Locale locale;

  AppLocalizations(this.locale);

  static AppLocalizations of(BuildContext context) {
    return Localizations.of<AppLocalizations>(context, AppLocalizations) ??
        AppLocalizations(const Locale('en'));
  }

  static const LocalizationsDelegate<AppLocalizations> delegate =
      _AppLocalizationsDelegate();

  bool get isHindi => locale.languageCode == 'hi';

  // Titles & Navigation
  String get appTitle => 'FIND-MISSING-PEP';
  String get dashboard => isHindi ? 'डैशबोर्ड' : 'Dashboard';
  String get reports => isHindi ? 'रिपोर्ट्स' : 'Reports';
  String get alerts => isHindi ? 'अलर्ट्स' : 'Alerts';
  String get profile => isHindi ? 'प्रोफ़ाइल' : 'Profile';

  // Dashboard
  String get activeAiMonitoring =>
      isHindi ? 'सक्रिय एआई निगरानी' : 'Active AI Monitoring';
  String welcomeMessage(String userName) => isHindi
      ? 'नमस्ते, $userName! सिस्टम सक्रिय रूप से सीसीटीवी स्ट्रीम स्कैन कर रहा है।'
      : 'Welcome back, $userName! Edge AI nodes are actively scanning connected CCTV streams.';
  String get activeCases => isHindi ? 'सक्रिय केस' : 'Active Cases';
  String get sightings => isHindi ? 'साइटिंग्स' : 'Sightings';
  String get verifiedMatches => isHindi ? 'सत्यापित मैच' : 'Verified Matches';
  String get quickActions => isHindi ? 'त्वरित कार्य' : 'Quick Actions';
  String get submitMissingPersonReport =>
      isHindi ? 'लापता व्यक्ति रिपोर्ट दर्ज करें' : 'Submit Missing Person Report';

  // Reports Management & List
  String get reportManagement => isHindi ? 'रिपोर्ट प्रबंधन' : 'Report Management';
  String get myReports => isHindi ? 'मेरी रिपोर्टें' : 'My Reports';
  String get allReports => isHindi ? 'सभी' : 'All';
  String get activeReports => isHindi ? 'सक्रिय' : 'Active';
  String get foundReports => isHindi ? 'मिल गया' : 'Found';
  String get processingReports => isHindi ? 'प्रक्रियाधीन' : 'Processing';
  String get closedReports => isHindi ? 'बंद' : 'Closed';
  String get searchReportsPlaceholder =>
      isHindi ? 'नाम या स्थान से खोजें...' : 'Search by name or location...';
  String get noReportsFound => isHindi ? 'कोई रिपोर्ट नहीं मिली' : 'No reports found';
  String get noReportsYet => isHindi
      ? 'अभी तक कोई रिपोर्ट दर्ज नहीं की गई है। नई रिपोर्ट दर्ज करने के लिए नीचे क्लिक करें।'
      : 'No reports filed yet. Click below to submit your first missing person report.';

  // Form Fields
  String get newReportTitle =>
      isHindi ? 'लापता व्यक्ति रिपोर्ट दर्ज करें' : 'File Missing Person Report';
  String get reportDetailsTitle =>
      isHindi ? 'रिपोर्ट का विवरण' : 'Report Details';
  String get fullName => isHindi ? 'पूरा नाम' : 'Full Name';
  String get fullNameHint =>
      isHindi ? 'व्यक्ति का कानूनी नाम दर्ज करें' : 'Enter full legal name';
  String get fullNameRequired =>
      isHindi ? 'पूरा नाम आवश्यक है' : 'Full name is required';
  String get age => isHindi ? 'उम्र (वर्ष)' : 'Age (years)';
  String get ageHint => isHindi ? 'उदा. 14' : 'e.g. 14';
  String get ageInvalid =>
      isHindi ? 'कृपया एक वैध उम्र (1-120) दर्ज करें' : 'Please enter a valid age (1-120)';
  String get gender => isHindi ? 'लिंग' : 'Gender';
  String get genderMale => isHindi ? 'पुरुष' : 'Male';
  String get genderFemale => isHindi ? 'महिला' : 'Female';
  String get genderOther => isHindi ? 'अन्य' : 'Other';
  String get height => isHindi ? 'कद (सेमी)' : 'Height (cm)';
  String get heightHint => isHindi ? 'उदा. 165' : 'e.g. 165';
  String get description =>
      isHindi ? 'विवरण एवं पहचान चिह्न' : 'Description & Distinguishing Features';
  String get descriptionHint => isHindi
      ? 'पहने हुए कपड़े, तिल, निशान, आभूषण, बोली जाने वाली भाषा...'
      : 'Clothing worn, birthmarks, scars, accessories, language spoken...';
  String get lastSeenLocation =>
      isHindi ? 'अंतिम बार देखे जाने का स्थान' : 'Last Seen Location';
  String get lastSeenLocationHint => isHindi
      ? 'उदा. सेक्टर 18 मेट्रो स्टेशन, गेट 2 के पास'
      : 'e.g. Sector 18 Metro Station, Near Gate 2';
  String get lastSeenLocationRequired =>
      isHindi ? 'अंतिम स्थान दर्ज करना आवश्यक है' : 'Last seen location is required';
  String get lastSeenDateTime =>
      isHindi ? 'अंतिम बार देखे जाने का समय व दिनांक' : 'Last Seen Date & Time';
  String get selectDate => isHindi ? 'तारीख चुनें' : 'Select Date';
  String get selectTime => isHindi ? 'समय चुनें' : 'Select Time';
  String get contactNumber =>
      isHindi ? 'आपातकालीन संपर्क नंबर' : 'Emergency Contact Number';
  String get contactNumberHint => '+91 9876543210';
  String get contactNumberRequired =>
      isHindi ? 'संपर्क नंबर आवश्यक है' : 'Contact number is required';

  // Photo Section
  String get photosSectionTitle =>
      isHindi ? 'तस्वीरें (1 से 5 फोटो)' : 'Photographs (1 to 5 Photos)';
  String get photosSectionSubtitle => isHindi
      ? '1 से 5 स्पष्ट सामने की तस्वीरें अपलोड करें। स्पष्ट चेहरे की तस्वीरों से एआई पहचान की सटीकता बढ़ती है।'
      : 'Upload 1-5 clear frontal photos. High-resolution face photos improve AI recognition accuracy.';
  String get addPhoto => isHindi ? 'फोटो जोड़ें' : 'Add Photo';
  String get takePhoto => isHindi ? 'कैमरे से फोटो लें' : 'Take Photo';
  String get chooseFromGallery =>
      isHindi ? 'गैलरी से चुनें' : 'Choose from Gallery';
  String get primaryPhoto => isHindi ? 'मुख्य संदर्भ फोटो' : 'Primary Reference';
  String get setAsPrimary => isHindi ? 'मुख्य फोटो बनाएं' : 'Set as Primary';
  String photoCompressedTo(String size) =>
      isHindi ? 'कंप्रेस किया गया $size' : 'Compressed to $size';
  String get atLeastOnePhotoRequired => isHindi
      ? 'कृपया लापता व्यक्ति की कम से कम एक तस्वीर संलग्न करें।'
      : 'Please attach at least one photo of the missing person.';
  String get maxPhotosAllowed =>
      isHindi ? 'आप अधिकतम 5 तस्वीरें ही अपलोड कर सकते हैं।' : 'You can upload a maximum of 5 photos.';

  // Submission Dialogs
  String get submittingReport =>
      isHindi ? 'रिपोर्ट सबमिट हो रही है...' : 'Submitting Report...';
  String get compressingAndUploading => isHindi
      ? 'तस्वीरों को कंप्रेस कर 512-डी फेस एम्बेडिंग निकाली जा रही है...'
      : 'Compressing images and extracting 512-D face embeddings...';
  String get reportSubmittedSuccess => isHindi
      ? 'रिपोर्ट सफलतापूर्वक दर्ज की गई! एआई निगरानी सक्रिय हो गई है।'
      : 'Report submitted successfully! AI monitoring has been activated.';
  String get reportSubmissionFailed => isHindi
      ? 'रिपोर्ट सबमिट करने में विफल। कृपया नेटवर्क जांचें और पुनः प्रयास करें।'
      : 'Failed to submit report. Please check your connection and try again.';

  // Statuses
  String get statusActive => isHindi ? 'सक्रिय' : 'Active';
  String get statusFound => isHindi ? 'मिल गया' : 'Found';
  String get statusProcessing => isHindi ? 'प्रक्रियाधीन' : 'Processing';
  String get statusClosed => isHindi ? 'बंद' : 'Closed';

  String get statusActiveDesc => isHindi
      ? 'एज एआई नोड्स बायोमेट्रिक मिलान के लिए सीसीटीवी कैमरों को स्कैन कर रहे हैं।'
      : 'Edge AI nodes are actively scanning CCTV feeds for biometric matches.';
  String get statusFoundDesc => isHindi
      ? 'व्यक्ति सुरक्षित मिल गया है और केस अपडेट कर दिया गया है।'
      : 'The individual has been located and marked as safe.';
  String get statusProcessingDesc => isHindi
      ? 'तस्वीरों से चेहरे की पहचान और एम्बेडिंग निकाली जा रही है।'
      : 'Photos are undergoing face detection and embedding extraction.';
  String get statusClosedDesc => isHindi
      ? 'यह मामला अब बंद और समाप्त कर दिया गया है।'
      : 'This case investigation has been concluded and closed.';

  // Actions
  String get markAsFound => isHindi ? 'मिल गया चिह्नित करें' : 'Mark as Found';
  String get confirmMarkFound => isHindi
      ? 'क्या आप वाकई इस व्यक्ति को \'मिल गया\' के रूप में चिह्नित करना चाहते हैं?'
      : 'Are you sure you want to mark this person as found?';
  String get closeCase => isHindi ? 'केस बंद करें' : 'Close Case';
  String get confirmCloseCase => isHindi
      ? 'क्या आप वाकई इस केस को बंद करना चाहते हैं? बायोमेट्रिक एम्बेडिंग निष्क्रिय कर दी जाएगी।'
      : 'Are you sure you want to close this case? Biometric embeddings will be deactivated.';
  String get addMorePhotos => isHindi ? 'और तस्वीरें जोड़ें' : 'Add More Photos';
  String get viewSightingsMap =>
      isHindi ? 'साइटिंग टाइमलाइन और मैप देखें' : 'View Sighting Timeline & Map';
  String sightingsFoundCount(int count) {
    if (count == 0) return isHindi ? 'कोई साइटिंग नहीं मिली' : 'No sightings yet';
    if (count == 1) return isHindi ? '1 साइटिंग मिली' : '1 Sighting Detected';
    return isHindi ? '$count साइटिंग्स मिलीं' : '$count Sightings Detected';
  }

  // Info Sections
  String get personalInfo => isHindi ? 'व्यक्तिगत जानकारी' : 'Personal Information';
  String get incidentInfo => isHindi ? 'घटना का विवरण' : 'Incident Information';
  String get contactInfo => isHindi ? 'संपर्क जानकारी' : 'Contact Information';
  String get biometricStatus =>
      isHindi ? 'बायोमेट्रिक नामांकन स्थिति' : 'Biometric Enrollment Status';
  String get faceExtracted =>
      isHindi ? 'चेहरे की एम्बेडिंग सफलतापूर्वक तैयार' : 'Facial Embedding Generated';
  String get noFaceDetected =>
      isHindi ? 'तस्वीर में कोई चेहरा नहीं मिला' : 'No Face Detected in Photo';

  // Common buttons
  String get cancel => isHindi ? 'रद्द करें' : 'Cancel';
  String get confirm => isHindi ? 'पुष्टि करें' : 'Confirm';
  String get save => isHindi ? 'सहेजें' : 'Save';
  String get delete => isHindi ? 'हटाएं' : 'Delete';
  String get edit => isHindi ? 'संपादित करें' : 'Edit';
  String get retry => isHindi ? 'पुनः प्रयास' : 'Retry';
  String get refresh => isHindi ? 'रिफ्रेश' : 'Refresh';
  String get shareBulletin => isHindi ? 'केस बुलेटिन साझा करें' : 'Share Case Bulletin';
}

class _AppLocalizationsDelegate
    extends LocalizationsDelegate<AppLocalizations> {
  const _AppLocalizationsDelegate();

  @override
  bool isSupported(Locale locale) =>
      ['en', 'hi'].contains(locale.languageCode);

  @override
  Future<AppLocalizations> load(Locale locale) async {
    return AppLocalizations(locale);
  }

  @override
  bool shouldReload(_AppLocalizationsDelegate old) => false;
}
