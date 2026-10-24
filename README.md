# CampusFoldTracker 💸

[![Direct APK Download](https://img.shields.io/badge/Direct%20APK-Download%20(No%20ZIP)-00E5FF.svg?logo=android)](https://github.com/bluntking/CampusFoldTracker/releases)
[![Build Android APK](https://github.com/bluntking/CampusFoldTracker/actions/workflows/build-apk.yml/badge.svg)](https://github.com/bluntking/CampusFoldTracker/actions/workflows/build-apk.yml)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-black.svg?style=flat&logo=android)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.21-7F52FF.svg?style=flat&logo=kotlin)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202024.06.00-4285F4.svg?style=flat&logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Room](https://img.shields.io/badge/Room%20DB-2.8.4-orange.svg?style=flat)](https://developer.android.com/training/data-storage/room)
[![LiteRT-LM](https://img.shields.io/badge/LiteRT--LM-0.16.1-00C49F.svg?style=flat&logo=google)](https://ai.google.dev)

> ### 📱 [Download Latest APK from GitHub Releases](https://github.com/bluntking/CampusFoldTracker/releases)
> Direct Android `.apk` downloads (no `.zip` extraction required). Each release is published with structured release notes and installs in-place over your existing installation without data loss.

---

**CampusFoldTracker** (launcher display: **CampusFold**) is a privacy-first, on-device Android expense tracker, multi-account financial ledger, bill-splitting manager, and on-device AI financial coach built natively with Kotlin and Jetpack Compose. Styled in an OLED dark aesthetic (`#0A0A0C` background, `#16161A` matte charcoal cards, and neon cyan, emerald, and purple highlights), it is purpose-engineered for clean, lag-free financial tracking.

---

## 🚀 Core Features

### 1. 📭 Automatic Discovery from SMS & Gmail IMAP
- **Automatic Account & Transaction Detection:** Discovers accounts, debit/credit transactions, merchant vendors, and categories from incoming financial SMS alerts and manual or scheduled Gmail IMAP synchronizations.
- **Exact Paise Integer Arithmetic:** All monetary amounts, balances, and shared splits are calculated and stored internally as exact 64-bit integer paise (`amountPaise`, `balancePaise`, `myActualSharePaise`), eliminating floating-point rounding errors and balance drift.
- **Credit Card Bill Transfer Recognition:** Payments towards credit card accounts are classified as internal transfers (`CC_BILL_TRANSFER`), avoiding artificial inflation of income or expense totals.

### 2. 🏦 Multi-Bank Parsing Engine (`:parser-core`)
- **Extensive Bank Coverage:** Integrates a modular `:parser-core` engine with regex and heuristics inspired by PennyWise AI alongside custom parsers.
- **Review Queue & Unparsed Messages:** Any transaction SMS or email that fails exact pattern matching is routed to the **Review Queue** or **Unparsed Messages** screen for manual inspection, AI analysis, or 1-tap rule creation.

### 3. 🤖 On-Device AI Fallback (Isolated Process `:ai`)
- **Isolated Process Architecture:** Google LiteRT-LM (`0.16.1`) executes inside a separate Android process (`:ai`). If heavy model inference runs out of memory or hits an unmanaged native fault, only the background worker restarts; the main user interface remains responsive and crash-free.
- **Unstructured Message Parsing:** Extracts structured `{amount, type, merchant, bank, accountLast4}` entities from irregular or non-templated financial notifications.
- **Context-Aware AI Coach:** An interactive chat assistant powered by on-device models (e.g. Qwen 2.5 1.5B, Gemma 3 1B) injected with live on-device context (monthly true spend, top categories, friend debt totals, and account balances).
- **Graceful Fallback:** If an AI model is not installed or enabled, the app continues normal operation using standard deterministic parsers and database analytics.

### 4. ⚙️ Rules Engine & Learned Associations
- **Custom Transaction Rules:** Create declarative rules with flexible conditions (`CONTAINS`, `EQUALS`, `STARTS_WITH`, `REGEX`) across transaction fields (`RAW_MERCHANT`, `SMS_BODY`, `ACCOUNT`, `AMOUNT`) to automatically reassign categories, rewrite vendor names, or block noise transactions.
- **Learned Rules:** System learns category associations as you manually categorize transactions, automatically applying them to future transactions from the same vendor.

### 5. 🔀 Duplicate Account Auto-Merge & Manual Merging
- **Confidence Scoring:** Automatically identifies duplicate financial accounts (e.g., matching bank identifier and last 4 digits). High-confidence matches are eligible for automatic merging, while ambiguous pairs are surfaced as suggested merges.
- **Full Undo Support:** Every merge operation records snapshots and moved transaction IDs in Room DB. You can review merge history and reverse any merge with 1 tap.
- **User Edit Protection:** Accounts with user-customized display names or account types are protected against automated merges.
- **Manual Account Merge:** Merge any two accounts on demand with custom field selections (surviving name, balance, and account type).

### 6. 🛠️ Comprehensive Manual Control & Sticky Edits
- **Batch Transaction Actions:** Bulk delete transactions, bulk recategorize, or bulk move transactions to another account.
- **Sticky Edits (`editedFields`):** Tracks manual overrides for categories, vendors, accounts, or descriptions to ensure automated ingestion passes never overwrite user choices.
- **Undo Delete Stash:** Deleting transactions offers an immediate undo snackbar to restore records without data loss.
- **Transaction Merging:** Merge duplicate charges (e.g., SMS and email alerts for the same purchase) into a single unified record.

### 7. 🏠 Rebuilt Home & Net Available Balance Engine
- **7-Section Dashboard:** Features a clean LazyColumn layout with Header (sync status & AI Coach launcher), slim Needs-Attention strip, Net Available Balance Card, Total Spends tile (Month/Week toggle, True Spend gauge, Gross/Split breakdown), Recent Transactions (with "See All >"), My Accounts tile, and 14-week Activity Heatmap.
- **Anchor-Based Balance Engine:** Accounts use immutable anchor timestamps (`openingBalancePaise`, `openingOwedPaise`, `openingAsOf`). Derived balances dynamically calculate from anchors (`anchor + credits - debits` for savings, `anchorOwed + debits - credits` for cards).
- **True Net Available Balance:** Headline displays strictly `savings balances - credit card owed`. Credit card bill transfers (`CC_BILL_TRANSFER`) and internal transfers between own accounts leave net available balance completely unchanged. Available credit is never inflated into the net balance headline.
- **Discrepancy Alerts:** Highlights discrepancies between derived balance and bank-reported message balance, with a 1-tap "Use bank balance" anchor update or an optional "Auto-use bank-reported balances" setting.

### 8. 👥 Bidirectional Splits, Friend-Paid Expenses & Repayments
- **Bidirectional Debt Summary:** Top summary card displays both "You owe ₹X" and "Owed to you ₹Y" alongside the net balance per friend.
- **Friend-Paid Expenses:** Record an expense a friend paid on your behalf directly from the Add Transaction sheet. Debits zero accounts, counts your share toward True Spend, and records an `OWED_BY_ME` debt in Room DB without inflating gross spending.
- **FIFO Settlement Engine:** "They paid me" settles incoming friend debts oldest first. "I paid them" debits your selected bank account, settles `OWED_BY_ME` debts FIFO, and records any excess overpayment as a normal expense on that account.
- **Smart Repayment Suggestions:** Outgoing bank payments matching a friend's SMS/UPI alias trigger 1-tap settlement suggestions directly on the Splits screen.

### 9. 🧭 Bottom Navigation & Pushed Screens
- **5-Slot Floating Bar:** Pill-shaped bottom navigation featuring Home, Analytics, Center Add (+), Splits, and Settings.
- **Pushed Screens:** Dedicated `TransactionsScreen` (with search, category/vendor drill-down, date grouping, bulk actions) and full `AccountsScreen` with system-back handling. Pushed screens keep the floating pill bar visible with Home highlighted.
- **True Edge-to-Edge:** Replaced deprecated window color assignments with native `enableEdgeToEdge()` responsive to app theme (Dark, Light, AMOLED) and dark window background, eliminating the white status bar glitch.

### 10. ⚙️ Dedicated Settings Hub & Enhanced Sync
- **Sub-Screen Navigation:** Modular settings hub with pushed screens for Sync & Data, Backup & Restore, Organize (Categories & Vendors, Review Queue, Rules), Accounts, AI Diagnostics, Appearance & Profile, and a guarded Danger Zone.
- **Sync & Data Engine:** Keyset cursor pagination for SMS avoids Android SQLite limit rejection; Gmail sync on open with 60s debounce, 100-msg batch ingestion with post-commit UID checkpointing, FetchProfile prefetching, RBI domain filtering (`.bank.in`, `.fin.in`), and editable custom bank senders.
- **Guarded Danger Zone:** Clear separation between "Clear Transactions Only" (reverts accounts to opening anchors, preserves metadata) and "Reset Everything" (factory defaults), both requiring typed "DELETE" confirmation.

### 9. 📊 Budgets & Intelligent Insights
- **Monthly Targets:** Set overall and per-category monthly spending limits with color-coded progress indicators.
- **Burn Rate Warnings:** Flags categories where spending pace outpaces elapsed days in the billing cycle.
- **High-Frequency Leaks:** Highlights recurring micro-transactions across the week.
- **Week-over-Week Spikes:** Alerts when spending in a category jumps significantly compared to the prior week.

### 10. 💾 Local Backup & Restore
- **Streaming JSON Export & Import:** Export all transactions, accounts, categories, rules, and friends to an open JSON file via Android Storage Access Framework (SAF).
- **Memory-Efficient Streaming:** Streams data via buffered chunk writing to prevent memory spikes on large databases.

---

## 📲 Setup Guide

### Step 1: Install the APK
1. Download the latest `CampusFold-1.0.XX.apk` from [GitHub Releases](https://github.com/bluntking/CampusFoldTracker/releases).
2. Open the downloaded APK on your Android device to install or update in-place.

### Step 2: Permissions
During the onboarding flow, CampusFold requests the following permissions:
- **`READ_SMS` & `RECEIVE_SMS`:** Required to import past financial messages and capture new transaction SMS alerts in real time.
- **`POST_NOTIFICATIONS`:** (Android 13+) Used to deliver optional budget limit warnings and transaction confirmations.
- **`INTERNET`:** Used exclusively for secure Gmail IMAP synchronization over SSL and optional AI model downloads.
- **`WAKE_LOCK`:** Ensures the background SMS receiver safely finishes database ingestion without being killed by device sleep modes.

### Step 3: Connect Gmail (Optional)
To automatically import transaction receipts and bank credit emails:
1. Enable **2-Step Verification** on your Google Account.
2. Go to **Google Account Settings > Security > App Passwords** and generate a 16-character App Password.
3. In CampusFold, enter your Gmail address and generated App Password.
4. Passwords are encrypted at rest using the hardware-backed **Android Keystore**.

### Step 4: Configure On-Device AI (Optional)
1. In the AI Coach or Onboarding screen, review recommended models (e.g. Qwen 2.5 1.5B or Gemma 3 1B).
2. Download an optimized model directly within the app, or import an existing `.bin` / `.task` file using Android Storage Access Framework.
3. You can skip this step at any time; all core parsing, rules, budgets, and splitting features work completely without an AI model.

### Step 5: Run Your First Scan
1. In settings or onboarding, choose your scan window (1, 3, 6 months, or All).
2. Tap **Scan SMS Inbox** or **Sync Gmail** to automatically discover accounts, vendors, categories, and historical transactions.

---

## 🔒 Privacy & Data Policy

- **On-Device Storage:** All financial records, transactions, account numbers, balances, split items, and custom rules are stored exclusively on your device in a local SQLite Room database.
- **No Analytics or Remote Tracking:** There are no telemetrySDKs, analytics trackers, advertising IDs, or external reporting servers embedded in the application.
- **Network Boundaries:** Network access is strictly restricted to two explicit user-initiated operations:
  1. Direct IMAP SSL connection to `imap.gmail.com:993` when you configure and run Gmail synchronization.
  2. Downloading quantized AI model weights when you choose to download an on-device model.
- Financial data is never transmitted to any third-party cloud service or external API.

---

## 🏛️ Supported Banks

CampusFold provides broad financial transaction coverage through the integrated `:parser-core` engine (supporting institutions such as Axis Bank, HDFC Bank, ICICI Bank, State Bank of India, Punjab National Bank, Bank of Baroda, Kotak Mahindra, Federal Bank, Canara Bank, Union Bank, Jupiter, Slice, Cred, and many others, along with international bank alert formats).

If an incoming SMS or email alert is not recognized by existing patterns, it is automatically saved to the **Review Queue** or **Unparsed Messages** list, allowing you to review it, parse it via Local AI, or define a rule with 1 tap.

---

## 🏗️ Tech Stack & Dependencies

- **Language:** Kotlin 2.3.21
- **Android Gradle Plugin (AGP):** 9.2.1
- **KSP:** 2.3.9
- **Target SDK:** 34 | **Compile SDK:** 35 | **Min SDK:** 26 (Android 8.0+)
- **UI Framework:** Jetpack Compose (BOM `2024.06.00`), Material 3, Material Icons Extended
- **Architecture & Lifecycle:** AndroidX Lifecycle 2.8.4 (`lifecycle-runtime-compose`, `lifecycle-viewmodel-compose`)
- **Database:** AndroidX Room 2.8.4 (`room-runtime`, `room-ktx`, `room-compiler`) with KSP schema exports
- **Concurrency:** Kotlinx Coroutines 1.11.0 (`kotlinx-coroutines-core`, `kotlinx-coroutines-android`)
- **Preferences:** AndroidX DataStore Preferences 1.1.1
- **Local AI Engine:** Google LiteRT-LM Android 0.16.1 (`litertlm-android`)
- **Background Tasks:** AndroidX WorkManager 2.9.1 (`work-runtime-ktx`)
- **Mail Protocols:** Jakarta / JavaMail for Android 1.6.7 (`android-mail`, `android-activation`)
- **CI/CD:** GitHub Actions (`build-apk.yml`) with automated Gradle builds, unit tests, and automated GitHub Releases

---

## 🙏 Credits

- **PennyWise AI:** Sincere thanks to [PennyWise AI](https://github.com/pennywise-ai) (licensed under AGPL-3.0) for the multi-bank parsing architecture in `:parser-core` and the declarative transaction rules design.
