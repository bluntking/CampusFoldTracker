# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [2.1.0] - 2026-10-24

### Added
- **5-Slot Floating Navigation**: Refactored `FloatingPillBottomBar` to exactly five slots: Home, Analytics, Center Add (+), Splits, and Settings.
- **Pushed Screens Architecture**: Added dedicated pushed `TransactionsScreen` (with search, category/vendor drill-down, date grouping, bulk actions) and full `AccountsScreen`; pushed screens keep the bar visible with Home highlighted and respect navigation-bar bottom padding.
- **Full-Height Add Transaction Sheet**: Replaced the legacy dialog with a full-height sheet supporting Expense ("I paid" vs "A friend paid"), Income, CC Bill Payment, and Transfer with real account selections, backdating date/time pickers, and inline friend creation.
- **Friend-Paid Expenses & True Spend**: Expenses paid by a friend now record zero account debit, set `transactions.paidByPersonId`, count toward personal true spend, and create `OWED_BY_ME` debts in Room DB without inflating gross spending.
- **Bidirectional Splits & Repayments**: Top debt summary ("You owe ₹X" and "Owed to you ₹Y"), net balances per friend, expandable bills in both directions, "They paid me" (FIFO settlement), "I paid them" (account debit + FIFO repayment with excess-to-expense protection), and outgoing payment match suggestions.
- **Anchor-Based Net Available Balance Engine**: Derived account balance calculation (`anchor + credits - debits` for savings, `anchorOwed + debits - credits` for cards); Net Available Balance is strictly `savings - credit card owed`; CC bill transfers and internal transfers leave net unchanged; added "Auto-use bank-reported balances" setting and discrepancy hints.
- **Rebuilt Home Screen**: LazyColumn featuring 7 structured sections in order: Header (with non-interactive background sync indicator and AI Coach button), slim Needs-Attention strip, Net Balance Card, Total Spends tile (Month/Week toggle, period picker, True Spend gauge, Gross/Split breakdown), Recent Transactions tile (with "See All >"), My Accounts tile, and 14-week Activity Heatmap.
- **Edge-to-Edge Theme Integration**: Eliminated the white status bar bug via `enableEdgeToEdge()` in `MainActivity.kt` with app-theme aware `SystemBarStyle`, dark NoActionBar window background in `themes.xml`, and clean removal of deprecated window color assignments in `Theme.kt`.
- **Dedicated Settings Hub**: Replaced the overflowing 5-tab strip with clean navigation into pushed sub-screens: Sync & Data, Backup & Restore, Organize (Categories & Vendors, Review Queue with badge, Automation Rules), Accounts, AI Diagnostics, Appearance & Profile (Dark/Light/AMOLED, Dynamic Color, Display Name), and a guarded Danger Zone with typed "DELETE" confirmation for "Clear Transactions Only" vs "Reset Everything".
- **Enhanced Sync Engine**: Replaced rejected `LIMIT`/`OFFSET` SMS pagination with keyset cursor pagination; Gmail auto-sync on open with 60s debounce, 100-msg batch ingestion with post-commit UID checkpointing, FetchProfile prefetching, RBI domain filtering (`.bank.in`, `.fin.in`), and editable custom bank senders.
- **Database Migration 4 -> 5**: Automated migration adding anchor fields to accounts, `direction` to `split_items` and `pending_settlement_suggestions`, and `paidByPersonId` to `transactions`.
- **Vendor Auto-Persistence**: Fixed empty categories/vendors root cause by auto-persisting extracted vendors directly to Room DB on resolution.
- **Automated Unit Tests**: Comprehensive test suite covering Migration 4->5, Net Balance Engine, Friend-Paid & FIFO Repayment logic, Bank Email Filters, and Reset Data operations.

### Important Note Before Updating
- Export a full JSON backup from your settings before installing this update to preserve your financial data.
- Existing database balances and transactions automatically migrate to exact integer paise to permanently eliminate floating-point rounding errors.
- Database schema safely migrates from version 3 to version 4 with automatic data preservation.
- Legacy AI model download paths have been reset and quarantined to ensure safe compatibility with the new isolated engine.

### Added
- Isolated background AI engine running on a dedicated process so neural operations never crash the main application.
- Modern on-device AI options supporting Qwen 2.5 1.5B and Gemma 3 1B models via Google LiteRT-LM.
- Automatic account and vendor discovery during SMS inbox scanning and Gmail sync.
- Multi-bank parsing support covering dozens of Indian and international financial institutions powered by parser-core.
- Flexible transaction rules engine supporting custom conditions, learned vendor mappings, and automatic categorization.
- Smart duplicate-account auto-merge engine with confidence scoring, suggested duplicate resolution, and full one-tap undo.
- Comprehensive manual controls for transactions including bulk deletion, undo restore, recategorization, account reassignment, and transaction merging.
- Sticky user edits ensuring your manual overrides for categories, vendors, and accounts are never overwritten by automated parsers.
- Refreshed Home tab featuring live account balance cards, quick metric filters, and an interactive daily spending gauge.
- Step-by-step onboarding walkthrough to help you set up permissions, connect accounts, and configure initial preferences.
- Direct secure Gmail IMAP synchronization over SSL with folder and date-range selection.

### Changed
- Converted all monetary calculations and database storage from floating-point numbers to exact integer paise.
- Modernized user interface with a custom Fold Money aesthetic, true OLED dark backgrounds, and neon accent colors.
- Replaced traditional bottom navigation with a smooth floating pill bar for quick switching between core sections.
- Upgraded the AI chat coach to inject live account balances, top expense categories, and debt figures into conversational context.

### Removed
- Removed the legacy Gmail Notification Listener service in favor of direct, privacy-preserving Gmail IMAP sync.
- Removed hardcoded vendor tables in favor of dynamic in-memory and rule-based vendor matching.
- Removed legacy MediaPipe GenAI implementation to eliminate native memory leaks and cold-start freezes.

### Fixed
- Fixed SQLite database thread deadlocks by migrating all internal database calls to Room coroutine transactions.
- Eliminated quadratic N+1 query bottlenecks during SMS batch ingestion and duplicate account evaluation.
- Resolved Jetpack Compose recomposition lag by migrating all screen state collectors to lifecycle-aware observers.
- Fixed main-thread audio and UI hiccups by moving heavy chart aggregations and date filtering to background worker threads.
- Prevented SMS receiver timeout crashes by acquiring a partial wake lock and enforcing execution timeouts.

### Security
- Gmail app passwords are now securely encrypted at rest using the hardware-backed Android Keystore.
- All SMS and email parsing executes strictly on your physical device with zero remote server data transmission.
