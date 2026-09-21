# Information Architecture & Navigation Blueprint
## PG Manager: Premium Mobile-First SaaS for Paying Guest (PG) Owners in India

**Document Version:** 1.0.0  
**Date:** July 18, 2026  
**Author:** Principal Mobile UX Architect & Information Architect  
**Classification:** Product Design & Engineering Blueprint  

---

## 1. Information Architecture Philosophy

### 1.1 Mobile-First Operational Reality
PG owners are highly active, on-the-move individuals who spend their days walking through corridors, supervising maintenance staff, and engaging with walk-in tenants. Their administrative tasks are brief, frequent, and interrupted. The information architecture of **PG Manager** is designed around this physical reality, transforming traditional, desk-bound property management systems (PMS) into a fast, fluid mobile operating tool.

### 1.2 Thumb-Friendly Layout and Tactile Ergonomics
Most mobile interactions occur while the user is multi-tasking (e.g., carrying keys, holding a clipboard, or talking on another phone).
* **The Thumb Zone:** All primary navigation triggers, quick-action buttons, and form confirmation controls are placed in the lower 60% of the screen. 
* **Top Screen De-prioritization:** The upper 40% of the screen is reserved exclusively for passive visual consumption (such as progress indicators, high-contrast totals, and section headings) that do not require physical interaction.

```
+-----------------------------------+
|         Passive Reading Zone      |  <- Stats, Total Balances, Profile Heads
|         (Low Thumb Reach)         |
+-----------------------------------+
|                                   |
|         Comfortable Zone          |  <- Lists, Cards, Tap Badges
|         (Medium Thumb Reach)      |
|                                   |
+-----------------------------------+
|          Active Touch Zone        |  <- Bottom Nav, FABs, Primary Button Sheets
|          (Optimal Thumb Reach)    |
+-----------------------------------+
```

### 1.3 Progressive Disclosure
To prevent cognitive overload, information is displayed in layers.
* **Level 1 (Aggregated KPI):** The Dashboard presents only high-level totals (e.g., "₹45,000 Overdue").
* **Level 2 (Categorized Directory):** Tapping the KPI leads to a clean, filtered directory (e.g., a list of tenants with pending balances).
* **Level 3 (Focused Detail):** A further tap reveals a comprehensive, interactive tenant sheet containing historical transaction logs, rent cycle settings, and KYC documents.

### 1.4 Minimized Navigation Depth (The Flat Arch Pattern)
To ensure fast operation, the screen hierarchy is limited to a maximum depth of three layers. Users are never required to drill down through complex, multi-level menus to perform daily tasks. Moving from one major business module (e.g., Room Map) to another (e.g., Expense Registry) is always a single-tap transition using the persistent Bottom Navigation bar.

### 1.5 Scalability Without Structural Redesign
As the product matures and introduces complex features like Automated QR Payments or Multi-Property Management, the core layout must remain stable. The Information Architecture uses modular containers and expandable menu points that can easily accommodate future extensions without requiring a redesign of the underlying navigation framework.

---

## 2. Complete Screen Inventory

Below is the complete inventory of all screens within the PG Manager MVP, detailing their specific purposes, entry and exit paths, and technical dependencies.

| Screen ID | Screen Name | Purpose | Primary User | Entry Points | Exit Points | Dependencies |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **SCR_001** | **Splash Screen** | Initializes the application, performs data checks, and manages routing. | System / Owner | App Launch Icon | SCR_002, SCR_003 | AppDatabase, Local Storage |
| **SCR_002** | **Owner Login PIN** | Secures tenant data and financial records via a 4-digit security PIN. | PG Owner | SCR_001, App Resume | SCR_003, App Minimize | OwnerProfileEntity, Encrypted State |
| **SCR_003** | **Main Dashboard** | Displays high-level KPIs and provides access to quick-entry tools. | PG Owner | SCR_002, Bottom Nav | SCR_004, SCR_008, SCR_011, SCR_013, SCR_015 | DashboardStats Flow |
| **SCR_004** | **Room Management** | Displays an interactive floor-by-floor layout of rooms and bed occupancies. | PG Owner | Bottom Nav, SCR_003 | SCR_005, SCR_006 | RoomEntity List Flow |
| **SCR_005** | **Room Details** | Displays bed allocations, resident details, and vacant spots for a room. | PG Owner | SCR_004 | SCR_004, SCR_007, SCR_009 | RoomEntity, TenantEntity List |
| **SCR_006** | **Add/Edit Room** | Configures room numbers, floor levels, bed capacities, and default rates. | PG Owner | SCR_004 (FAB) | SCR_004 | RoomEntity Insertion |
| **SCR_007** | **Add Tenant Form** | Records tenant personal details, links them to a bed, and sets rent terms. | PG Owner | SCR_005, SCR_008 (FAB) | SCR_005, SCR_008 | TenantEntity Insertion |
| **SCR_008** | **Tenant Registry** | Provides a searchable directory of active, past, and upcoming residents. | PG Owner | Bottom Nav | SCR_009, SCR_007 | TenantEntity List Flow |
| **SCR_009** | **Tenant Details** | Displays individual profile details, KYC documents, and payment histories. | PG Owner | SCR_005, SCR_008 | SCR_008, SCR_010, SCR_012 | TenantEntity, RentPaymentEntity |
| **SCR_010** | **KYC Document Viewer**| Displays high-resolution views of uploaded tenant ID cards. | PG Owner | SCR_009 | SCR_009 | TenantEntity File References |
| **SCR_011** | **Rent Tracking Hub** | Tracks rent billing cycles and payment statuses across Paid, Pending, and Overdue tabs. | PG Owner | Bottom Nav, SCR_003 | SCR_012, SCR_009 | RentPaymentEntity List Flow |
| **SCR_012** | **Record Payment Sheet**| Logs collected rents, payment dates, and payment modes (Cash, UPI, etc.). | PG Owner | SCR_011, SCR_009 | SCR_011, SCR_009 | RentPaymentEntity Update |
| **SCR_013** | **Expense Registry** | Displays a chronological ledger of property costs, filterable by category. | PG Owner | Bottom Nav, SCR_003 | SCR_014 | ExpenseEntity List Flow |
| **SCR_014** | **Log Expense Sheet** | Records expenditure amounts, categories, transaction dates, and notes. | PG Owner | SCR_013 (FAB) | SCR_013 | ExpenseEntity Insertion |
| **SCR_015** | **Owner Profile** | Manages business settings, payment details, and security configurations. | PG Owner | Bottom Nav | SCR_016, SCR_002 | OwnerProfileEntity Flow |
| **SCR_016** | **Settings & Security** | Edits secure PIN configurations and database backup preferences. | PG Owner | SCR_015 | SCR_015 | OwnerProfileEntity Update |

---

## 3. Application Hierarchy

The diagram below illustrates the structural hierarchy of the PG Manager application, highlighting the persistent bottom navigation structure.

```
                         [ROOT APPLICATION]
                                 │
                         ┌───────┴───────┐
                  [SCR_001: Splash] [SCR_002: PIN Login]
                                 │
                        [PERSISTENT BOTTOM NAV]
        ┌──────────────┬─────────┴────────────┬──────────────┬──────────────┐
        │              │                      │              │              │
   [Dashboard]      [Rooms]                [Tenants]      [Rent Hub]    [Expenses]
   (SCR_003)       (SCR_004)              (SCR_008)       (SCR_011)     (SCR_013)
        │              │                      │              │              │
        │         ┌────┴────┐                 │              │              ├─────────┐
        │         │         │                 │              │              │         │
        │    [Add Room] [Room Details]        │              │         [Log Exp] [Exp Details]
        │    (SCR_006)   (SCR_005)            │              │         (SCR_014) (SCR_013_Detail)
        │                   │                 │              │
        │         ┌─────────┴────────┐        │              │
        │         │                  │        │              │
        │    [Add Tenant]   [Tenant Details]◄─┘              │
        │    (SCR_007)          (SCR_009)                    │
        │                           │                        │
        │                     ┌─────┴─────┐             ┌────┴────┐
        │                     │           │             │         │
        │                 [KYC View] [Rent History]  [Record Pay] [Receipts]
        │                 (SCR_010)   (SCR_009_Led)  (SCR_012)    (SCR_012_Rec)
        │
   [Owner Profile] (SCR_015) ──► [Settings & PIN] (SCR_016)
```

---

## 4. Navigation Architecture

To deliver a premium, intuitive mobile experience, PG Manager establishes clear, consistent navigation rules across the entire application.

```
+-----------------------------------------------------------------------------+
|                          NAVIGATION ACTION MATRICES                         |
+-----------------------------------------------------------------------------+
| Component      | Interaction Pattern | Layout Target | UX Rationale         |
+-----------------------------------------------------------------------------+
| Add Room       | Full Screen Page    | Primary Level | Complex form inputs  |
| Record Payment | Bottom Sheet (Slide)| Lower 60%     | Fast, focused updates|
| Delete Tenant  | Alert Dialog        | Center Screen | Intentional safety   |
| View KYC ID    | Full Screen Modal   | Primary Level | High-contrast viewing|
+-----------------------------------------------------------------------------+
```

### 4.1 Primary Layout Components
* **Bottom Navigation Bar:** Serving as the primary router, the Bottom Navigation bar is persistent across the five main screens (Dashboard, Rooms, Tenants, Rent, Expenses). Transitions between bottom navigation tabs are instant, without screen-slide animations, and reset the internal backstack of that specific tab.
* **Top App Bar:** Used on secondary screens to display a clean page title, a back button (aligned left), and relevant action icons (such as edit or delete options, aligned right). The Dashboard does not use a back button, instead displaying the property's brand name.
* **Floating Action Button (FAB):** Positioned in the bottom-right corner of relevant screens. The FAB changes context based on the active tab (e.g., adding a room, tenant, or expense) and features a fluid morphing animation on press.

### 4.2 Modal and Interaction Overlays
* **Bottom Sheets:** Used for quick data entry and updates (e.g., recording a rent payment or selecting a payment mode). The sheet slides up to cover the lower 60% of the screen, keeping actions within easy thumb reach.
* **Alert Dialogs:** Used exclusively for critical, destructive actions (e.g., deleting a room, checking out a tenant, or resetting data). Dialogs appear in the center of the screen with a semi-transparent background overlay, requiring explicit confirmation.

### 4.3 Screen-Transition Routing Rules
* **Push (Navigate forward):** Used when drill-down is required (e.g., Dashboard → Room Management → Room 101 Details). This adds the target screen to the navigation backstack and slides it in from the right edge.
* **Replace (Reset State):** Used when changing authentication states (e.g., entering the correct PIN replaces the Login screen with the Dashboard, clearing the backstack so pressing Back does not return the user to the Login screen).
* **Pop (Navigate backward):** Pressing the system back button or the top app bar back arrow pops the top screen off the stack, sliding it out to the right and returning the user to the previous screen.

---

## 5. Bottom Navigation Design

The persistent bottom navigation bar features five high-priority tabs, each mapped directly to the core workflows of Indian PG owners:

```
+-------------------------------------------------------------------------+
| [ Dashboard ]   [ Room Grid ]   [ Rent Ledger ]  [ Expense Log ]  [ More ] |
+-------------------------------------------------------------------------+
```

### 5.1 Tab Breakdown & Product Rationale
1. **Dashboard (SCR_003):** The central command center. This tab provides immediate, high-level business insights and quick access to primary actions, minimizing time-to-insight.
2. **Rooms (SCR_004):** Displays the property's physical room configurations and bed-occupancy statuses. This tab is essential for managing walk-in inquiries and optimizing overall room utilization.
3. **Rent (SCR_011):** Manages rent billing cycles, outstanding collections, and payment statuses. It is positioned directly in the middle of the navigation bar for fast, easy access on the go.
4. **Expenses (SCR_013):** Records daily operational expenditures as they occur, ensuring accurate financial records and preventing forgotten cash logs.
5. **Profile (SCR_015):** Houses property setup details, payment credentials (such as UPI IDs), and app preferences. Placing these secondary options in a dedicated tab keeps the primary interfaces clean.

---

## 6. Screen Relationships

The diagram below maps the navigation paths, backstack transitions, and routing rules across all screens in the application:

```
[SCR_001: Splash] 
   └── (Auto-Route / Correct DB Loaded) ────► [SCR_002: PIN Login]
                                                 └── (Replace Stack on Success) ──► [SCR_003: Dashboard]

[SCR_003: Dashboard]
   ├── [Bottom Nav Tab] ──────────────────► [SCR_004: Rooms]
   │                                           ├── [Tap Room Card] ──────────► [SCR_005: Room Details]
   │                                           │                                  ├── [Back Arrow] ──► [SCR_004: Rooms]
   │                                           │                                  └── [Tap Vacant Bed] ──► [SCR_007: Add Tenant]
   │                                           └── [Tap FAB (+)] ────────────► [SCR_006: Add Room]
   │
   ├── [Bottom Nav Tab] ──────────────────► [SCR_008: Tenants]
   │                                           ├── [Tap Tenant Card] ────────► [SCR_009: Tenant Details]
   │                                           │                                  ├── [Back Arrow] ──► [SCR_008: Tenants]
   │                                           │                                  ├── [Tap KYC Card] ──► [SCR_010: KYC View]
   │                                           │                                  └── [Tap Record Payment] ──► [SCR_012: Payment Sheet]
   │                                           └── [Tap FAB (+)] ────────────► [SCR_007: Add Tenant]
   │
   ├── [Bottom Nav Tab] ──────────────────► [SCR_011: Rent Hub]
   │                                           ├── [Tap Overdue/Pending Card] ► [SCR_012: Payment Sheet]
   │                                           └── [Tap Tenant Avatar] ──────► [SCR_009: Tenant Details]
   │
   ├── [Bottom Nav Tab] ──────────────────► [SCR_013: Expenses]
   │                                           └── [Tap FAB (+)] ────────────► [SCR_014: Log Expense Sheet]
   │
   └── [Bottom Nav Tab] ──────────────────► [SCR_015: Owner Profile]
                                               └── [Tap Settings Item] ──────► [SCR_016: Settings & PIN]
```

---

## 7. Complete User Flows

This section details how the navigation architecture supports and simplifies primary day-to-day business operations:

### 7.1 Morning Property Review & Rent Follow-up
```
[Dashboard (SCR_003)] ──(View Rent KPI)──► [Rent Hub (SCR_011)] ──(Select Overdue Tab)──► [Overdue List]
                                                                                               │
   ┌───────────────────────────────────────────────────────────────────────────────────────────┘
   ▼
[Select Tenant Card] ──(Tap Send Reminder)──► [Generate & Copy Reminder Template] ──► [Open WhatsApp to Share]
```

### 7.2 Walk-in Tenant Onboarding & Bed Allocation
```
[Rooms Tab (SCR_004)] ──(Select Floor)──► [Room Grid] ──(Tap Target Room)──► [Room Details (SCR_005)]
                                                                                      │
   ┌──────────────────────────────────────────────────────────────────────────────────┘
   ▼
[Tap "Vacant Bed" Slot] ──(Launch Form)──► [Add Tenant (SCR_007)] ──(Save & Allocate)──► [Room Details Updated]
```

### 7.3 Real-time Expense Logging
```
[Expenses Tab (SCR_013)] ──(Tap "+" FAB)──► [Log Expense Sheet (SCR_014)] ──(Select Plumbing Category)──┐
                                                                                                        │
   ┌────────────────────────────────────────────────────────────────────────────────────────────────────┘
   ▼
[Enter Numeric Amount] ──(Tap Save Button)──► [Instant DB Write] ──► [Expenses Tab updated / Margin recalculates]
```

---

## 8. Interaction Patterns

To deliver a premium, responsive feel, PG Manager establishes specific interaction guidelines for every task, balancing simplicity with data security.

```
                  +----------------------------------------------+
                  |         TACTILE INTERACTION PATTERNS         |
                  +----------------------------------------------+
                                         |
         +-------------------------------+-------------------------------+
         |                               |                               |
         v                               v                               v
+------------------+           +------------------+             +------------------+
| RECORD PAYMENT   |           | DELETE TRANSACT  |             | DATA MUTATION    |
| Bottom sheet,    |           | Alert Dialog,    |             | Success Snackbar |
| slide transition,|           | center focus,    |             | with instant     |
| thumb reach.     |           | manual confirm.  |             | "Undo" action.   |
+------------------+           +------------------+             +------------------+
```

### 8.1 Pattern Selection Matrix

* **Full-Screen Pages (Push/Pop):** Used for primary, multi-step actions and detail-rich screens (e.g., Room Details, Tenant Profiles, adding rooms or tenants). This gives the user ample visual space to complete detailed tasks.
* **Slide-Up Bottom Sheets:** Used for transient, single-step tasks that benefit from fast entry (e.g., logging payment details or picking expense categories). This pattern keeps actions focused and within easy thumb reach.
* **Alert Dialogs:** Reserved exclusively for destructive, irreversible actions (e.g., deleting a tenant's history or removing rooms). This pattern interrupts the user's flow to prevent accidental data loss.
* **Persistent Action FAB:** Placed in the bottom-right corner of primary directories. The FAB uses a responsive slide-out animation on scroll, expanding to show a text label (e.g., "+ Add Room") when static and collapsing into an icon on scroll to maximize readability.
* **Swipe-to-Action Gestures:** Swipe actions are reserved for secondary, non-destructive tasks. Swiping left on an expense card, for example, reveals a quick-delete option, while swiping right on a tenant card copies their contact information.
* **System Snackbars:** Used to provide immediate, non-intrusive feedback for background actions (e.g., confirming "Payment Recorded" or "Expense Saved"). Snackbars include a clear, single-tap "Undo" option that remains visible for 4 seconds.

---

## 9. Global Search Architecture

To minimize the effort required to look up records across different screens, PG Manager features one unified Global Search bar, accessible from the top of the main dashboard.

```
+---------------------------------------------------------------------------------+
|                         UNIFIED GLOBAL SEARCH ARCHITECTURE                      |
+---------------------------------------------------------------------------------+
| Search Query Types     | Database Scope Match       | Real-time Filter Targets  |
+---------------------------------------------------------------------------------+
| "204" (Numeric)        | Matches room numbers       | Room Detail (SCR_005)     |
| "Arjun" (Alphabetical) | Matches tenant profiles    | Tenant Details (SCR_009)  |
| "9876" (Numeric)       | Matches phone records      | Call/Message Trigger      |
| "Aadhaar" (Alpha-num)  | Matches KYC document types | KYC Verification Status   |
+---------------------------------------------------------------------------------+
```

### 9.1 Search Matching & Relevance Logic
The search engine processes queries in real-time, matching inputs across multiple databases:
* **Numeric Inputs (e.g., "102"):** Prioritizes room and bed matching, displaying occupancy details immediately.
* **Alphabetical Inputs (e.g., "Arjun"):** Prioritizes tenant registry matching, displaying the resident's name, linked room number, and phone number.
* **Contact Queries (e.g., "98765"):** Filters active and past tenants by phone number, emergency contacts, or email addresses.

### 9.2 Search UX States
* **Active Typing State:** Results update progressively as the user types, highlighting matching characters in bold text.
* **Empty/Recent Searches State:** Displays the user's three most recent search queries, alongside quick-tap filter chips (e.g., "Vacant Beds," "Overdue Payments") to speed up navigation.
* **No Results State:** Shows a clean, professional "No records found" illustration with a helpful suggestion (e.g., *"Try searching for a different room number or tenant name"*).

---

## 10. Filters

To keep lists clean and organized, every major business module features high-contrast, scrollable filter chips positioned immediately below the top app bar:

```
+-------------------------------------------------------------------------+
|  [All Rooms]   [ Occupied ]   [ Vacant (4) ]   [ Triple Share ]  [ + ]  |
+-------------------------------------------------------------------------+
```

### 10.1 Module-Specific Filter Configurations

#### 10.1.1 Room Management Filters
* **All Rooms (Default):** Displays the property's entire room and floor inventory.
* **Occupied:** Filters to show only rooms that have at least one occupied bed.
* **Vacant:** Displays rooms that have at least one empty bed, helping owners place walk-in inquiries quickly.
* **Full:** Shows rooms that are at capacity, assisting with overall property planning.

#### 10.1.2 Rent Tracking Hub Filters
* **Overdue:** Displays tenants whose rent payments are past their due date, serving as the primary follow-up list.
* **Pending:** Shows tenants whose rent is currently due but still within their grace period.
* **Paid:** Displays tenants who have successfully completed their payment cycles for the active billing month.

#### 10.1.3 Expense Registry Filters
* **Month Selector:** A simple drop-down filter to view expenses by calendar month (e.g., "July 2026," "June 2026").
* **Category Chips:** Filters the expense list by specific operational categories (e.g., Repairs, Utilities, Staff Salary, Food), helping owners understand where their money is going.

---

## 11. Screen States & Fallbacks

To ensure a highly professional and reliable experience, every screen is designed with specific visual states to handle network variations and initial configurations gracefully:

```
+---------------------------------------------------------------------------------+
|                           SCREEN STATE TRANSITION MATRIX                        |
+---------------------------------------------------------------------------------+
| Trigger Event           | Visual Transition Target | State Design Impact        |
+---------------------------------------------------------------------------------+
| App First-Time Launch   | Empty Onboarding Layout  | Dynamic step-by-step guides|
| Heavy Database Query    | Shimmer Skeleton Overlay  | Prevent layout jumping     |
| Loss of Signal/Offline  | Status Banner Indicator  | Reassure offline capability|
| System Exception/Error  | Friendly Recovery Sheet  | Single-tap retry button    |
+---------------------------------------------------------------------------------+
```

### 11.1 Shimmer Loading States (Skeleton Loaders)
To prevent jarring layout shifts during data loads, PG Manager uses cohesive, low-contrast Shimmer animations. Instead of showing generic progress spinners, screens display skeleton layouts that mimic the structure of the incoming data cards, creating a smoother perceived loading experience.

### 11.2 Professional Empty States
Empty states are treated as helpful onboarding opportunities.
* **No Rooms Configured:** Rather than a blank screen, the room directory displays a friendly illustration with a clear call-to-action button: *"Set up your first room to begin tracking occupancies."*
* **No Outstanding Rents:** Displays a reassuring green shield icon with the message: *"All caught up! No overdue payments for this month."*

### 11.3 Offline Status Banners
Because PG Manager is designed with a local-first architecture, the application remains fully functional without an internet connection. If the device goes offline, a subtle, non-intrusive indicator appears at the top of the screen (e.g., *"Offline Mode — Saved to Local Storage"*), assuring the owner that their data is safe and will sync automatically when connectivity is restored.

### 11.4 Graceful Error Fallbacks
If a system error occurs (e.g., a file-reading issue or database conflict), the app displays a friendly error screen rather than crashing. The screen explains the issue in plain language and provides a prominent "Retry" button to restore operations without losing progress.

---

## 12. Micro-Interactions & Visual Polish

Visual polish transforms a functional app into a premium experience. PG Manager uses deliberate, meaningful animations to establish a tactile sense of quality.

```
                  +----------------------------------------------+
                  |         TACTILE MOTION ARCHITECTURE          |
                  +----------------------------------------------+
                                         |
         +-------------------------------+-------------------------------+
         |                               |                               |
         v                               v                               v
+------------------+           +------------------+             +------------------+
| BOTTOM NAV SWITCH|           | DATA RECONCILE   |             | DESTRUCTIVE POP  |
| Zero-slide, text |           | Smooth numeric   |             | Scale-down and   |
| label fade-in,   |           | counter roll,    |             | fade-out overlay |
| tactile ripple.  |           | green indicator. |             | visual exit.     |
+------------------+           +------------------+             +------------------+
```

### 12.1 Tab Navigation Transitions
Transitions between bottom navigation tabs are immediate (without lateral sliding) to prevent visual fatigue during frequent navigation. Buttons use a subtle scale-up and fade-in animation on select, accompanied by a localized material ripple effect.

### 12.2 Primary Page Transitions (Forward & Backward)
* **Forward (Push):** New screens slide in smoothly from the right edge of the device while the parent screen dim-fades slightly, creating a clear sense of depth and progress.
* **Backward (Pop):** When the user goes back, the top screen slides out to the right, revealing the parent screen beneath and reinforcing a natural spatial mental model.

### 12.3 Data Mutation Animations
* **Card Expansions:** Tapping a room or tenant card expands it inline, revealing secondary details smoothly rather than launching a new page, keeping the user in context.
* **Numerical Reconciliations:** When a rent payment is recorded, the outstanding balance on the dashboard rolls down smoothly (like a digital counter) while the collected total increments, providing satisfying visual confirmation of the transaction.
* **Success Celebrations:** Recording a collection or adding a tenant triggers a brief, refined success animation (e.g., a subtle green ripple across the payment card), giving the owner immediate, positive feedback.

---

## 13. Scalability Strategy

To ensure PG Manager can scale from a simple MVP into a comprehensive SaaS platform, the application's information architecture includes dedicated expansion points for future modules:

```
+---------------------------------------------------------------------------------+
|                           FUTURE MODULE EXPANSION POINTS                        |
+---------------------------------------------------------------------------------+
| Future Capability  | Integration Anchor              | UX Impact Design         |
+---------------------------------------------------------------------------------+
| Multi-Property     | Dashboard Header Dropdown       | Quick portfolio switching|
| In-App QR Payments | Floating Payment Sheet          | Automated collection sync|
| Complaint Tickets  | Main Navigation Tab (Center)    | Direct tenant support    |
| AI Insights Engine | Performance Metrics Panel       | Predictive yield tracking|
| Tenant Portal Sync | Tenant Profile Verification Card| Real-time record sync    |
+---------------------------------------------------------------------------------+
```

### 13.1 Multi-Property Management Expansion
* **MVP Limitation:** Configured for a single property.
* **Scalability Anchor:** The dashboard header features an expandable dropdown arrow next to the property name (e.g., "Emerald Stays ▼"). In future phases, tapping this dropdown will display a list of properties, enabling instant switching across an owner's entire portfolio without changing the core app layout.

### 13.2 Automated In-App UPI & QR Payments
* **MVP Limitation:** Payments are logged manually by the owner.
* **Scalability Anchor:** The payment entry sheet is designed to support digital integrations. A dedicated "Generate QR" button will be added to the Record Payment Sheet, allowing owners to display a dynamically generated UPI QR code on their screen for tenants to scan and pay instantly.

### 13.3 Tenant Complaint Ticketing
* **MVP Limitation:** Excluded from the MVP.
* **Scalability Anchor:** The main Navigation Bar is structurally prepared to accommodate a central "Complaints" tab (replacing the primary "+ " FAB with a balanced 5-tab layout or integrating complaints directly into the Tenant Details screen). This ensures the core navigation remains clean and familiar.

### 13.4 AI-Driven Revenue & Expense Insights
* **MVP Limitation:** Financial calculations are strictly mathematical.
* **Scalability Anchor:** The Performance Summary on the Dashboard includes a placeholder section for intelligent insights. In future versions, this section will host proactive AI tips, such as: *"Insight: Food expenses are up 12% this month. Consider checking vendor rates."* or *"Insight: Room 201 has had a 15% higher vacancy rate than average this quarter."*

### 13.5 Direct Tenant Portal Synchronization
* **MVP Limitation:** Records are managed entirely by the owner.
* **Scalability Anchor:** Tenant detail sheets include a "Link Account" indicator. Once the Tenant App is launched, owners can tap this option to send an invite link. Once accepted, the tenant can upload their KYC documents and log payments directly, updating the owner's ledger automatically.

---

## 14. Final Navigation Blueprint

This comprehensive navigation blueprint serves as the official structural guide for product development, design wireframing, and quality assurance testing:

```
                                  [START: APP ICON]
                                          │
                                   [SCR_001: Splash]
                                          │
                                 [SCR_002: PIN Login]
                                          │
                  ┌───────────────────────┴───────────────────────┐
                  ▼                                               ▼
         (Incorrect PIN Entry)                            (Success PIN Entry)
         [Show Shake & Error]                             [SCR_003: Dashboard]
                                                                  │
   ┌───────────────────────┬───────────────────────┬──────────────┴────────┬───────────────────────┐
   ▼                       ▼                       ▼                       ▼                       ▼
[SCR_004: Rooms]       [SCR_008: Tenants]      [SCR_011: Rent Hub]     [SCR_013: Expenses]     [SCR_015: Profile]
   │                       │                       │                       │                       │
   ├── [Tap FAB (+)]       ├── [Tap FAB (+)]       ├── [Select Tab]        ├── [Tap FAB (+)]       └── [Tap Settings]
   │    └── SCR_006        │    └── SCR_007        │    ├── Paid           │    └── SCR_014            └── SCR_016
   │         (Add Room)    │         (Add Tenant)  │    ├── Pending        │         (Log Expense)          (PIN Set)
   │                       │                       │    └── Overdue        │
   └── [Tap Room Card]     └── [Tap Tenant Card]   │                       └── [Swipe Left Card]
        └── SCR_005             └── SCR_009        └── [Tap "Record Pay"]       └── Delete Dialog
             (Room Detail)           (Tenant Spec)      └── SCR_012                  (Center Alert)
                  │                       │                  (Payment Sheet)
                  ├── [Tap Bed Vacant]    ├── [Tap KYC Card]
                  │    └── SCR_007        │    └── SCR_010
                  │         (Add Tenant)  │         (KYC Viewer)
                  │                       │
                  └── [Tap Tenant Av]     └── [Tap Rent List]
                       └── SCR_009             └── Rent Ledgers
                            (Tenant Spec)           (Tenant Sub-tab)
```

---

## 15. Operational Readiness Checklist

To transition this blueprint into immediate wireframing and software planning, the design and engineering teams must complete the following next steps:

1. **UX Design Action Items:**
   * Wireframe the **Dashboard (SCR_003)** to place high-level KPIs at the top and key actions within easy reach of the thumb.
   * Design the **Room Grid (SCR_004)** to show floors and room/bed statuses clearly, using intuitive color coding.
   * Prototype the slide-up **Record Payment Sheet (SCR_012)** to ensure it is easy to use and provides clear visual feedback on completion.
2. **Technical Architecture Action Items:**
   * Verify that the Room Database schemas in **Database.kt** map correctly to the relationships defined in Section 6.
   * Implement **PgViewModel.kt** using standard Jetpack Compose navigation, using type-safe route keys to prevent runtime navigation issues.
   * Ensure the application's navigation backstack is cleared correctly during login, logout, and tab transitions to prevent memory leaks and keep the back key behaving predictably.
