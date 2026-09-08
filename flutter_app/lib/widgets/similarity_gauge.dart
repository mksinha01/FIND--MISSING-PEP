import 'dart:math' as math;
import 'package:flutter/material.dart';
import '../config/theme.dart';

/// Visual size presets for SimilarityGauge
enum SimilarityGaugeSize {
  small(80, 6, 14, 9),
  medium(120, 8, 20, 11),
  large(160, 12, 28, 13);

  final double diameter;
  final double strokeWidth;
  final double percentageFontSize;
  final double labelFontSize;

  const SimilarityGaugeSize(
    this.diameter,
    this.strokeWidth,
    this.percentageFontSize,
    this.labelFontSize,
  );
}

/// Circular similarity percentage gauge with radial gradient arc and sweep animation
class SimilarityGauge extends StatefulWidget {
  final double similarityScore; // 0.0 to 1.0
  final SimilarityGaugeSize size;
  final bool showLabel;
  final bool isHindi;
  final bool animate;

  const SimilarityGauge({
    super.key,
    required this.similarityScore,
    this.size = SimilarityGaugeSize.medium,
    this.showLabel = true,
    this.isHindi = false,
    this.animate = true,
  });

  @override
  State<SimilarityGauge> createState() => _SimilarityGaugeState();
}

class _SimilarityGaugeState extends State<SimilarityGauge>
    with SingleTickerProviderStateMixin {
  late AnimationController _controller;
  late Animation<double> _animation;

  @override
  void initState() {
    super.initState();
    final target = widget.similarityScore.clamp(0.0, 1.0);
    _controller = AnimationController(
      vsync: this,
      duration: widget.animate ? const Duration(milliseconds: 1200) : Duration.zero,
    );

    _animation = Tween<double>(begin: 0.0, end: target).animate(
      CurvedAnimation(parent: _controller, curve: Curves.easeOutCubic),
    );

    if (widget.animate) {
      _controller.forward();
    } else {
      _controller.value = 1.0;
    }
  }

  @override
  void didUpdateWidget(covariant SimilarityGauge oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.similarityScore != widget.similarityScore) {
      final target = widget.similarityScore.clamp(0.0, 1.0);
      _animation = Tween<double>(
        begin: _animation.value,
        end: target,
      ).animate(
        CurvedAnimation(parent: _controller, curve: Curves.easeOutCubic),
      );
      _controller.forward(from: 0.0);
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  Color _getScoreColor(double score) {
    if (score >= 0.75) {
      return AppTheme.successGreen;
    } else if (score >= 0.60) {
      return AppTheme.accentAmber;
    } else {
      return AppTheme.errorRed;
    }
  }

  String _getConfidenceLabel(double score, bool isHindi) {
    if (score >= 0.85) {
      return isHindi ? 'उच्च मिलान' : 'HIGH CONFIDENCE';
    } else if (score >= 0.70) {
      return isHindi ? 'संभावित मैच' : 'PROBABLE MATCH';
    } else if (score >= 0.60) {
      return isHindi ? 'सीमावर्ती मैच' : 'BORDERLINE';
    } else {
      return isHindi ? 'कम मिलान' : 'LOW CONFIDENCE';
    }
  }

  @override
  Widget build(BuildContext context) {
    final score = widget.similarityScore.clamp(0.0, 1.0);
    final scoreColor = _getScoreColor(score);
    final label = _getConfidenceLabel(score, widget.isHindi);

    return AnimatedBuilder(
      animation: _animation,
      builder: (context, child) {
        final currentVal = _animation.value;
        final percentageText = '${(currentVal * 100).toStringAsFixed(1)}%';

        return Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            SizedBox(
              width: widget.size.diameter,
              height: widget.size.diameter,
              child: Stack(
                alignment: Alignment.center,
                children: [
                  // Custom painted radial arc
                  CustomPaint(
                    size: Size(widget.size.diameter, widget.size.diameter),
                    painter: _GaugePainter(
                      progress: currentVal,
                      strokeWidth: widget.size.strokeWidth,
                      progressColor: scoreColor,
                      backgroundColor: AppTheme.darkCardBorder.withOpacity(0.5),
                    ),
                  ),

                  // Center percentage readout
                  Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Text(
                        percentageText,
                        style: TextStyle(
                          fontSize: widget.size.percentageFontSize,
                          fontWeight: FontWeight.w800,
                          color: AppTheme.darkTextPrimary,
                          letterSpacing: -0.5,
                        ),
                      ),
                      Text(
                        widget.isHindi ? 'समानता' : 'SIMILARITY',
                        style: TextStyle(
                          fontSize: widget.size.labelFontSize - 2,
                          fontWeight: FontWeight.w600,
                          color: AppTheme.darkTextSecondary,
                          letterSpacing: 0.5,
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),

            if (widget.showLabel) ...[
              const SizedBox(height: 8),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                decoration: BoxDecoration(
                  color: scoreColor.withOpacity(0.15),
                  borderRadius: BorderRadius.circular(20),
                  border: Border.all(color: scoreColor.withOpacity(0.4), width: 1),
                ),
                child: Text(
                  label,
                  style: TextStyle(
                    fontSize: widget.size.labelFontSize,
                    fontWeight: FontWeight.w700,
                    color: scoreColor,
                    letterSpacing: 0.5,
                  ),
                ),
              ),
            ],
          ],
        );
      },
    );
  }
}

/// Custom painter for smooth circular arc gauge with rounded caps and gradient
class _GaugePainter extends CustomPainter {
  final double progress; // 0.0 to 1.0
  final double strokeWidth;
  final Color progressColor;
  final Color backgroundColor;

  const _GaugePainter({
    required this.progress,
    required this.strokeWidth,
    required this.progressColor,
    required this.backgroundColor,
  });

  @override
  void paint(Canvas canvas, Size size) {
    final center = Offset(size.width / 2, size.height / 2);
    final radius = (size.width - strokeWidth) / 2;

    // Background track circle (300 degree open arc or full circle)
    final bgPaint = Paint()
      ..color = backgroundColor
      ..strokeWidth = strokeWidth
      ..style = PaintingStyle.stroke
      ..strokeCap = StrokeCap.round;

    // Active progress arc paint
    final progressPaint = Paint()
      ..color = progressColor
      ..strokeWidth = strokeWidth
      ..style = PaintingStyle.stroke
      ..strokeCap = StrokeCap.round;

    // Draw background track from -135 deg to 135 deg (270 deg span) or 360 deg
    const startAngle = -math.pi * 0.5; // Top (-90 deg)
    final sweepAngle = 2 * math.pi * progress;

    // Draw full background ring
    canvas.drawCircle(center, radius, bgPaint);

    // Draw active progress arc
    if (progress > 0) {
      canvas.drawArc(
        Rect.fromCircle(center: center, radius: radius),
        startAngle,
        sweepAngle,
        false,
        progressPaint,
      );
    }
  }

  @override
  bool shouldRepaint(covariant _GaugePainter oldDelegate) {
    return oldDelegate.progress != progress ||
        oldDelegate.progressColor != progressColor ||
        oldDelegate.strokeWidth != strokeWidth ||
        oldDelegate.backgroundColor != backgroundColor;
  }
}
