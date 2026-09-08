import 'dart:typed_data';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:find_missing_pep/config/constants.dart';
import 'package:find_missing_pep/models/enums.dart';
import 'package:find_missing_pep/models/missing_person_model.dart';
import 'package:find_missing_pep/providers/reports_provider.dart';
import 'package:find_missing_pep/services/image_service.dart';
import 'package:find_missing_pep/widgets/status_badge.dart';

void main() {
  group('Architectural Fix #19 & Models Test', () {
    test('PhotoModel resolves server relative URL to full uploads URL', () {
      const photo = PhotoModel(
        id: 'photo-1',
        originalPath: 'reports/2026/09/sample.jpg',
        faceCropPath: '/crops/sample_face.jpg',
        isPrimary: true,
        processingStatus: PhotoProcessingStatus.success,
      );

      expect(
        photo.originalUrl,
        equals('${AppConstants.uploadsBaseUrl}/reports/2026/09/sample.jpg'),
      );
      expect(
        photo.faceCropUrl,
        equals('${AppConstants.uploadsBaseUrl}/crops/sample_face.jpg'),
      );
    });

    test('PhotoModel preserves absolute HTTP/HTTPS URLs', () {
      const photo = PhotoModel(
        id: 'photo-2',
        originalPath: 'https://cdn.example.com/photos/image.jpg',
        faceCropPath: 'https://cdn.example.com/crops/image.jpg',
      );

      expect(photo.originalUrl, equals('https://cdn.example.com/photos/image.jpg'));
      expect(photo.faceCropUrl, equals('https://cdn.example.com/crops/image.jpg'));
    });

    test('MissingPersonModel picks primary photo URL and primary face crop URL', () {
      const photo1 = PhotoModel(
        id: 'p1',
        originalPath: 'p1.jpg',
        faceCropPath: 'p1_crop.jpg',
        isPrimary: false,
      );
      const photo2 = PhotoModel(
        id: 'p2',
        originalPath: 'p2.jpg',
        faceCropPath: 'p2_crop.jpg',
        isPrimary: true,
      );

      final report = MissingPersonModel(
        id: 'rep-1',
        userId: 'user-1',
        fullName: 'Aarav Sharma',
        age: 12,
        gender: Gender.male,
        status: ReportStatus.active,
        photos: const [photo1, photo2],
      );

      expect(report.primaryPhotoUrl, equals('${AppConstants.uploadsBaseUrl}/p2.jpg'));
      expect(report.primaryFaceCropUrl, equals('${AppConstants.uploadsBaseUrl}/p2_crop.jpg'));
    });
  });

  group('CompressedPhotoItem & Client Compression Tests', () {
    test('CompressedPhotoItem formats file sizes and validates < 1MB limit', () {
      final itemUnder1MB = CompressedPhotoItem(
        id: '1',
        path: '/tmp/test.jpg',
        name: 'test.jpg',
        bytes: Uint8List(500 * 1024), // 500 KB
        sizeBytes: 500 * 1024,
        originalSizeBytes: 2 * 1024 * 1024, // 2 MB original
        isPrimary: true,
      );

      expect(itemUnder1MB.isUnder1MB, isTrue);
      expect(itemUnder1MB.formattedSize, equals('500.0 KB'));
      expect(itemUnder1MB.isPrimary, isTrue);

      final itemOver1MB = CompressedPhotoItem(
        id: '2',
        path: '/tmp/large.jpg',
        name: 'large.jpg',
        bytes: Uint8List(1200 * 1024), // 1.2 MB
        sizeBytes: 1200 * 1024,
        originalSizeBytes: 5 * 1024 * 1024,
      );

      expect(itemOver1MB.isUnder1MB, isFalse);
      expect(itemOver1MB.formattedSize, equals('1.17 MB'));
    });

    test('CompressedPhotoItem converts to MultipartFile for atomic upload', () {
      final item = CompressedPhotoItem(
        id: '3',
        path: '/tmp/doc.jpg',
        name: 'portrait.jpg',
        bytes: Uint8List.fromList([1, 2, 3, 4, 5]),
        sizeBytes: 5,
        originalSizeBytes: 10,
      );

      final multipart = item.toMultipartFile();
      expect(multipart.filename, equals('portrait.jpg'));
      expect(multipart.length, equals(5));
    });
  });

  group('ReportsState Search & Filtering Pipeline Tests', () {
    final report1 = MissingPersonModel(
      id: '1',
      userId: 'u1',
      fullName: 'Rohan Gupta',
      age: 15,
      gender: Gender.male,
      lastSeenLocation: 'Sector 18 Metro',
      description: 'Blue t-shirt, black bag',
      status: ReportStatus.active,
    );

    final report2 = MissingPersonModel(
      id: '2',
      userId: 'u1',
      fullName: 'Priya Singh',
      age: 22,
      gender: Gender.female,
      lastSeenLocation: 'Connaught Place',
      description: 'Yellow dupatta',
      status: ReportStatus.found,
    );

    final report3 = MissingPersonModel(
      id: '3',
      userId: 'u1',
      fullName: 'Amit Kumar',
      age: 8,
      gender: Gender.male,
      lastSeenLocation: 'Central Park',
      description: 'Red jacket',
      status: ReportStatus.closed,
    );

    final state = ReportsState(
      reports: [report1, report2, report3],
      total: 3,
    );

    test('Returns all reports when statusFilter is ALL', () {
      final filtered = state.filteredReports;
      expect(filtered.length, equals(3));
    });

    test('Filters reports by status correctly', () {
      final activeState = state.copyWith(statusFilter: 'ACTIVE');
      expect(activeState.filteredReports.length, equals(1));
      expect(activeState.filteredReports.first.fullName, equals('Rohan Gupta'));

      final foundState = state.copyWith(statusFilter: 'FOUND');
      expect(foundState.filteredReports.length, equals(1));
      expect(foundState.filteredReports.first.fullName, equals('Priya Singh'));
    });

    test('Searches across full name, location, and description case-insensitively', () {
      final nameSearch = state.copyWith(searchQuery: 'priya');
      expect(nameSearch.filteredReports.length, equals(1));
      expect(nameSearch.filteredReports.first.fullName, equals('Priya Singh'));

      final locationSearch = state.copyWith(searchQuery: 'METRO');
      expect(locationSearch.filteredReports.length, equals(1));
      expect(locationSearch.filteredReports.first.fullName, equals('Rohan Gupta'));

      final descSearch = state.copyWith(searchQuery: 'jacket');
      expect(descSearch.filteredReports.length, equals(1));
      expect(descSearch.filteredReports.first.fullName, equals('Amit Kumar'));
    });
  });

  group('StatusBadge Widget Tests', () {
    testWidgets('Renders ACTIVE status badge with correct text in EN and HI', (tester) async {
      await tester.pumpWidget(
        const MaterialApp(
          home: Scaffold(
            body: StatusBadge(
              status: 'ACTIVE',
              isHindi: false,
            ),
          ),
        ),
      );

      expect(find.text('ACTIVE'), findsOneWidget);

      await tester.pumpWidget(
        const MaterialApp(
          home: Scaffold(
            body: StatusBadge(
              status: 'ACTIVE',
              isHindi: true,
            ),
          ),
        ),
      );

      expect(find.text('सक्रिय'), findsOneWidget);
    });

    testWidgets('Renders FOUND and CLOSED status badges properly', (tester) async {
      await tester.pumpWidget(
        const MaterialApp(
          home: Scaffold(
            body: Column(
              children: [
                StatusBadge(status: 'FOUND', isHindi: false),
                StatusBadge(status: 'CLOSED', isHindi: true),
              ],
            ),
          ),
        ),
      );

      expect(find.text('FOUND'), findsOneWidget);
      expect(find.text('केस बंद'), findsOneWidget);
    });
  });
}
