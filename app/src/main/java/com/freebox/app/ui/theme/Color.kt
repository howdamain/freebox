package com.freebox.app.ui.theme

import androidx.compose.ui.graphics.Color

// "Luminous Precision" palette, reconciled per DESIGN.md prose:
// Profit Green accents on pure-white surfaces with slate neutrals.

// Brand greens
val ProfitGreen = Color(0xFF16A34A)        // green-600: primary CTAs, active states
val ProfitGreenDeep = Color(0xFF15803D)    // green-700: green text on white (AA-safe)
val ProfitGreenBright = Color(0xFF22C55E)  // green-500: decorative, high-energy accents
val GreenChipContainer = Color(0xFFDCFCE7) // green-100: chip backgrounds
val GreenChipContent = Color(0xFF166534)   // green-800: chip text/icons

// Slate neutrals
val SurfaceWhite = Color(0xFFFFFFFF)
val SoftSlate = Color(0xFFF8F9FA)          // nested containers, input backgrounds
val SlateBorderFaint = Color(0xFFF1F5F9)   // slate-100: card hairlines, dividers
val SlateBorder = Color(0xFFE2E8F0)        // slate-200: input/secondary-button hairlines
val SlateOutline = Color(0xFFCBD5E1)       // slate-300: stronger outlines
val SlateMuted = Color(0xFF64748B)         // slate-500: secondary text (4.7:1 on white)
val InkSlate = Color(0xFF0F172A)           // slate-900: primary text

// Premium
val GoldAccent = Color(0xFFD97706)         // amber-600: paywall/premium gradient accent

// Semantic
val ErrorRed = Color(0xFFBA1A1A)
val ErrorContainerRed = Color(0xFFFFDAD6)
val OnErrorContainerRed = Color(0xFF93000A)
