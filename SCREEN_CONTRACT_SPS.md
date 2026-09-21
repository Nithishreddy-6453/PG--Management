# Screen Contract & Component Specification Document (SPS)
## PG Manager: Premium Mobile-First SaaS for Paying Guest (PG) Owners in India

**Document Version:** 1.0.0  
**Date:** July 18, 2026  
**Classification:** Technical Product Specification & Interface Contract  
**Intended Audience:** Product Designers, UI/UX Engineers, Native Android Developers, QA Engineers, System Architects  

---

## 1. Application Component Inventory

To ensure interface consistency, rapid development, and modular maintainability, all UI development must utilize this standardized set of reusable, stateless components.

### 1.1 Atomic & Interactive Components

#### 1.1.1 Primary Button
* **Purpose:** Triggers high-priority, positive actions on a screen (e.g., "Check In Tenant", "Save Changes").
* **Variants:** Active (Default), Loading (displays a centered circular progress indicator), Disabled (grayed out, non-interactive).
* **Properties:** `text: String`, `onClick: () -> Unit`, `isEnabled: Boolean`, `isLoading: Boolean`, `icon: ImageVector?`.
* **Behavior:** Features a subtle scale-down bounce on tap (0.97x scale transformation) with a Material 3 ripple effect. Minimum touch target is strictly **48.dp** in height.
* **Future Scalability:** Supports transition to a secondary background color for promotion-based buttons in paid tiers.

#### 1.1.2 Secondary Button
* **Purpose:** Handles secondary, non-destructive actions (e.g., "Cancel", "Back", "Share Receipt").
* **Variants:** Active, Disabled.
* **Properties:** `text: String`, `onClick: () -> Unit`, `isEnabled: Boolean`, `icon: ImageVector?`.
* **Behavior:** Outlined or transparent background depending on screen contrast. Features light hover states on hover/focus.

#### 1.1.3 Floating Action Button (FAB)
* **Purpose:** Provides a persistent, high-visibility shortcut to add or create resources from main directories.
* **Variants:** Expanded (with text label next to the icon, e.g., "+ Add Tenant"), Collapsed (icon only, e.g., "+").
* **Properties:** `icon: ImageVector`, `label: String?`, `isExpanded: Boolean`, `onClick: () -> Unit`.
* **Behavior:** Positioned in the bottom-right corner of the screen. When the user scrolls list elements down, the FAB collapses into an icon-only shape to maximize visual room. When scrolling stops or reverses, it smoothly expands back to include the text label.

#### 1.1.4 Search Bar
* **Purpose:** Provides a real-time, fuzzy matching search interface across primary records.
* **Variants:** Static (Dashboard Header), Active Full Screen Search Panel.
* **Properties:** `query: String`, `onQueryChange: (String) -> Unit`, `placeholder: String`, `onClear: () -> Unit`.
* **Behavior:** Automatically shows a trailing "Clear" cross icon when characters are entered. Immediately autofocuses when entering the dedicated search route.

### 1.2 Layout & Structural Cards

#### 1.2.1 Room Card
* **Purpose:** Represents a single physical room configuration in a floor grid.
* **Properties:** `roomNumber: String`, `floorLabel: String`, `occupiedCount: Int`, `capacity: Int`, `monthlyRate: Double`.
* **Behavior:** Displays a visual progress bar or block container indicating filled beds. Colored in a calm, modern slate theme:
  * Full occupancy = Muted Slate Blue
  * Partial occupancy = Mixed Gray/Light Blue
  * Complete vacancy = Bright Teal Accent

#### 1.2.2 Tenant Card
* **Purpose:** Displays high-priority tenant details in list registries.
* **Properties:** `name: String`, `roomNumber: String`, `bedLabel: String`, `phone: String`, `isKycVerified: Boolean`.
* **Behavior:** Includes quick-action buttons for Call, Message, and Profile Details. Indicates missing KYC status with a subtle, non-destructive caution yellow dot.

#### 1.2.3 Rent Card
* **Purpose:** Summarizes a single tenant's billing cycle status in payment directories.
* **Properties:** `tenantName: String`, `roomNumber: String`, `dueDate: String`, `amount: Double`, `status: String` ("Paid", "Pending", "Overdue").
* **Behavior:** Displays a prominent status-colored background pill:
  * Paid = Muted Mint Green
  * Pending = Neutral Charcoal Gray
  * Overdue = Alert Coral Red

#### 1.2.4 Expense Card
* **Purpose:** Represents an itemized ledger entry for a property cost.
* **Properties:** `amount: Double`, `category: String`, `notes: String`, `date: String`.
* **Behavior:** Displays category icons (e.g., wrench for "Repairs", plate for "Food"). Supports quick swiping left to expose a Delete button.

#### 1.2.5 Statistic Card (KPI Card)
* **Purpose:** Highlights core business performance totals on the Dashboard.
* **Properties:** `title: String`, `value: String`, `subtitle: String?`, `trendIcon: ImageVector?`, `backgroundColor: Color`.
* **Behavior:** Large-format display typography. Tapping triggers a deep-link navigation path directly to the pre-filtered directory associated with that metric.

---

## 2. Design Tokens

Design tokens establish a cohesive aesthetic baseline across all platforms, ensuring the app feels modern, premium, and visually balanced.

```
+-----------------------------------------------------------------------------+
|                            DESIGN SYSTEM SUMMARY                            |
+-----------------------------------------------------------------------------+
| Token Category      | Value / Specification     | Design Purpose            |
+-----------------------------------------------------------------------------+
| Baseline Grid       | 8dp Grid (Multiples)      | Alignment & padding consistency|
| Layout Margins      | 16dp (Horizontal/Vertical) | Standard screen margins   |
| Corner Radii        | Large (16dp), Medium (12dp)| Soft, approachable shapes  |
| Typography          | Display (Space Grotesk)   | Premium visual hierarchy  |
|                     | Body (Inter)              | Legibility & clean reading|
| Touch Targets       | Minimum 48dp x 48dp       | Secure, error-free tapping|
+-----------------------------------------------------------------------------+
```

* **Spacing System:** Based on an 8dp grid (4dp, 8dp, 12dp, 16dp, 24dp, 32dp, 48dp) to maintain structural consistency and visual balance across varying Android screen sizes.
* **Layout Margins:**
  * Screen Edge Margins: **16.dp** (Standard Mobile), **24.dp** (Tablet Supporting Pane).
  * Card-to-Card Spacing: **12.dp** vertically, **8.dp** horizontally in grids.
* **Corner Radius (Rounding):**
  * Primary Cards & Bottom Sheets: **16.dp** for a modern, approachable aesthetic.
  * Buttons, Input Fields, & Chips: **12.dp** for compact, readable layouts.
  * Status Pills: Fully rounded (**100.dp**) for clean visual categorization.
* **Elevation & Shadows:**
  * Base Cards: Elevation **0.dp** to **1.dp** (utilizing subtle borders instead of heavy shadows to maintain a clean, flat design).
  * Bottom Sheets & Active Modals: Elevation **8.dp** to establish visual depth over background elements.
* **Typography Hierarchy:**
  * Primary Display Headings (KPI values): **32.sp**, Semi-Bold, paired with *Space Grotesk* for a premium, high-impact aesthetic.
  * Section Headers: **18.sp**, Medium, with clean letter-spacing (tracking).
  * Body Text: **14.sp** to **16.sp**, Regular, using *Inter* to ensure high legibility.
  * Micro/Helper Labels: **12.sp**, Medium, used for secondary dates and status chips.
* **Icon Sizing:**
  * Primary Actions (FAB, Top Bar): **24.dp** x **24.dp** icons with **48.dp** overall touch target bounding boxes.
  * Secondary Category Icons (Cards, Tiles): **18.dp** x **18.dp** centered.
* **Universal Motion & Animations:**
  * Dynamic Transition Duration: **250ms** with standard cubic bezier easing (`[0.2, 0.0, 0.0, 1.0]`) to make animations feel fluid and premium.
* **Minimum Touch Targets:** All interactive items (chips, buttons, sliders, icons) feature a touch target size of **at least 48dp x 48dp** to support reliable selection.

---

## 3. Complete Screen Contracts

This section defines the strict user interface contract and behavioral guidelines for every screen in the PG Manager MVP.

### SCR_001: Splash Screen
* **Purpose:** Initializes application state and routes users based on their active session.
* **Business Objective:** Prevent app-launch lag and maintain security by checking local database integrity on launch.
* **Primary User Goal:** Instant, secure app opening with clear feedback.
* **Entry Points:** System launcher icon.
* **Exit Points:** SCR_002 (Owner PIN Login) on initialization complete.
* **Displayed Information:** App branding logo, quiet loading indicator.
* **Primary Action:** Automated system checks.
* **Dependencies:** Database validation helper.
* **Validation Rules:** System must verify database integrity before routing.
* **Loading State:** Center-screen brand logomark with a smooth, pulsing opacity transition (duration 1200ms).
* **Error State:** If local database reading fails, show a centered dialog with a "Restore Database" option.
* **Performance Expectation:** Transitions to SCR_002 in **under 1500ms** from icon tap.

---

### SCR_002: Owner PIN Login
* **Purpose:** Secures property records and tenant personal details behind a 4-digit security PIN.
* **Business Objective:** Ensure compliance with data-privacy regulations and establish user trust.
* **Primary User Goal:** Securely and quickly unlock their business ledger.
* **Entry Points:** SCR_001 (Splash Screen) or returning from background state.
* **Exit Points:** SCR_003 (Main Dashboard) on correct PIN entry.
* **Displayed Information:** PG Name (e.g., "Emerald Stays"), 4 secure PIN input indicator dots, clean numeric keypad.
* **Required Inputs:** 4-digit numeric code.
* **Primary Action:** PIN entry complete triggers automatic authorization validation.
* **Secondary Actions:** "Forgot PIN" recovery path (displays owner security question).
* **Dependencies:** Local `OwnerProfileEntity` credentials.
* **Validation Rules:** Code must contain exactly 4 digits. Numbers only.
* **Success State:** PIN verification triggers a clean fade-out transition, routing immediately to the Dashboard.
* **Failure State:** If incorrect, clear entered inputs, trigger a horizontal shake animation on the entry dots, and show an error toast.
* **Performance Expectation:** Verification occurs in **under 300ms** after entering the 4th digit.

---

### SCR_003: Main Dashboard
* **Purpose:** Serves as the primary operational hub, highlighting key performance metrics and quick actions.
* **Business Objective:** Provide immediate, actionable insight into property occupancies and collections to prevent revenue leakage.
* **Primary User Goal:** View total outstanding rent, occupied bed counts, and log transactions quickly.
* **Entry Points:** SCR_002 (PIN Login) or persistent Bottom Navigation.
* **Exit Points:** SCR_004 (Rooms), SCR_008 (Tenants), SCR_011 (Rent Hub), SCR_013 (Expenses), SCR_015 (Profile).
* **Displayed Information:**
  * Welcome greeting with the PG's name.
  * **Occupancy KPI Card:** e.g., "34/40 Beds Occupied (85%)".
  * **Outstanding Rent KPI Card:** e.g., "₹45,000 Overdue".
  * **Net Month Margin KPI Card:** e.g., "₹1,85,000 Net Profit".
  * **Quick Action Sheet:** "Log Payment", "Add Tenant", "Record Expense".
* **Required Data:** Compiled aggregates from SQLite tables (`rooms`, `tenants`, `payments`, `expenses`).
* **Primary Action:** Prominent "+" Floating Action Button (FAB) positioned in the bottom-right corner.
* **Navigation Rules:** Tapping a KPI card navigates directly to the pre-filtered tab representing that metric (e.g., tapping "Outstanding Rent" routes to the Rent Hub with the "Overdue" filter active).
* **Performance Expectation:** Loads compiled database statistics in **under 500ms** with zero screen flicker.

---

### SCR_004: Room Management Directory
* **Purpose:** Displays an interactive floor-by-floor list of rooms and bed-occupancy statuses.
* **Business Objective:** Prevent lost revenue by helping owners identify empty beds.
* **Primary User Goal:** View bed availability to place walk-in inquiries quickly.
* **Entry Points:** Bottom Navigation (Rooms Tab) or Dashboard Deep-links.
* **Exit Points:** SCR_005 (Room Details), SCR_006 (Add/Edit Room).
* **Displayed Information:** Floor-by-floor sections containing a grid of room cards (each showing room number, bed capacity, occupied beds, and price).
* **Required Data:** Full list of `RoomEntity` elements and associated `TenantEntity` counts.
* **Primary Action:** "+ Add Room" FAB.
* **Secondary Actions:** Scrollable filter chips (All, Vacant, Occupied, Full) immediately below the header.
* **Loading State:** Shimmer skeleton placeholders representing the room cards.
* **Empty State:** Shows a clean illustration with the text: *"Set up your first room to begin tracking occupancies."*
* **Offline State:** Displays local database records seamlessly.

---

### SCR_005: Room Details Screen
* **Purpose:** Displays individual room configurations, bed assignments, and resident details.
* **Business Objective:** Ensure correct occupancy placement and manage room-sharing details.
* **Primary User Goal:** Check bed availability, view tenant details, and assign walk-ins.
* **Entry Points:** SCR_004 (Room Management Directory).
* **Exit Points:** SCR_004 (Room Directory), SCR_007 (Add Tenant), SCR_009 (Tenant Details).
* **Displayed Information:** Room Number, floor level, monthly rate per bed, and a list of physical beds (e.g., Bed A, Bed B) showing tenant names and check-in dates.
* **Required Data:** Specific `RoomEntity` record and linked `TenantEntity` profiles.
* **Primary Action:** Tapping an "Empty Bed" card opens the Add Tenant form, pre-filled with the room number and bed ID.
* **Secondary Actions:** "Delete Room" option in the top-right menu (disabled if any beds in the room are currently occupied).
* **Validation Rules:** Prevents checking a tenant into an already occupied bed.
* **Performance Expectation:** Renders room configuration and bed states in **under 300ms**.

---

### SCR_006: Add/Edit Room Form
* **Purpose:** Configures and saves room details, including floor levels, capacities, and rates.
* **Business Objective:** Build a flexible property inventory map that supports diverse layouts.
* **Primary User Goal:** Add new rooms or update room prices.
* **Entry Points:** SCR_004 (Room Management Directory FAB).
* **Exit Points:** SCR_004 (Room Directory).
* **Required Inputs:** Room Number, Floor Selector (Ground, 1st, 2nd, 3rd, 4th), Bed Capacity (1, 2, 3, 4), and Monthly Rate per Bed.
* **Primary Action:** "Save Room" Primary Button.
* **Secondary Actions:** "Cancel" Outlined Button.
* **Dependencies:** `RoomDao` insertion.
* **Validation Rules:**
  * Room Number cannot be blank.
  * Rate must be a positive number greater than zero.
  * Duplicate room numbers are prevented with an on-screen warning.
* **Success State:** Displays a green confirmation snackbar: *"Room [Number] created successfully."*

---

### SCR_007: Add Tenant Form
* **Purpose:** Registers a new tenant and assigns them to an available bed.
* **Business Objective:** Ensure accurate, compliant resident records and security deposit tracking.
* **Primary User Goal:** Register a tenant's contact information and assign them to a room.
* **Entry Points:** SCR_005 (Room Details Empty Bed Tap) or SCR_008 (Tenant Registry FAB).
* **Exit Points:** SCR_005 (Room Details), SCR_008 (Tenant Registry).
* **Required Inputs:** Tenant Name, Phone Number, Email, Emergency Contact, Room Number, Bed ID, Monthly Rent, Security Deposit, Move-in Date, and KYC Document Type (Aadhaar Card, PAN Card, None).
* **Primary Action:** "Confirm Check-in" Primary Button.
* **Secondary Actions:** "Cancel" Button.
* **Dependencies:** `TenantDao` insertion and automatic initial billing generation in `RentPaymentDao`.
* **Validation Rules:** See Section 7 (Validation Matrix).
* **Success State:** Green confirmation snackbar: *"Tenant [Name] registered successfully in Room [Number]."*, accompanied by a database write that marks the assigned bed as occupied.

---

### SCR_008: Tenant Registry
* **Purpose:** Provides a searchable directory of active, past, and upcoming residents.
* **Business Objective:** Serve as the central system of record for tenant contact and billing details.
* **Primary User Goal:** Quickly find a resident's profile or contact number.
* **Entry Points:** Bottom Navigation (Tenants Tab).
* **Exit Points:** SCR_009 (Tenant Details), SCR_007 (Add Tenant).
* **Displayed Information:** Scrollable list of tenant cards showing name, room number, phone, and KYC verification status. Includes a persistent search bar in the header.
* **Primary Action:** "+ Add Tenant" FAB.
* **Secondary Actions:** Real-time search by name, room number, or phone.
* **Loading State:** Shimmer skeleton placeholders representing the tenant lists.
* **Empty State:** Shows the text: *"No active tenants found. Tap '+' to register a resident."*

---

### SCR_009: Tenant Details Profile
* **Purpose:** Displays a tenant's full profile, KYC document status, and rent collection history.
* **Business Objective:** Provide a clear history of a tenant's lease and payments to prevent disputes.
* **Primary User Goal:** Verify contact information, view uploaded ID cards, and record payments.
* **Entry Points:** SCR_008 (Tenant Registry) or SCR_005 (Room Details).
* **Exit Points:** SCR_008 (Tenant Registry), SCR_010 (KYC View), SCR_012 (Payment Sheet).
* **Displayed Information:** Tenant Name, phone, email, emergency contact, active room/bed details, security deposit, move-in date, KYC document preview card, and a chronological history of rent payments.
* **Primary Action:** "Record Rent Payment" Button (visible if the active month's rent is pending).
* **Secondary Actions:**
  * "Share Tenant Record" (copies profile details to the clipboard).
  * "Check Out Tenant" (removes the tenant and frees up their bed, protected by a confirmation dialog).
* **Performance Expectation:** Pulls complete tenant profiles and history logs in **under 400ms**.

---

### SCR_010: KYC Document Viewer
* **Purpose:** Displays high-resolution views of uploaded tenant identity cards.
* **Business Objective:** Ensure compliance with local police verification regulations.
* **Primary User Goal:** View or verify a resident's ID details.
* **Entry Points:** SCR_009 (Tenant Details).
* **Exit Points:** SCR_009 (Tenant Details).
* **Displayed Information:** High-contrast full-screen rendering of the uploaded ID card image, document type label, and date uploaded.
* **Primary Action:** "Back" Arrow (pops the screen).
* **Secondary Actions:** "Share Document" option.
* **Performance Expectation:** Renders image files smoothly with pinch-to-zoom support.

---

### SCR_011: Rent Tracking Hub
* **Purpose:** Tracks and manages monthly rent collections and payment statuses.
* **Business Objective:** Directly accelerate collections and reduce outstanding balances.
* **Primary User Goal:** Identify unpaid rents and record collections.
* **Entry Points:** Bottom Navigation (Rent Tab) or Dashboard Overdue KPI deep-link.
* **Exit Points:** SCR_012 (Payment Sheet), SCR_009 (Tenant Details).
* **Displayed Information:** Segmented tab layout (Overdue, Pending, Paid) displaying lists of billing cards showing tenant name, room number, amount, billing month, and due date.
* **Primary Action:** Tapping a card opens the Record Payment Sheet (SCR_012).
* **Secondary Actions:** "Copy Reminder Link" (copies a pre-formatted reminder to the clipboard).
* **Validation Rules:** Payments can only be updated from "Unpaid/Pending" to "Paid."

---

### SCR_012: Record Payment Sheet (Slide-up Modal)
* **Purpose:** Logs a collected rent payment, specifying payment mode and date.
* **Business Objective:** Ensure accurate cash-flow tracking and maintain clear payment records.
* **Primary User Goal:** Mark rent as paid and record the payment details.
* **Entry Points:** SCR_011 (Rent Tracking Hub) or SCR_009 (Tenant Details).
* **Exit Points:** Bottom Sheet slide down to SCR_011 or SCR_009.
* **Displayed Information:** Tenant Name, Outstanding Amount, Billing Month, Payment Mode Selector (UPI, Cash, Bank Transfer), Amount Received, and Date Collected.
* **Required Inputs:** Amount Received, Payment Mode.
* **Primary Action:** "Confirm Payment" Button.
* **Success State:** Displays a green confirmation snackbar: *"Payment of ₹[Amount] received from [Name] for [Month]."*, and updates the database to mark the payment cycle as paid.

---

### SCR_013: Expense Registry
* **Purpose:** Displays a chronological ledger of property operational costs.
* **Business Objective:** Keep property operational costs organized and visible.
* **Primary User Goal:** Log maintenance, grocery, and utility costs as they occur.
* **Entry Points:** Bottom Navigation (Expenses Tab) or Dashboard Expense KPI deep-link.
* **Exit Points:** SCR_014 (Log Expense Sheet).
* **Displayed Information:** Chronological list of expenses, grouped by month, with category icons (Repairs, Food, Utilities, Salary, Other) and category filters.
* **Primary Action:** "+ Log Expense" FAB.
* **Secondary Actions:** Swipe left on an expense card to expose a Delete button (which triggers a confirmation dialog).
* **Empty State:** Shows a clean illustration with the text: *"No expenses logged this month."*

---

### SCR_014: Log Expense Sheet (Slide-up Modal)
* **Purpose:** Records expenditures with categorization and notes.
* **Business Objective:** Ensure accurate profit calculations and categorize business expenses.
* **Primary User Goal:** Log a property cost instantly before forgetting details.
* **Entry Points:** SCR_013 (Expense Registry FAB) or Dashboard "+" Action Sheet.
* **Exit Points:** Bottom Sheet slide down to SCR_013.
* **Required Inputs:** Amount (Numeric), Category (Selector), Expense Date (Defaults to today), and optional notes.
* **Primary Action:** "Log Expense" Primary Button.
* **Validation Rules:** Amount must be greater than zero.
* **Success State:** Slide-down dismissal, a green confirmation snackbar, and real-time updates to the dashboard margins.

---

### SCR_015: Owner Profile Screen
* **Purpose:** Manages basic property and business details.
* **Business Objective:** Personalize receipt templates and configure basic app settings.
* **Primary User Goal:** Update property branding and payment settings.
* **Entry Points:** Bottom Navigation (Profile Tab).
* **Exit Points:** SCR_016 (Settings & PIN), SCR_002 (PIN Login on lock).
* **Displayed Information:** Owner Name, PG Name, phone number, default UPI ID (e.g., nithish@okaxis), and business summary stats.
* **Primary Action:** "Edit Profile Details" form toggle.
* **Secondary Actions:** "Lock App Now" (logs the user out and returns to SCR_002).

---

### SCR_016: Settings & PIN Configuration
* **Purpose:** Edits security configurations and manages local database backups.
* **Business Objective:** Protect application security and ensure data recovery options are available.
* **Primary User Goal:** Change their security PIN or export a database backup.
* **Entry Points:** SCR_015 (Owner Profile).
* **Exit Points:** SCR_015 (Owner Profile).
* **Required Inputs:** Old PIN, New PIN, Confirm New PIN.
* **Primary Action:** "Update Security PIN" Primary Button.
* **Secondary Actions:** "Backup Local Database" (exports a secure, shareable database backup file).
* **Validation Rules:** Old PIN must match the existing PIN, and the new PIN must be verified correctly.

---

## 4. Component Mapping

To streamline engineering planning, the table below maps each screen to its specific reusable atomic and layout components:

```
+---------------------------------------------------------------------------------+
|                           SCREEN COMPONENT MAPPING                              |
+---------------------------------------------------------------------------------+
| Screen ID & Name           | Reusable Component Dependencies                    |
+---------------------------------------------------------------------------------+
| SCR_003: Main Dashboard    | Statistic Cards, Primary FAB, Shimmer Skeletons   |
| SCR_004: Rooms Directory   | Room Cards, Scrollable Filter Chips, Search Bar    |
| SCR_005: Room Details      | Bed Cards, Secondary Buttons, Alert Dialogs       |
| SCR_007: Add Tenant        | Custom Text Inputs, Date Pickers, Secondary Buttons|
| SCR_009: Tenant Profile    | Rent Cards, Secondary Buttons, Confirm Dialogs    |
| SCR_011: Rent Hub          | Rent Cards, Scrollable Filter Chips, Snackbars    |
| SCR_013: Expense Registry  | Expense Cards, Category Filter Chips, Primary FAB  |
+---------------------------------------------------------------------------------+
```

---

## 5. Interaction Contracts

This section defines the exact system and user-interface response required for key application tasks:

### 5.1 Quick Check-in a Tenant
* **Trigger:** Taps a "Vacant Bed" card on SCR_005.
* **System Response:** Opens SCR_007 (Add Tenant form) with the Floor, Room, and Bed ID fields automatically pre-filled.
* **Animation:** Smooth forward-navigation slide-in transition from the right edge.
* **Data Update:** Upon form submission, inserts a new `TenantEntity` and generates an initial rent invoice in `RentPaymentEntity`.
* **UI Update:** Room Details (SCR_005) immediately updates to display the bed status as "Occupied" with the tenant's name, and the Dashboard occupancy stats refresh in real-time.
* **Feedback:** A slide-up snackbar confirms the check-in with a green verification tick icon.

### 5.2 Swipe-to-Delete Expense
* **Trigger:** Swipes left on an expense card in SCR_013.
* **System Response:** Reveals a red "Delete" action button next to the card.
* **Animation:** Fluid card slide offset matching the swipe gesture.
* **Data Update:** Tapping "Delete" prompts a center-screen confirmation dialog. Once confirmed, deletes the record from `ExpenseEntity`.
* **UI Update:** The deleted card fades out, other list items slide up smoothly to fill the empty space, and the dashboard expense totals update in real-time.
* **Feedback:** A gray "Expense Deleted" snackbar appears, featuring an "Undo" button that remains visible for 4 seconds.

---

## 6. Business Rules

These operational rules govern the application's logic, database integrity, and validation behaviors:

```
                  +----------------------------------------------+
                  |         CORE BUSINESS LOGIC CODES            |
                  +----------------------------------------------+
                                         |
         +-------------------------------+-------------------------------+
         |                               |                               |
         v                               v                               v
+------------------+           +------------------+             +------------------+
| BED ASSIGNMENTS  |           | DELETE ACTIONS   |             | ENTRY VALUES     |
| Limit 1 tenant   |           | Block rooms if   |             | Amounts must be  |
| per bed. Max capacity|       | occupied by active|            | positive (>0).   |
| enforced strictly.|          | tenant records.  |             | No negative inputs|
+------------------+           +------------------+             +------------------+
```

1. **Bed Allocation Rule:** A single bed can only be assigned to **one active tenant** at a time. The system will prevent checking a new tenant into an occupied bed.
2. **Room Capacity Enforcement:** The total number of beds assigned to a room cannot exceed the room's configured capacity (e.g., a double sharing room can contain a maximum of 2 active bed allocations).
3. **Room Deletion Constraints:** A room cannot be deleted if any beds in the room are currently occupied by active tenants. The owner must check out or re-assign all active residents before the delete option is enabled.
4. **Tenant Deletion Safeguards:** Checking out a tenant removes their profile and frees up their assigned bed, but their historical payment records are retained within the system to ensure historical cash-flow data remains accurate.
5. **No Negative Values:** All financial transactions—including rent payments, security deposits, and operational expenses—must be logged as positive numbers greater than zero.
6. **Unified Billing Dates:** The system automatically initializes rent records for checked-in tenants based on their check-in date, setting their recurring monthly billing cycle from that day forward.

---

## 7. Validation Matrix

The table below defines the validation rules, constraints, and error behaviors for all form inputs within the application:

| Field Name | Mandatory | Min Length / Value | Max Length / Value | Allowed Input Types | Real-time Error Message |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Tenant Name** | Yes | 3 Characters | 50 Characters | Alphabetic & Spaces | *"Name must be at least 3 characters long."* |
| **Phone Number**| Yes | 10 Digits | 10 Digits | Numeric only | *"Please enter a valid 10-digit phone number."* |
| **Rent Amount** | Yes | ₹100 | ₹1,00,000 | Positive Decimal | *"Rent must be between ₹100 and ₹1,00,000."* |
| **Security Dep**| Yes | ₹0 | ₹5,00,000 | Positive Decimal | *"Security deposit must be ₹0 or greater."* |
| **Room Number** | Yes | 1 Character | 10 Characters | Alphanumeric | *"Please enter a room identifier."* |
| **Bed Capacity**| Yes | 1 Bed | 4 Beds | Selected Integer | *"Bed capacity must be between 1 and 4."* |
| **Expense Amt** | Yes | ₹1 | ₹10,000,000 | Positive Decimal | *"Expense must be a positive number greater than ₹0."* |
| **Owner Name**  | Yes | 3 Characters | 50 Characters | Alphabetic & Spaces | *"Please enter your full name."* |
| **UPI ID**      | No | N/A | 50 Characters | Standard VPA format | *"Please enter a valid UPI address (e.g., name@okaxis)."* |

---

## 8. State Matrix

To deliver a premium, responsive user experience, the interface changes dynamically based on the current state of the application's data and network:

* **Initial State (Unconfigured):** Upon first launching the app, the screens display clean, helpful onboarding guides. The Room tab, for example, guides the owner to add their first room with a clear, direct layout.
* **Loading State (Active Query):** When pulling data from the database, list views display quiet Shimmer skeletons that mimic the shape of the incoming cards. Interactive buttons transition to a loading state to prevent double-submitting forms.
* **Success State (Action Complete):** Successfully saving an entry triggers a brief confirmation animation, dismisses forms smoothly, and shows a green, non-intrusive snackbar confirming the transaction.
* **Offline State (Network Lost):** A subtle, non-intrusive banner appears at the top of the screen (e.g., *"Offline Mode — All changes saved locally"*), assuring the owner that their work is safe and operations will continue uninterrupted.
* **Empty State (No Records):** Directories show clean, friendly illustrations with clear call-to-action buttons (e.g., *"All rents paid! No outstanding payments found."*), keeping the interface approachable.

---

## 9. Edge Cases

The table below outlines how the application handles unusual or critical scenarios, ensuring data security and system stability at all times:

| Scenario / Edge Case | System Response / Expected Behavior | UX Design Safeguard |
| :--- | :--- | :--- |
| **Duplicate Room Number** | The database blocks the entry, and the form displays a red input error: *"Room identifier already exists."* | Real-time input validation on the form. |
| **Duplicate Phone Number** | The database permits the entry (supporting siblings in different rooms), but displays a quiet warning tag. | Reassuring on-screen validation indicator. |
| **Attempt to Delete Occupied Room** | The "Delete" option is completely hidden or disabled, showing a helper tooltip: *"Cannot delete an occupied room."* | Clear, informative tooltip helper. |
| **Future Move-in Dates** | The tenant profile is saved, but their assigned bed remains marked as "Available" until the move-in date arrives. | Visual "Upcoming Tenant" tag on the bed status. |
| **App Closed During Entry** | Draft entries are saved to temporary local memory, allowing the owner to resume where they left off on next launch. | Automatic draft recovery sheet on relaunch. |
| **Screen Rotation Changes** | The UI layout adapts dynamically to fit the new aspect ratio, maintaining form entries and scroll positions. | Container-based fluid Jetpack Compose layouts. |
| **Low-End Android Device** | Animations are simplified (disabling heavy transitions) to maintain a highly responsive, fluid feel. | Automated device hardware resource matching. |

---

## 10. Performance Contracts

To maintain a premium, responsive feel, all development must meet these strict, measurable performance targets:

```
+---------------------------------------------------------------------------------+
|                            PERFORMANCE SLAS (TARGETS)                           |
+---------------------------------------------------------------------------------+
| Transaction Metric         | Max Target SLA | Architectural Enforced Rule       |
+---------------------------------------------------------------------------------+
| Splash Initialization      | < 1500 ms      | Fast DB verification check        |
| Dashboard Initial Load     | < 500 ms       | Optimized database index queries  |
| Directory Search Query     | < 100 ms       | Real-time on-memory collection map|
| Write Tenant Record        | < 300 ms       | Non-blocking background thread    |
| Form Dismiss Transitions   | < 200 ms       | Hardware-accelerated transitions  |
+---------------------------------------------------------------------------------+
```

* **Target Frame Rate:** The application must run at a consistent **60 FPS** on standard Android devices, utilizing optimized Jetpack Compose recompositions to prevent lag.
* **Maximum Navigation Depth:** Accessing any functional screen or primary feature must require **no more than 3 steps** from the main dashboard.
* **Offline Responsiveness:** All primary database reads and writes (room setups, tenant additions, payment logging) must execute locally on the device, ensuring instant, offline-capable performance.

---

## 11. Analytics & Event Tracking

To monitor feature adoption and user behavior, the application will track the following standard business events:

* `screen_viewed` (Parameters: `screen_id`, `screen_name`) - Fired when any screen in Section 3 is loaded.
* `room_created` (Parameters: `room_number`, `capacity`, `rate`) - Logged when a new room is added.
* `tenant_registered` (Parameters: `rent_amount`, `deposit`, `kyc_type`) - Fired when a tenant check-in is complete.
* `payment_recorded` (Parameters: `billing_month`, `amount`, `payment_mode`) - Logged when a rent payment is recorded.
* `expense_logged` (Parameters: `category`, `amount`) - Fired when an operational cost is recorded.
* `security_pin_changed` - Logged when the owner updates their security PIN in settings.
* `database_backed_up` - Fired when the owner exports a database backup file.

---

## 12. Developer Notes & Architecture Guidelines

These technical guidelines help ensure a clean, maintainable, and robust implementation:

```
+---------------------------------------------------------------------------------+
|                           ARCHITECTURE GUIDELINES                               |
+---------------------------------------------------------------------------------+
| Guideline Category | Design System Implementation Rules                         |
+---------------------------------------------------------------------------------+
| Local Database     | Use Room with SQLite. Use UUIDs instead of integer keys to |
|                    | support future Firebase cloud sync without data conflicts. |
| State Management   | Use Architecture Components ViewModels with StateFlow.      |
| Thread Safety      | All database writes must run on non-blocking IO threads.    |
| Extensibility      | Build layouts as small, modular Jetpack Compose elements.   |
+---------------------------------------------------------------------------------+
```

* **Avoid Common Pitfalls:** Ensure all database operations are run on background threads using Kotlin Coroutines (`Dispatchers.IO`) to prevent thread blocking and UI lag.
* **Testing Guidelines:** Write robust unit tests to verify database insertions and transaction updates, and use Robolectric to test critical user flows (e.g., checking in a tenant).
* **Future-Proofing:** Write modular, decoupled business logic, ensuring components are highly reusable and easy to extend as the application scales.

---

## 13. QA Acceptance Criteria

The following testable criteria define when features are complete and ready for release:

### 13.1 Feature: Tenant Check-in and Bed Allocation
* **GIVEN:** A room has at least one vacant bed.
* **WHEN:** The owner enters valid tenant details and taps "Confirm Check-in."
* **THEN:** The tenant is registered in the database, the bed is marked as occupied, and the dashboard occupancy stats update immediately.

### 13.2 Feature: Rent Collection Logging
* **GIVEN:** A tenant has an unpaid rent balance for the current month.
* **WHEN:** The owner records a cash payment and taps "Confirm Payment."
* **THEN:** The payment status updates to "Paid" in the database, the outstanding balance on the dashboard decreases, and a shareable receipt is copied to the clipboard.

### 13.3 Feature: Secure Application Launch
* **GIVEN:** PIN protection is active.
* **WHEN:** The owner opens the app and enters their correct 4-digit PIN.
* **THEN:** The app unlocks instantly and displays the main dashboard.
* **AND WHEN:** An incorrect PIN is entered, the app displays a clear input warning, shakes the indicator dots, and remains locked.

---

## 14. Implementation Readiness Checklist

* [x] **Product & Business Goals Defined** (Finalized in PRD)
* [x] **Information Architecture Complete** (Detailed in NAVIGATION_ARCHITECT)
* [x] **App Icon & Adaptive Visual Assets Prepared**
* [x] **Local Database & Room Schemas Designed** (Mapped in `Database.kt`)
* [x] **Core Reactive Repository Implemented** (Ready in `PgRepository.kt`)
* [x] **Unified State Managers Defined** (Configured in `PgViewModel.kt`)
* [x] **Reusable Component Library Defined** (Section 1)
* [x] **Form Inputs & Validations Matrix Completed** (Section 7)
* [x] **Edge Cases & Error Handling Covered** (Section 9)
* [x] **Performance SLAs & Event Analytics Defined** (Section 10 & 11)

### Implementation Readiness Score: **100%**

This technical specification is complete, verified, and ready to guide the system architecture, visual design, and development phases. All core database, business logic, and visual components are defined, ensuring a clear path to production.
