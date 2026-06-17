---
name: Luminous Precision
colors:
  surface: '#faf8ff'
  surface-dim: '#d2d9f4'
  surface-bright: '#faf8ff'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f2f3ff'
  surface-container: '#eaedff'
  surface-container-high: '#e2e7ff'
  surface-container-highest: '#dae2fd'
  on-surface: '#131b2e'
  on-surface-variant: '#3e4a3d'
  inverse-surface: '#283044'
  inverse-on-surface: '#eef0ff'
  outline: '#6e7b6c'
  outline-variant: '#bdcaba'
  surface-tint: '#006e2d'
  primary: '#006b2c'
  on-primary: '#ffffff'
  primary-container: '#00873a'
  on-primary-container: '#f7fff2'
  inverse-primary: '#62df7d'
  secondary: '#006e2f'
  on-secondary: '#ffffff'
  secondary-container: '#6bff8f'
  on-secondary-container: '#007432'
  tertiary: '#5a5c5d'
  on-tertiary: '#ffffff'
  tertiary-container: '#737576'
  on-tertiary-container: '#fcfdfe'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#7ffc97'
  primary-fixed-dim: '#62df7d'
  on-primary-fixed: '#002109'
  on-primary-fixed-variant: '#005320'
  secondary-fixed: '#6bff8f'
  secondary-fixed-dim: '#4ae176'
  on-secondary-fixed: '#002109'
  on-secondary-fixed-variant: '#005321'
  tertiary-fixed: '#e1e3e4'
  tertiary-fixed-dim: '#c5c7c8'
  on-tertiary-fixed: '#191c1d'
  on-tertiary-fixed-variant: '#454748'
  background: '#faf8ff'
  on-background: '#131b2e'
  surface-variant: '#dae2fd'
typography:
  display-lg:
    fontFamily: Sora
    fontSize: 48px
    fontWeight: '700'
    lineHeight: 56px
    letterSpacing: -0.02em
  display-lg-mobile:
    fontFamily: Sora
    fontSize: 32px
    fontWeight: '700'
    lineHeight: 40px
    letterSpacing: -0.02em
  headline-md:
    fontFamily: Sora
    fontSize: 24px
    fontWeight: '600'
    lineHeight: 32px
  headline-sm:
    fontFamily: Sora
    fontSize: 20px
    fontWeight: '600'
    lineHeight: 28px
  body-lg:
    fontFamily: Sora
    fontSize: 18px
    fontWeight: '400'
    lineHeight: 28px
  body-md:
    fontFamily: Sora
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  label-md:
    fontFamily: Sora
    fontSize: 14px
    fontWeight: '600'
    lineHeight: 20px
    letterSpacing: 0.01em
  label-sm:
    fontFamily: Sora
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 16px
rounded:
  sm: 0.5rem
  DEFAULT: 1rem
  md: 1.5rem
  lg: 2rem
  xl: 3rem
  full: 9999px
spacing:
  unit: 8px
  container-padding-mobile: 20px
  container-padding-desktop: 40px
  gutter: 24px
  stack-sm: 12px
  stack-md: 24px
  stack-lg: 48px
---

## Brand & Style

This design system represents a sophisticated evolution of a high-energy tech aesthetic, shifting from a dark-mode core to a luminous, airy, and high-end light interface. The brand personality is professional yet optimistic, blending the precision of telecommunications hardware with the warmth of a consumer-centric service. 

The design style is **Minimalist with Tactile Accents**, characterized by vast amounts of whitespace, sharp geometric typography, and "bubbly" oversized corner radii that provide a friendly counterpoint to the technical precision of the layout. It utilizes 1px hairline strokes and subtle tonal shifts rather than heavy shadows to define structure, ensuring a clean and premium feel.

## Colors

The palette is anchored in **Pure White (#FFFFFF)** to maximize the sense of space and cleanliness. A secondary surface color, **Soft Slate (#F8F9FA)**, is used for nested containers and background layering to provide subtle depth without introducing heavy shadows.

The primary accent is "Profit Green." While the base vibrant green (#22c55e) is used for decorative elements and high-energy states, a slightly darkened version (**#16a34a**) is utilized for text-on-white or critical UI actions to ensure AA/AAA accessibility compliance. The neutral palette uses deep slates instead of pure blacks to maintain a high-end, sophisticated contrast ratio.

## Typography

This design system exclusively uses **Sora** to leverage its geometric construction and technical clarity. The type hierarchy is intentionally bold at the display level to drive brand energy, while the body text maintains generous line heights for maximum legibility against the white canvas.

For headlines, use tighter letter spacing to emphasize the typeface's distinctive ink traps and structural weight. For smaller labels and functional text, slightly increased tracking is recommended to ensure clarity at small scales.

## Layout & Spacing

The layout follows a **Fluid Grid** model with strict 8px incremental spacing. On desktop, a 12-column grid is used with 24px gutters, while mobile scales to a 4-column layout with 20px side margins.

Content is organized in "Airy Stacks," where vertical rhythm is prioritised over dense packing. High-level sections should utilize the `stack-lg` (48px) spacing to maintain the premium, uncrowded feel. Components within cards should adhere to a 16px or 24px internal padding rhythm.

## Elevation & Depth

Depth is achieved through **Tonal Layering** and **Minimalist Outlines** rather than traditional drop shadows. 

1.  **Level 0 (Base):** Pure White (#FFFFFF).
2.  **Level 1 (Sub-containers):** Soft Slate (#F8F9FA) with a 1px border (#E2E8F0).
3.  **Active/Interactive:** When an element is lifted (e.g., a card on hover), apply a very soft, high-diffusion shadow: `0px 10px 30px rgba(15, 23, 42, 0.04)`.

This approach keeps the UI feeling light and "weightless," reinforcing the sophisticated and professional aesthetic.

## Shapes

The shape language is defined by **Extreme Roundness**, providing a friendly and modern contrast to the sharp typography. 

Standard components (buttons, inputs) utilize a minimum radius of 16px, while larger surface elements (cards, modals) employ a 24px or 32px radius. This "bubbly" geometry is a signature trait of the design system, making the interface feel approachable and organic. All strokes must remain at 1px to ensure the roundness feels intentional and high-end rather than heavy.

## Components

-   **Buttons:** Primary buttons are Solid Profit Green (#16a34a) with white text. Secondary buttons use a transparent background with a 1px slate border. All buttons use a fully rounded (pill) shape for a friendly, high-energy feel.
-   **Cards:** Use a 24px border radius. Cards should be Pure White with a 1px #F1F5F9 border. On hover, the border color transitions to Profit Green to signal interactivity.
-   **Inputs:** Large, 16px rounded corners. Backgrounds should be #F8F9FA to differentiate from the base white surface. Focused states should use a 2px Profit Green outline.
-   **Chips/Tags:** Small, pill-shaped elements with light green backgrounds (#DCFCE7) and dark green text (#166534) for status indicators.
-   **Lists:** Items are separated by 1px #F1F5F9 dividers. Selection states should use a soft #F8F9FA background fill with a vertical Profit Green indicator on the left.
-   **Modals:** Large 32px corner radius, centered, with a light backdrop blur (12px) to maintain the airy, translucent aesthetic without heavy dimming.