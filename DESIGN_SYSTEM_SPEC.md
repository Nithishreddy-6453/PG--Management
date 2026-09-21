# Design System Specification (DSS)
## PG Manager: Premium Mobile-First SaaS for Paying Guest (PG) Owners in India

**Document Version:** 1.0.0  
**Date:** July 18, 2026  
**Classification:** Definitive Visual, Interactive, & Accessibility Authority  
**Status:** Frozen, Design System Council Approved, Ready for UI Implementation  

---

## 1. Design Philosophy

### 1.1 Brand & Product Personality
PG Manager is built for the modern Paying Guest (PG) owner in India. The visual and interactive language MUST evoke a feeling of **control, prestige, and quiet reliability**. It is an enterprise tool that feels like a premium consumer application—balancing high-density business utility with a sophisticated, calm, and distraction-free interface.

### 1.2 Core Emotional Goals
* **Confidence & Control:** Financial ledgers, occupancy percentages, and check-in statuses MUST feel mathematically precise and easy to verify at a glance.
* **Calm Efficiency:** Property owners operate in high-stress, noisy environments. The interface MUST serve as a quiet, structured, and focused space using ample negative space, clean typography, and muted colors.
* **Modern Prestige:** The user interface MUST feel exceptionally polished, moving far away from typical "utility app slop" through the use of precise spacing, sharp layouts, and soft transitions.

### 1.3 Design Principles

```
+---------------------------------------------------------------------------------+
|                                 DESIGN PRINCIPLES                               |
+---------------------------------------------------------------------------------+
| Principle              | Core Operational Standard                              |
+------------------------+--------------------------------------------------------+
| **Data over Chrome**   | The owner's data is the hero. Container lines, borders,|
|                        | and solid card fills SHOULD be used sparingly to keep  |
|                        | cognitive load low.                                    |
+------------------------+--------------------------------------------------------+
| **One-Handed Flow**    | Key actions (FABs, primary buttons, sheet triggers)    |
|                        | MUST remain within the ergonomic thumb sweep zone      |
|                        | (bottom 60% of the screen).                            |
+------------------------+--------------------------------------------------------+
| **Delight in Motion**  | Screen transitions and status changes MUST feel alive, |
|                        | utilizing responsive, spring-based micro-interactions. |
+---------------------------------------------------------------------------------+
```

---

## 2. Color System

The PG Manager color palette is designed around a premium **Slate Minimalist Theme**. The palette is fully compliant with Material Design 3 and enforces strict contrast ratios for high outdoor readability under direct sunlight.

### 2.1 Core Palette Tokens (Light Theme & Dark Theme)

| Token Name | Light Theme Hex | Dark Theme Hex | Purpose & Usage Rule |
| :--- | :--- | :--- | :--- |
| `color-primary` | `#0F172A` (Slate 900) | `#F8FAFC` (Slate 50) | Dominant branding, primary buttons, active states. |
| `color-primary-container` | `#E2E8F0` (Slate 200) | `#1E293B` (Slate 800) | Highlight backdrops, soft button backgrounds. |
| `color-secondary` | `#475569` (Slate 600) | `#CBD5E1` (Slate 300) | Secondary text, status indicators, inactive tags. |
| `color-success` | `#15803D` (Green 700) | `#4ADE80` (Green 400) | Full rent settlements, active tenant status. |
| `color-warning` | `#B45309` (Amber 700) | `#FBBF24` (Amber 400) | Overdue invoice alerts, maintenance status. |
| `color-error` | `#B91C1C` (Red 700) | `#F87171` (Red 400) | Outstanding arrears, payment failures, deletes. |
| `color-surface` | `#FFFFFF` | `#0B0F19` (Deep Slate) | Core card layouts, list backdrops, bottom sheets. |
| `color-background` | `#F8FAFC` (Slate 50) | `#020617` (Deepest Slate) | Primary viewport backdrop, screen-level wrapper. |
| `color-outline` | `#E2E8F0` (Slate 200) | `#1E293B` (Slate 800) | Thin borders, subtle dividers, grid separators. |

### 2.2 Accessibility Compliance Constraints
* **Contrast Ratios:** Text on any background color MUST maintain a contrast ratio of at least **4.5:1** for body copy and **3:1** for large headlines, complying strictly with WCAG 2.1 AA standards.
* **Color Blindness Guard:** Status indicators (such as bed occupancy) MUST NOT rely on color alone. They MUST pair color with a supporting icon or descriptive text label (e.g., Green Circle + "Vacant", Red Circle + "Occupied").

---

## 3. Typography System

The selected typeface for PG Manager is **Inter** for all standard UI body copy, paired with **Space Grotesk** for large, prominent display headings and financial figures.

### 3.1 Typography Rationale
* **Inter:** Chosen for its exceptional neutral legibility, tall x-height, and precise tabular lining numbers, which keep financial spreadsheets and numeric ledgers perfectly aligned.
* **Space Grotesk:** A modern, geometric grotesque typeface that adds visual personality to display numbers, occupancy rates, and screen headers, reinforcing the premium SaaS feel.

### 3.2 Typography Token Matrix

| Token Name | Typeface | Size (sp) | Weight | Line Height (sp) | Letter Spacing (sp) | Intent / Usage Rules |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `type-display-lg` | Space Grotesk | `36` | Bold (700) | `44` | `-0.5` | Dashboard hero totals, prominent numeric summaries. |
| `type-headline-md` | Space Grotesk | `24` | Bold (700) | `32` | `0` | Primary screen titles, main empty-state headers. |
| `type-title-lg` | Inter | `18` | SemiBold (600)| `24` | `0.1` | Card headers, list sections, sheet headers. |
| `type-body-md` | Inter | `14` | Regular (400) | `20` | `0.2` | Primary descriptions, user inputs, detail text. |
| `type-label-md` | Inter | `12` | Medium (500) | `16` | `0.5` | Badges, status pills, helper descriptions, dates. |
| `type-button-lg` | Inter | `15` | SemiBold (600)| `20` | `0.2` | Primary call-to-actions, action sheet buttons. |

---

## 4. Spacing System

The spacing system is built on a strict **8dp grid**, establishing consistent vertical rhythm and visual structure throughout the application.

### 4.1 Spacing Scale Tokens
* `space-xs`: **4dp** (Sub-element alignment, badge padding)
* `space-sm`: **8dp** (Inner element padding, text-to-icon spacing)
* `space-md`: **16dp** (Standard padding for lists, cards, and input fields)
* `space-lg`: **24dp** (Container margins, section grouping gutters)
* `space-xl`: **32dp** (Large gutters, hero layout buffers)

### 4.2 Layout Margin & Content Padding
* **Screen Edge Margins:** All mobile viewports MUST use a minimum outer margin of `space-md` (16dp).
* **Card Inner Padding:** Standard interactive cards MUST use a default padding of `space-md` (16dp).

---

## 5. Shape & Corner System

Shapes and corner radiuses help define our visual identity. They are categorized based on their scale and purpose:

```
+---------------------------------------------------------------------------------+
|                                 SHAPE SCALE MATRIX                              |
+---------------------------------------------------------------------------------+
| Shape Scale       | Radius Value | Target UI Component Components               |
+-------------------+--------------+----------------------------------------------+
| **None**          | `0dp`        | Standard fullscreen views, edge-to-edge bars |
| **Small**         | `8dp`        | Status tags, chips, input fields, badges     |
| **Medium**        | `16dp`       | Interactive cards, dashboard statistics      |
| **Large**         | `28dp`       | Floating Action Buttons (FAB), Dialog boxes  |
| **Full**          | `9999dp`     | Avatars, system pills, button shapes         |
+---------------------------------------------------------------------------------+
```

---

## 6. Elevation & Surface System

The elevation system defines depth and hierarchy. It uses subtle Material Design 3 color tints (tonal elevations) instead of heavy, dark shadows, ensuring layouts look clean and modern.

### 6.1 Elevation Level Specifications
* **Level 0 (Flat):** `0dp` elevation. Used for background canvasses, parent lists, and form wrappers.
* **Level 1 (Low Depth):** `1dp` elevation (or `#FFFFFF` with a 3% primary tint). Used for inactive list items, search bars, and filter pills.
* **Level 2 (Standard Floating):** `3dp` elevation (or `#FFFFFF` with a 6% primary tint). Used for active cards, swipeable actions, and default state views.
* **Level 3 (Modal Sheet):** `6dp` elevation (or `#FFFFFF` with an 8% primary tint). Used for bottom sheets, dropdown menus, and dialog overlay content.

---

## 7. Iconography

Iconography plays a key role in making the application highly scannable, especially when language barriers exist.

### 7.1 Visual Style Standards
* **Family:** All icons MUST utilize the **Material Symbols** family (Rounded style).
* **Consistent Usage:** Outlined icons MUST be used for standard, inactive states, while Filled icons MUST represent selected or highly active states.
* **Optical Sizing:** Standard touch actions MUST use `24dp` icons, while smaller utility tags or chips SHOULD use `16dp` formats.
* **Touch-Friendly Targets:** All interactive icons MUST have their touch target expanded to at least **48dp x 48dp**, regardless of the physical icon size.

---

## 8. Motion & Interaction System

Motion is not just decorative—it provides essential feedback, helps explain spatial relationships, and guides users through the app.

### 8.1 Animation Easing & Timing Specifications
* **Standard Spring Curve (Visual Physics):** Standard UI moves (e.g., expanding cards, listing tasks) MUST utilize custom spring animations instead of linear timings:
  * `dampingRatio`: `0.8` (Soft bounce, highly organic)
  * `stiffness`: `StiffnessMedium` (Fast, responsive feel)
* **Duration Targets:**
  * **System transitions (Bottom Sheets, Dialogs):** `300ms` (Decelerate curve)
  * **Micro-interactions (Chips, Checkboxes, Switches):** `150ms` (Linear Out / Slow In)
* **Screen Page Slide (Type-Safe Navigation):** Navigating forward MUST slide new screens in from the right (`300ms`), while navigating backward MUST slide them out to the right.

---

## 9. Component Standards

Every UI component in the PG Manager app is designed to be highly reusable, consistent, and fully accessible.

### 9.1 Interactive Buttons
* **Anatomy:** Left-aligned leading icon (optional), centered label (`type-button-lg`), and right-aligned trailing icon (optional).
* **Color Usage:** Primary actions use solid `color-primary`. Secondary actions use outline borders (`color-outline`) on flat backgrounds.
* **DO:** Maintain a minimum button height of **52dp** to ensure it is easy to tap on mobile screens.
* **DON'T:** Use buttons with truncated text; text labels MUST always fit comfortably on a single line.

### 9.2 Property & Room Inventory Cards
* **Anatomy:** Prominent room name (`type-title-lg`), active capacity bar (e.g., "1 of 3 occupied"), and a status badge ("Vacant", "Occupied", "Maintenance").
* **Touch Action:** Tapping anywhere on the card navigates the user to that specific room's detail view.
* **Visual Polish:** Ensure the card backdrop uses a subtle tonal elevation (`Level 1`), paired with rounded corners (`Medium / 16dp`) to establish clear visual structure.

---

## 10. Forms & Data Entry

Entering tenant profiles, room configurations, and rent amounts MUST feel fast and error-free on mobile touchscreens.

### 10.1 Input Field Standards
* **Filled Style:** Fields MUST use the Material 3 Filled design system with clear label headers, placeholder guides, and a subtle bottom divider line.
* **Dynamic Keyboard Support:** Form fields MUST configure context-aware virtual keyboards:
  * **Phone Fields:** Enforce number-only entry layouts with automatic country code indicators (`+91`).
  * **Currency Inputs:** Display clear leading currency symbols (`₹`) and automatically show decimal numeric keypads.
* **Instant Validation Checks:** Show error validation warnings dynamically only after the user taps out of a field or attempts to submit the form, preventing premature or annoying error states while typing.

---

## 11. Accessibility (a11y)

The application MUST be fully accessible to a wide range of users, including property owners managing accounts outdoors or in low-visibility environments.

### 11.1 Accessibility Metrics
* **Touch Targets:** ALL interactive elements (buttons, checkboxes, list items, menus) MUST meet or exceed a minimum tap target size of **48dp x 48dp**.
* **Color Contrast:** All text configurations MUST maintain contrast ratios that comply with WCAG AA guidelines (4.5:1 minimum).
* **Screen Reader Support:** All functional icons and image illustrations MUST declare meaningful, non-empty `contentDescription` labels. Decorative visual highlights MUST set their description targets to `null`.
* **Font Scaling Support:** Text sizes MUST utilize scale-independent pixels (`sp`) exclusively, allowing screens to render readable layouts even under high system font-scaling preferences.

---

## 12. Responsive Design

PG Manager is designed to look polished on a variety of Android hardware layouts, from compact entry-level screens to large premium foldables.

### 12.1 Screen Optimization Rules
* **Small Phones (<360dp width):** Compress large horizontal padding blocks down to `space-sm` (8dp), and adjust Display typography sizes down by 4sp to prevent text clipping.
* **Large Phones (>400dp width):** Scale structural padding up to standard `space-md` (16dp) to keep layouts readable and spacious.
* **Expanded Tablets / Foldables (>600dp width):** Enforce maximum container widths of **600dp** for form cards and detail views, preventing inputs from stretching awkwardly across wide screens.

---

## 13. Dark Theme

Dark Theme is highly requested by property owners managing accounts late at night. The dark theme is designed to reduce eye strain while maintaining excellent readability.

### 13.1 Design Principles
* **Avoid Flat Pure Blacks:** Use a sophisticated, deep slate palette (`#020617` and `#0B0F19`) as the primary background, avoiding flat, high-contrast pure black backgrounds.
* **Soft White Typography:** Primary headings and body copy MUST use soft slate whites (`#F8FAFC` and `#CBD5E1`) instead of harsh, high-contrast pure whites (`#FFFFFF`), keeping reading comfortable in low-light environments.
* **Muted Accents:** Scale down the brightness of success, warning, and error colors by 10% to prevent them from feeling too harsh on dark backgrounds.

---

## 14. Illustrations & Empty States

An empty screen is a great opportunity to guide and onboard owners who are setting up their property portfolios for the first time.

### 14.1 Visual Brand Guidelines
* **Illustration Style:** Use elegant, geometric, and minimalist flat vector shapes in muted slate tones. Avoid using complex, cartoonish, or overly busy multi-color drawings.
* **Action-Oriented Prompts:** Empty states MUST clearly display:
  1. A friendly, high-quality vector placeholder icon.
  2. A clean, descriptive title explaining what's missing (e.g., "No active tenants yet").
  3. A helpful, step-by-step tip guiding the user on how to add their first record.
  4. A prominent, primary action button positioned directly below the prompt (e.g., "Add Tenant").

---

## 15. Design Tokens

Design Tokens serve as the foundational building blocks of our UI implementation, translating design guidelines directly into clean, platform-agnostic code variables.

### 15.1 Core Token Definition File (`theme_tokens.json`)

```json
{
  "color": {
    "primary": "#0F172A",
    "primary_container": "#E2E8F0",
    "background": "#F8FAFC",
    "surface": "#FFFFFF",
    "success": "#15803D",
    "warning": "#B45309",
    "error": "#B91C1C",
    "outline": "#E2E8F0"
  },
  "spacing": {
    "xs": 4,
    "sm": 8,
    "md": 16,
    "lg": 24,
    "xl": 32
  },
  "radius": {
    "small": 8,
    "medium": 16,
    "large": 28,
    "full": 9999
  }
}
```

---

## 16. Design Governance

To keep the application's user interface consistent and cohesive as new features are added, we enforce a strict design governance process:

* **Token Versioning:** Modifications to our primary design tokens (colors, spacing, typography) are managed as explicit versioned updates (e.g., `v1.0.0` to `v1.1.0`), preventing breaking layout changes.
* **Adding New Components:** If a new feature requires a custom UI component, it MUST be submitted to the Design System Council for review and approved for consistency before it is added to the shared code libraries.
* **Component Deprecation:** Deprecated visual components MUST be clearly marked in the code and scheduled for removal in the next major release cycle, ensuring the codebase remains clean and modern.

---

## 17. Quality & Consistency Standards

Maintaining visual and interactive consistency is critical to providing a premium, high-quality user experience across the entire product.

* **Automated Layout Audits:** Layout alignments, button touch targets, and color contrasts are validated during automated testing cycles to catch potential issues early.
* **Design Compliance Reviews:** Before any major release, our design system leads will perform a visual audit of all screens and interactions, verifying that the implementation perfectly matches our design system standards.

---

## 18. Design Readiness Assessment

* [x] **Material Design 3 Colors Configured** (Slate Minimalist palette frozen)
* [x] **Dual Font Typography Paired** (Inter + Space Grotesk tokens established)
* [x] **8dp Spacing System Grid Defined** (Consistent rhythm and gutters mapped)
* [x] **Shape Corner Radiuses Standardized** (Rounded corner tokens declared)
* [x] **Tonal Elevation Surfaces Defined** (Depth levels and backdrops mapped)
* [x] **Responsive and Accessibility Rules Set** (WCAG AA and 48dp touch targets active)
* [x] **Dark Theme Guidelines Completed** (Low-strain slate dark palette frozen)

### Design System Maturity Rating: **100%**

The Design System Specification is complete, internally consistent, and ready to guide implementation. The design language and tokens are officially declared **Approved for UI Implementation**.
