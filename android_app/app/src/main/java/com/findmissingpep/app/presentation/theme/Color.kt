package com.findmissingpep.app.presentation.theme

import androidx.compose.ui.graphics.Color

// ─── Base Slate Palette (Dark Glassmorphic) ─────────────────────────────────
val Slate950 = Color(0xFF020617)
val Slate900 = Color(0xFF0F172A)
val Slate800 = Color(0xFF1E293B)
val Slate700 = Color(0xFF334155)
val Slate600 = Color(0xFF475569)
val Slate500 = Color(0xFF64748B)
val Slate400 = Color(0xFF94A3B8)
val Slate300 = Color(0xFFCBD5E1)
val Slate200 = Color(0xFFE2E8F0)
val Slate100 = Color(0xFFF1F5F9)
val Slate50 = Color(0xFFF8FAFC)

val BackgroundDark = Slate950
val SurfaceDark = Slate900

// ─── Accent Colors ──────────────────────────────────────────────────────────
val CyanPrimary = Color(0xFF06B6D4)      // Detection / Vision
val CyanLight = Color(0xFF22D3EE)
val CyanDark = Color(0xFF0891B2)
val CyanSurface = Color(0x1A06B6D4)       // 10% opacity for glass panels

val EmeraldPrimary = Color(0xFF10B981)    // Confirmed / Active / Success
val EmeraldLight = Color(0xFF34D399)
val EmeraldDark = Color(0xFF059669)
val EmeraldSurface = Color(0x1A10B981)

val RosePrimary = Color(0xFFF43F5E)       // High Alert / Danger
val RoseLight = Color(0xFFFB7185)
val RoseDark = Color(0xFFE11D48)
val RoseSurface = Color(0x1AF43F5E)

val AmberPrimary = Color(0xFFF59E0B)      // Pending / Warning
val AmberLight = Color(0xFFFBBF24)
val AmberDark = Color(0xFFD97706)
val AmberSurface = Color(0x1AF59E0B)

val VioletPrimary = Color(0xFF8B5CF6)     // Processing / AI
val VioletLight = Color(0xFFA78BFA)

// ─── Glassmorphic Surface Colors ────────────────────────────────────────────
val GlassSurface = Color(0x801E293B)      // 50% opacity
val GlassSurfaceLight = Color(0x40334155) // 25% opacity
val GlassBorder = Color(0x40475569)       // 25% opacity border

// ─── Text Colors ────────────────────────────────────────────────────────────
val TextPrimary = Color(0xFFF1F5F9)
val TextSecondary = Color(0xFF94A3B8)
val TextTertiary = Color(0xFF64748B)

// ─── Functional Colors ──────────────────────────────────────────────────────
val ConfidenceHigh = EmeraldPrimary       // >= 0.75
val ConfidenceMedium = AmberPrimary       // 0.60 - 0.74
val ConfidenceLow = RosePrimary           // < 0.60
