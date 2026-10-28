# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [2.0.2] - 2026-10-28

### Fixed & Enhanced
- **Gmail IMAP Sync & UID-Checkpoint Healing (TODO #7)**:
  - **Prevented `[Gmail]/All Mail` Poisoning**: Tracked `activeFolderIsInbox` so fallback UID lookups in `[Gmail]/All Mail` are never saved under `INBOX`'s UID checkpoint (`serverUidValidity`), eliminating silent sync freeze.
  - **Auto-Healing for Poisoned Checkpoints**: Automatically heals existing poisoned checkpoints on first run if `effectiveLastUid >= inboxUidNext`, resetting to start so poisoned folders resume syncing instantly.
  - **"Sync from date" Reliability**: Explicit start date syncs bypass UID checkpoint filtering and update the checkpoint via `maxOf(storedLastUid, chunkHighestUid)` so checkpoints never move backward.
  - **Unified Manual & Background Sync**: Routed manual "Sync Now" / "Sync from date" (`REPLACE`) and app-open auto-sync (`KEEP`) through `GmailScanWorker` with `NetworkType.CONNECTED` constraint and exponential backoff.
  - **Real-Time Step Diagnostics**: Live sync progress (`"Connecting..."`, `"Downloading N of M..."`, `"Saving..."`) streamed directly from WorkManager; recorded both "Last attempt" (with plain-English errors) and "Last successful sync" in Settings.
  - **Cold-Start Auto-Sync Race Fix**: Directly collected credentials and settings from DataStore, eliminating false early-exit. Fixed chip border preset typo in `GmailSyncDialog`.
- **Ignore Future-Debit Mandate & ASBA Notices (TODO #1 & TODO #3)**:
  - **Pre-Check Filter**: Automatically ignored future-debit mandate reminders (`will be debited`, `upcoming mandate set for`) before money movement logic can misclassify them as completed debits.
  - **IPO Application Notices Ignored**: Prevented ASBA / IPO UPI collect requests and fund blocking notices (e.g. NSEIL) from being falsely categorized as incoming `INCOME`.
- **Real DB Columns for Reimbursements & Normal Row Preservation (TODO #9 & TODO #11)**:
  - **Room Database Migration (`v5 -> v6`)**: Added `linkedExpenseId`, `linkedIncomeId`, `reimbursedOffsetPaise`, and `previousSharePaise` to `TransactionEntity`. Converted legacy bracketed tags and restored `SPLIT_SETTLEMENT` rows back to normal `TransactionType.INCOME` with original vendor names preserved.
  - **Uncorrupted Income & Spend**: Reimbursed income rows remain normal `INCOME` rows and are excluded from Total Income in `HomeSpendingTile`; personal share reductions are stored directly in `reimbursedOffsetPaise`.
  - **Split Ledger Accounting**: Filtered unsettled friend splits strictly by `direction = 'OWED_TO_ME'`, fixed net pending balance to signed accounting, preserved prior split item settlement states upon bill edits, and sanitized account references on debt repayments.
  - **Accurate Split Owed Metric**: Spending tile "Split Owed" now uses `netOverview.totalFriendsOweMePaise` directly rather than flawed gross minus true spend arithmetic.
- **`INVESTMENT` Transaction Type (TODO #3)**:
  - Full first-class support for `TransactionType.INVESTMENT` across database, parsers (`parser-core`, `MessageExtractor`, `CampusSmsParser`), and UI (badges, feed card with trending icon and purple styling, type selector chips, filter chips, and notifications). Excluded from budget alerts and monthly personal spend calculations.
- **Account Filtering & Tap-to-Filter (TODO #6)**:
  - Tap any account card on the Dashboard or Accounts screen to open `TransactionsScreen` pre-filtered to that account.
  - Added removable filter chip in `TransactionsScreen` with account display name.
- **Automatic Vendor Rule Backfill (TODO #2)**:
  - Adding, editing, renaming, or re-categorizing vendor mappings now backfills matching past unmapped/default transactions retroactively across the database.
- **Notification Privacy (TODO #12)**:
  - Stripped raw SMS bodies and payment modes from notifications; notifications now show clean, private `"$vendor · $category"` with amount and title.
- **UI & Layout Polish (TODO #8, #4, #10)**:
  - **Home Recent Transactions (TODO #8)**: Displays exactly the latest 5 transactions instead of 8.
  - **Rules & Budgets Screen (TODO #4)**: Added `.statusBarsPadding()`, fixed header title clipping and single-line "Sync & Data" button, lifted FAB above the bottom pill bar with navigation padding, and replaced truncated category chips with a horizontally scrollable picker.
  - **Map Reimbursement Dialog (TODO #10)**: Expanded candidate list container with breathing room and fixed bottom-clipping on the offset currency input field.
  - **Theme Switching & AI Coach**: Fixed case sensitivity in dark/light theme switching, and wired `totalCreditCardAvailable` into AI Coach sheet.
- **Onboarding AI Model Picker & Real Scan Progress (TODO #5)**:
  - Dynamic AI model picker during onboarding with choice of Qwen 2.5 1.5B or ultra-lightweight Qwen 2.5 0.5B (~547 MB), device RAM checks, "Recommended" badge, dynamic download button text, and prominent "Skip for now" option.
  - Replaced simulated fake delays with real-time WorkManager inbox scanning observation and progress reporting.

## [2.0.1] - 2026-10-24

### Fixed & Enhanced
- **Axis Bank Credit Email Direction & Type Synchronization**:
  - **Deterministic Primary Signal Parsing**: Primary direction is now strictly resolved in order of priority: Labelled field (`Amount Credited:` / `Amount Debited:`), then subject sentence (`was credited` / `was debited`), then body sentence. Boilerplate disclaimers and footers (e.g., dispute links with "was debited") are explicitly stripped before sentence evaluation.
  - **Single Source of Truth**: Both `Direction` and `TransactionType` are derived from the single primary signal, guaranteeing they can never disagree.
  - **Genuine Conflict Routing**: If labelled fields and subject sentences genuinely contradict each other, the message is safely routed to the Review Queue (`ExtractionResult.Unparsed`) rather than guessing.
  - **Deduplication Type Correction in MessageIngestor**: Re-syncing (such as "Sync from date" for 21 Oct 2026 on Axis ••2003) now corrects previously misclassified transactions whose direction was inverted, preserving user manual edits while re-aligning vendor names, share values, and triggering income settlements.
  - **Condition C Account Ownership Guard in checkOwnTransfer**: Opposite transactions with the same amount within a 10-minute window are now converted to `OWN_TRANSFER` ONLY if they belong to two distinct accounts owned by the user. A friend reimbursing an expense on the same account is preserved as `INCOME`.
- **Restored "Map to an Expense" (Reimbursement) & Duplicate Merging**:
  - **Callback Wire-Up**: Wired `onLinkIncomeToExpense` and `onMergeWithDuplicate` from `TransactionViewModel` through the outer `TransactionDetailBottomSheet` wrapper.
  - **Type-Safety Constraint**: Marked both callbacks non-nullable in the inner composable signature to prevent them from silently disappearing.
  - **Bidirectional Linking & Editable Offsets**: Supported mapping income to an expense and offsetting an expense with incoming transfers, with an editable offset amount, updating user shares and split balances.
  - **Unlink Option**: Added an "Unlink Reimbursement" action on linked rows that restores original expense shares, split item settlement states, and returns the income leg back to standard `TransactionType.INCOME`.
  - **Account Balance Accuracy**: Reimbursed income credited to bank accounts is accounted for in savings account derived balances, keeping Net Available Balance exact.

## [2.0.0] - 2026-10-24

### Fixed & Enhanced (v2.0.0 Release)
- **Gmail IMAP Sync Restoration**:
  - **OTP False-Positive Drop Resolved**: Emails with completed money movement (`debited`, `credited`, `spent`) are no longer falsely classified as OTPs due to security footer disclaimers ("never share OTP/password").
  - **Subject Field Propagation**: Added `subject` to `RawMessage` model and propagated email subject to `AxisBankEmailParser`.
  - **Axis Bank Alert Support**: Full parser support for Axis Bank debit and credit notifications (`alerts@axis.bank.in`, `UPI/P2M/<RRN>/<Merchant>`, `UPI/P2A/<RRN>/<Merchant>/<Bank>/Sent`), matching existing accounts without duplicates.
  - **Post-Commit UID Checkpoint Safety**: Checkpoint advances only for emails that are safely ingested, updated, or stored in the Review Queue.
  - **60-Second Sync Debounce**: Failed or empty sync runs no longer lock out subsequent sync attempts; auto-sync debounce set to 60 seconds.
  - **Keystore Decryption Failure Detection**: Detects if an Android Keystore key changed after an app update/reinstall and prevents sending raw ciphertext to IMAP, displaying a clear prompt in Settings > Sync & Data to re-enter the App Password.
  - **"Last sync" Panel in Settings**: Added real-time sync metrics panel under Settings > Sync & Data showing sync time, emails fetched, filtered out, parsed, sent to Review Queue, saved, and the last error in plain words.
- **UI Bug Fixes**:
  - **Manage Accounts Header**: Balanced title with flexible weight so "Manage Accounts" never collides with the Merge button; enlarged `+` button with a 20dp icon.
  - **Account Card Subtitles**: Removed duplicate "Axis Bank ••2003" subtitles, replacing them with clean account type labels ("Savings Account", "Credit Card").
  - **Credit Card Balance Squeezing**: Placed credit card balance and owed amounts on their own dedicated row, giving full card width to long names ("Punjab National Bank ••8598") so they are never truncated against "Owed: ₹0".
  - **Source Tag Accuracy**: Corrected "entered manually" tag condition to strictly require `anchorSet && balanceSource == MANUAL`.
  - **Theme Background Consistency**: Set `#0A0A0C` background across `themes.xml`, `Theme.kt`, and all screen containers in OLED dark mode.
  - **Splits Header & Clearer Icons**: Balanced "Friend Balances & Debts" header to prevent touching action buttons; replaced shopping cart icon with receipt icon (`Icons.Default.ReceiptLong`, 18dp) on "+ Friend Paid"; enlarged Add Friend icon (`Icons.Default.PersonAdd`, 20dp).
  - **Readable ₹0.00 Summary Values**: Made ₹0.00 values in Splits summary card readable using high-contrast text color rather than dim muted grey.
  - **Splits Content Layout**: Constrained empty state Box and LazyColumn with `weight(1f)` bounds to eliminate spurious scrollbar thumbs.
  - **Mathematically Centered Bottom Bar**: Flanked the center `+` button between two equal-weighted 50% sub-rows in `FloatingPillBottomBar`, ensuring the `+` button remains dead-centered on every screen regardless of which tab is expanded.

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
