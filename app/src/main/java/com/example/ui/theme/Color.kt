package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// ── Cinematic Pulse – NihonHua Design System ─────────────────────────────
// Brand: Deep Ink Obsidian + Electric Violet + Neon Cyan + Crimson Spark

// Backgrounds & Surfaces
val Obsidian          = Color(0xFF0B1326)   // background
val ObsidianDim       = Color(0xFF060E20)   // surface-container-lowest
val SurfaceLow        = Color(0xFF131B2E)   // surface-container-low
val SurfaceMid        = Color(0xFF171F33)   // surface-container
val SurfaceHigh       = Color(0xFF222A3D)   // surface-container-high
val SurfaceHighest    = Color(0xFF2D3449)   // surface-container-highest / surface-variant
val SurfaceBright     = Color(0xFF31394D)   // surface-bright

// Primary – Electric Violet
val ElectricViolet    = Color(0xFFD2BBFF)   // primary
val VioletOnPrimary   = Color(0xFF3F008E)   // on-primary
val VioletContainer   = Color(0xFF7C3AED)   // primary-container
val VioletOnContainer = Color(0xFFEDE0FF)   // on-primary-container
val VioletInverse     = Color(0xFF732EE4)   // inverse-primary

// Secondary – Neon Cyan
val NeonCyan          = Color(0xFFA6E6FF)   // secondary
val CyanContainer     = Color(0xFF14D1FF)   // secondary-container
val CyanDim           = Color(0xFF4CD6FF)   // secondary-fixed-dim

// Tertiary – Crimson Spark
val CrimsonSpark      = Color(0xFFFFB2BA)   // tertiary
val CrimsonContainer  = Color(0xFFCD0047)   // tertiary-container

// Text
val TextPrimary       = Color(0xFFDAE2FD)   // on-surface / on-background
val TextSecondary     = Color(0xFFCCC3D8)   // on-surface-variant
val OutlineColor      = Color(0xFF958DA1)   // outline
val OutlineVariant    = Color(0xFF4A4455)   // outline-variant

// Error
val ErrorRed          = Color(0xFFFFB4AB)
val ErrorContainer    = Color(0xFF93000A)

// Legacy aliases kept for backward-compat
val Purple80          = ElectricViolet
val PurpleGrey80      = NeonCyan
val Pink80            = CrimsonSpark
val Purple40          = Color(0xFF5A00C6)
val PurpleGrey40      = Color(0xFF004E60)
val Pink40            = Color(0xFF910030)

// ─────────────────────────────────────────────────────────────────────────
// Glass & Neon helpers (used inline in composables)
val GlassTint         = Color(0x0DFFFFFF)   // rgba(255,255,255, 0.05)
val GlassBorder       = Color(0x1AFFFFFF)   // rgba(255,255,255, 0.10)
val NeonVioletGlow    = Color(0x66D2BBFF)   // violet glow 40%
val NeonCyanGlow      = Color(0x664CD6FF)   // cyan glow 40%s