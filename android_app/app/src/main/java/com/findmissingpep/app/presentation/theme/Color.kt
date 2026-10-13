package com.findmissingpep.app.presentation.theme

import androidx.compose.ui.graphics.Color

// ─── Blue & Cream Signature Design Tokens (from Kestrel.canvas) ─────────────
// Warm, human, government/public safety palette — clean, approachable & clear.

val CreamBackground = Color(0xFFF8F3F0)      // Main screen canvas
val PaperWhite = Color(0xFFFFFDFC)           // Clean card / sheet surface
val PaperElevated = Color(0xFFFFFFFF)        // Top modals / elevated inputs
val PaperSubtle = Color(0xFFF4EFEA)          // Inner tile / secondary wash

val BluePrimary = Color(0xFF014BAA)          // Signature Official Royal Blue
val BlueDeep = Color(0xFF07356F)             // Deep navy blue (headers/titles)
val BlueWash = Color(0xFFEAF1FB)             // Soft light-blue background tint
val BlueBorder = Color(0xFF90A6C2)           // Focused / primary outline

val InkText = Color(0xFF17243A)              // High-contrast legible text
val InkMuted = Color(0xFF657187)             // Secondary / hint / subtitle text
val InkSubtle = Color(0xFF8B96A5)            // Captions & footnotes

val LineBorder = Color(0xFFE5DDD6)           // Card & input divider line
val LineBorderSubtle = Color(0xFFEFE8E1)     // Soft separator

val AmberAlert = Color(0xFF956114)           // Pending / Warning text
val AmberAlertBg = Color(0xFFFBF0D9)         // Pending / Warning pill
val GreenSuccess = Color(0xFF187757)         // Approved / Active / Match
val GreenSuccessBg = Color(0xFFE6F3EC)       // Approved / Active pill
val RedDanger = Color(0xFFA83B50)            // High alert / Rejected
val RedDangerBg = Color(0xFFF8E8E9)          // Alert / Rejected pill

// ─── Semantic Mappings for Compose Compatibility ────────────────────────────
val BackgroundLight = CreamBackground
val SurfaceLight = PaperWhite

val TextPrimary = InkText
val TextSecondary = InkMuted
val TextTertiary = InkSubtle

val AmberPrimary = AmberAlert
val AmberBg = AmberAlertBg
val AmberLight = Color(0xFFB8781B)
val AmberDark = Color(0xFF7A4B06)
val AmberSurface = AmberAlertBg

val EmeraldPrimary = GreenSuccess
val EmeraldBg = GreenSuccessBg
val EmeraldLight = Color(0xFF238E6A)
val EmeraldDark = Color(0xFF105C41)
val EmeraldSurface = GreenSuccessBg

val RosePrimary = RedDanger
val RoseBg = RedDangerBg
val RoseLight = Color(0xFFC44B62)
val RoseDark = Color(0xFF8B2B3E)
val RoseSurface = RedDangerBg

val CyanPrimary = BluePrimary
val CyanLight = Color(0xFF1A6CE0)
val CyanDark = BlueDeep
val CyanSurface = BlueWash

val VioletPrimary = BlueDeep
val VioletLight = BluePrimary

// ─── Glass & Card Surface Mappings (Paper Human UI) ─────────────────────────
val GlassSurface = PaperWhite
val GlassSurfaceLight = PaperElevated
val GlassBorder = LineBorder

// ─── Slate Aliases (mapped to Blue & Cream light aesthetic) ─────────────────
val Slate950 = CreamBackground
val Slate900 = PaperWhite
val Slate800 = PaperSubtle
val Slate700 = LineBorder
val Slate600 = InkMuted
val Slate500 = InkSubtle
val Slate400 = InkSubtle
val Slate300 = LineBorderSubtle
val Slate200 = PaperSubtle
val Slate100 = PaperWhite
val Slate50 = CreamBackground

val BackgroundDark = CreamBackground
val SurfaceDark = PaperWhite

// ─── Functional Confidence / Verification ───────────────────────────────────
val ConfidenceHigh = GreenSuccess
val ConfidenceMedium = AmberAlert
val ConfidenceLow = RedDanger
