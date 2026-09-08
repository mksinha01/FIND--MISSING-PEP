import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import 'package:latlong2/latlong.dart';
import '../config/constants.dart';
import '../config/theme.dart';
import '../models/enums.dart';
import '../models/sighting_model.dart';

/// Interactive Map and Timeline Widget rendering chronological CCTV sightings
class TimelineWidget extends StatefulWidget {
  final PersonTimelineModel timeline;
  final bool isHindi;
  final Function(String sightingId)? onSightingSelected;

  const TimelineWidget({
    super.key,
    required this.timeline,
    this.isHindi = false,
    this.onSightingSelected,
  });

  @override
  State<TimelineWidget> createState() => _TimelineWidgetState();
}

class _TimelineWidgetState extends State<TimelineWidget> {
  final MapController _mapController = MapController();
  int _selectedEntryIndex = 0;
  bool _showBottomList = true;

  // Default fallback center (New Delhi / India coordinates)
  static const LatLng _defaultCenter = LatLng(28.6139, 77.2090);

  List<TimelineEntryModel> get _validGpsEntries =>
      widget.timeline.entries.where((e) => e.latitude != null && e.longitude != null).toList();

  List<LatLng> get _polylinePoints =>
      _validGpsEntries.map((e) => LatLng(e.latitude!, e.longitude!)).toList();

  LatLng get _initialCenter {
    if (_polylinePoints.isNotEmpty) {
      return _polylinePoints.last; // Center on most recent sighting
    }
    return _defaultCenter;
  }

  void _fitAllMarkers() {
    if (_polylinePoints.isEmpty) return;
    if (_polylinePoints.length == 1) {
      _mapController.move(_polylinePoints.first, 15.0);
      return;
    }
    try {
      final bounds = LatLngBounds.fromPoints(_polylinePoints);
      _mapController.fitCamera(
        CameraFit.bounds(
          bounds: bounds,
          padding: const EdgeInsets.all(50.0),
        ),
      );
    } catch (_) {
      _mapController.move(_polylinePoints.last, 14.0);
    }
  }

  void _selectEntry(int index) {
    setState(() => _selectedEntryIndex = index);
    if (index >= 0 && index < widget.timeline.entries.length) {
      final entry = widget.timeline.entries[index];
      if (entry.latitude != null && entry.longitude != null) {
        _mapController.move(LatLng(entry.latitude!, entry.longitude!), 15.5);
      }
      widget.onSightingSelected?.call(entry.sightingId);
    }
  }

  @override
  Widget build(BuildContext context) {
    final validPoints = _polylinePoints;
    final entries = widget.timeline.entries;

    if (entries.isEmpty) {
      return Container(
        color: AppTheme.darkBackground,
        child: Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Icon(Icons.location_off_outlined, size: 64, color: AppTheme.darkTextSecondary),
              const SizedBox(height: 16),
              Text(
                widget.isHindi ? 'कोई साइटिंग रिकॉर्ड उपलब्ध नहीं' : 'No Sighting Records Yet',
                style: const TextStyle(
                  fontSize: 18,
                  fontWeight: FontWeight.bold,
                  color: AppTheme.darkTextPrimary,
                ),
              ),
              const SizedBox(height: 8),
              Text(
                widget.isHindi
                    ? 'सीसीटीवी नोड्स द्वारा मैच मिलने पर टाइमलाइन अपडेट होगी।'
                    : 'The movement trail will appear once CCTV nodes detect matches.',
                style: const TextStyle(color: AppTheme.darkTextSecondary, fontSize: 13),
              ),
            ],
          ),
        ),
      );
    }

    return Stack(
      children: [
        // ── FLUTTER_MAP OPENSTREETMAP LAYER ──
        FlutterMap(
          mapController: _mapController,
          options: MapOptions(
            initialCenter: _initialCenter,
            initialZoom: validPoints.isEmpty ? 11.0 : 14.0,
            minZoom: 3.0,
            maxZoom: 18.0,
          ),
          children: [
            // Standard OpenStreetMap Tile Layer
            TileLayer(
              urlTemplate: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
              userAgentPackageName: 'com.findmissingpep.app',
              maxZoom: 19,
            ),

            // Chronological Movement Polyline Layer
            if (validPoints.length > 1)
              PolylineLayer(
                polylines: [
                  Polyline(
                    points: validPoints,
                    strokeWidth: 4.5,
                    color: AppTheme.primaryLight,
                  ),
                ],
              ),

            // Chronological Numbered Map Pins Layer
            MarkerLayer(
              markers: _buildMarkers(),
            ),
          ],
        ),

        // ── TOP FLOATING CASE SUMMARY PILL ──
        Positioned(
          top: 16,
          left: 16,
          right: 16,
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
            decoration: BoxDecoration(
              color: AppTheme.darkSurface.withOpacity(0.92),
              borderRadius: BorderRadius.circular(16),
              border: Border.all(color: AppTheme.darkCardBorder),
              boxShadow: [
                BoxShadow(
                  color: Colors.black.withOpacity(0.3),
                  blurRadius: 10,
                  offset: const Offset(0, 4),
                ),
              ],
            ),
            child: Row(
              children: [
                Container(
                  padding: const EdgeInsets.all(8),
                  decoration: BoxDecoration(
                    color: AppTheme.primaryBlue.withOpacity(0.2),
                    shape: BoxShape.circle,
                  ),
                  child: const Icon(Icons.radar, color: AppTheme.primaryLight, size: 20),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Text(
                        widget.timeline.personName,
                        style: const TextStyle(
                          fontSize: 15,
                          fontWeight: FontWeight.w700,
                          color: AppTheme.darkTextPrimary,
                        ),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                      const SizedBox(height: 2),
                      Text(
                        '${entries.length} ${widget.isHindi ? "साइटिंग्स दर्ज" : "sightings recorded"} • ${validPoints.length} ${widget.isHindi ? "मैप नोड्स" : "GPS mapped"}',
                        style: const TextStyle(
                          fontSize: 11.5,
                          color: AppTheme.darkTextSecondary,
                        ),
                      ),
                    ],
                  ),
                ),
                IconButton(
                  icon: const Icon(Icons.my_location, color: AppTheme.primaryLight),
                  tooltip: widget.isHindi ? 'सभी देखें' : 'Fit All Sightings',
                  onPressed: _fitAllMarkers,
                ),
              ],
            ),
          ),
        ),

        // ── BOTTOM CHRONOLOGICAL SIGHTING CAROUSEL / DRAWER ──
        Positioned(
          bottom: 16,
          left: 12,
          right: 12,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              // Toggle Drawer Strip
              GestureDetector(
                onTap: () => setState(() => _showBottomList = !_showBottomList),
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
                  decoration: BoxDecoration(
                    color: AppTheme.darkSurface.withOpacity(0.95),
                    borderRadius: BorderRadius.circular(20),
                    border: Border.all(color: AppTheme.darkCardBorder),
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(
                        _showBottomList ? Icons.keyboard_arrow_down : Icons.keyboard_arrow_up,
                        size: 16,
                        color: AppTheme.primaryLight,
                      ),
                      const SizedBox(width: 6),
                      Text(
                        _showBottomList
                            ? (widget.isHindi ? 'टाइमलाइन छिपाएं' : 'Hide Trail')
                            : (widget.isHindi ? 'टाइमलाइन देखें' : 'View Chronological Trail'),
                        style: const TextStyle(
                          fontSize: 12,
                          fontWeight: FontWeight.w600,
                          color: AppTheme.primaryLight,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 8),

              // Horizontal Sighting Breadcrumbs Cards
              if (_showBottomList)
                SizedBox(
                  height: 130,
                  child: ListView.separated(
                    scrollDirection: Axis.horizontal,
                    itemCount: entries.length,
                    separatorBuilder: (_, __) => const SizedBox(width: 10),
                    itemBuilder: (context, index) {
                      final entry = entries[index];
                      final isSelected = index == _selectedEntryIndex;
                      final isConfirmed = entry.status == SightingStatus.confirmed;
                      final scoreColor = entry.similarityScore >= 0.75
                          ? AppTheme.successGreen
                          : (entry.similarityScore >= 0.60 ? AppTheme.accentAmber : AppTheme.errorRed);

                      return GestureDetector(
                        onTap: () => _selectEntry(index),
                        child: Container(
                          width: 260,
                          padding: const EdgeInsets.all(12),
                          decoration: BoxDecoration(
                            color: AppTheme.darkSurface.withOpacity(0.95),
                            borderRadius: BorderRadius.circular(16),
                            border: Border.all(
                              color: isSelected
                                  ? AppTheme.primaryLight
                                  : (isConfirmed ? AppTheme.successGreen.withOpacity(0.5) : AppTheme.darkCardBorder),
                              width: isSelected ? 2.0 : 1.0,
                            ),
                            boxShadow: [
                              BoxShadow(
                                color: Colors.black.withOpacity(0.25),
                                blurRadius: 8,
                                offset: const Offset(0, 4),
                              ),
                            ],
                          ),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            children: [
                              // Top Tag: Step Number & Match %
                              Row(
                                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                children: [
                                  Container(
                                    padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                                    decoration: BoxDecoration(
                                      color: AppTheme.primaryBlue,
                                      borderRadius: BorderRadius.circular(6),
                                    ),
                                    child: Text(
                                      '#${index + 1} ${widget.isHindi ? "स्टॉप" : "Stop"}',
                                      style: const TextStyle(
                                        fontSize: 10,
                                        fontWeight: FontWeight.bold,
                                        color: Colors.white,
                                      ),
                                    ),
                                  ),
                                  Container(
                                    padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                                    decoration: BoxDecoration(
                                      color: scoreColor.withOpacity(0.18),
                                      borderRadius: BorderRadius.circular(6),
                                      border: Border.all(color: scoreColor.withOpacity(0.4)),
                                    ),
                                    child: Text(
                                      '${(entry.similarityScore * 100).toStringAsFixed(1)}%',
                                      style: TextStyle(
                                        fontSize: 11,
                                        fontWeight: FontWeight.w800,
                                        color: scoreColor,
                                      ),
                                    ),
                                  ),
                                ],
                              ),

                              // Camera Name / Location
                              Text(
                                entry.cameraLocation ?? entry.cameraName,
                                style: const TextStyle(
                                  fontSize: 13,
                                  fontWeight: FontWeight.w700,
                                  color: AppTheme.darkTextPrimary,
                                ),
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                              ),

                              // Time & View Action
                              Row(
                                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                children: [
                                  Row(
                                    children: [
                                      const Icon(Icons.access_time, size: 12, color: AppTheme.darkTextSecondary),
                                      const SizedBox(width: 4),
                                      Text(
                                        DateFormat('dd MMM, hh:mm a').format(entry.detectedAt.toLocal()),
                                        style: const TextStyle(
                                          fontSize: 11,
                                          color: AppTheme.darkTextSecondary,
                                        ),
                                      ),
                                    ],
                                  ),
                                  InkWell(
                                    onTap: () => context.push(AppRoutes.sightingDetailPath(entry.sightingId)),
                                    child: Row(
                                      children: [
                                        Text(
                                          widget.isHindi ? 'सबूत' : 'Evidence',
                                          style: const TextStyle(
                                            fontSize: 11,
                                            fontWeight: FontWeight.w700,
                                            color: AppTheme.secondaryCyan,
                                          ),
                                        ),
                                        const Icon(Icons.arrow_forward_ios, size: 10, color: AppTheme.secondaryCyan),
                                      ],
                                    ),
                                  ),
                                ],
                              ),
                            ],
                          ),
                        ),
                      );
                    },
                  ),
                ),
            ],
          ),
        ),
      ],
    );
  }

  List<Marker> _buildMarkers() {
    final entries = widget.timeline.entries;
    final List<Marker> markers = [];

    int validIndex = 0;
    for (int i = 0; i < entries.length; i++) {
      final entry = entries[i];
      if (entry.latitude == null || entry.longitude == null) continue;

      final point = LatLng(entry.latitude!, entry.longitude!);
      final isSelected = i == _selectedEntryIndex;
      final isConfirmed = entry.status == SightingStatus.confirmed;
      final stepNumber = validIndex + 1;
      validIndex++;

      final markerColor = isConfirmed
          ? AppTheme.successGreen
          : (entry.similarityScore >= 0.75
              ? AppTheme.primaryLight
              : (entry.similarityScore >= 0.60 ? AppTheme.accentAmber : AppTheme.errorRed));

      markers.add(
        Marker(
          point: point,
          width: isSelected ? 52 : 44,
          height: isSelected ? 52 : 44,
          child: GestureDetector(
            onTap: () => _selectEntry(i),
            child: AnimatedContainer(
              duration: const Duration(milliseconds: 250),
              decoration: BoxDecoration(
                color: isSelected ? AppTheme.primaryLight : markerColor,
                shape: BoxShape.circle,
                border: Border.all(
                  color: Colors.white,
                  width: isSelected ? 3.0 : 2.0,
                ),
                boxShadow: [
                  BoxShadow(
                    color: (isSelected ? AppTheme.primaryLight : markerColor).withOpacity(0.6),
                    blurRadius: isSelected ? 12 : 6,
                    spreadRadius: isSelected ? 2 : 0,
                  ),
                ],
              ),
              child: Center(
                child: Text(
                  '$stepNumber',
                  style: const TextStyle(
                    color: Colors.black,
                    fontWeight: FontWeight.w900,
                    fontSize: 14,
                  ),
                ),
              ),
            ),
          ),
        ),
      );
    }

    return markers;
  }
}
