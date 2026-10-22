# CampusFoldTracker 💸

[![Build Android APK](https://github.com/bluntking/CampusFoldTracker/actions/workflows/build-apk.yml/badge.svg)](https://github.com/bluntking/CampusFoldTracker/actions/workflows/build-apk.yml)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-black.svg?style=flat&logo=android)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.24-7F52FF.svg?style=flat&logo=kotlin)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202024.06.00-4285F4.svg?style=flat&logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Room](https://img.shields.io/badge/Room%20DB-2.6.1-orange.svg?style=flat)](https://developer.android.com/training/data-storage/room)
[![MediaPipe](https://img.shields.io/badge/MediaPipe%20GenAI-0.10.14-00C49F.svg?style=flat&logo=google)](https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

**CampusFoldTracker** is an ultra-fast, 100% offline-first Android expense tracker and bill-splitting financial matrix built natively in Kotlin and Jetpack Compose. Styled in a bespoke **Fold Money OLED dark aesthetic** (`#0A0A0C` background, `#16161A` matte charcoal elevated cards, and neon accent highlights), it is purpose-engineered for Indian campus university life.

---

## 🚀 Key Highlights & Architectural Pillars

### 1. 📭 Multi-Account SMS & Gmail Credit Parsing
- **Zero Cloud / Bank Credential Risk:** Operates 100% locally on device with no backend servers, no Plaid/Yodlee tokens, and no financial data transmission.
- **PNB Credit Card 8598:**
  - Automatically parses debits: captures amount, date/time, UPI Ref, raw merchant, and live Available Credit Limit (`Avl limit Rs. 59396.93`).
  - **Credit Card Bill Anti-Double-Counting:** Payment credits towards PNB CC 8598 are logged strictly as `CC_BILL_TRANSFER` (internal transfer), avoiding inflating expense or income metrics.
- **Axis Bank XX2003 (SMS + Gmail Notification Listener):**
  - **SMS Receiver:** Parses debit/credit notifications from AXISBK.
  - **`GmailNotificationListener` (`NotificationListenerService`):** Strictly filters notifications from `com.google.android.gm` for Axis Bank credit alerts (`INR XX.XX was credited to your A/c` / `UPI/P2A/...`).
  - Seamlessly handles incoming UPI settlements even when UPI banking app notifications are muted.
- **Other Supported Pre-Configured Accounts:**
  - Federal (Jupiter) `2548`
  - PNB Savings Account `5557`
  - Axis Bank Savings Account `5476`
  - Axis Bank Credit Card
  - Full CRUD support to add, edit, or customize any bank account.

---

### 2. 🏪 Campus Vendor Alias Normalization
Never decipher cryptic POS merchant names like `Sekar store` or `CHAATTERBOX` again. CampusFoldTracker features built-in vendor normalization:

| Raw Bank / UPI Keyword | Friendly Campus Vendor | Default Category |
| :--- | :--- | :--- |
| `Sekar store` | **Akka shop** | Chai & Smoke |
| `KARTHIK` | **Bistro** | Food |
| `CHAATTERBOX` | **Chaaterbox** | Food |
| `Mohan` | **Chennai beverage** | Beverages |
| `HungerBox` | **Hungerbox** | Food |
| `DD STORE` | **Utility store** | Utilities |

*Fully editable: Add new vendor keywords, modify mappings, or adjust default categories on the fly.*

---

### 3. 👥 Normalized FIFO Bill Splitting & UPI Auto-Settlement
- **Zero Balance Drift:** Balances are never stored as static figures; net balances are dynamically computed on the fly via Room queries:
  ```sql
  SELECT SUM(amountOwed - amountPaidSoFar) FROM split_items WHERE personId = :personId AND isSettled = 0
  ```
- **FIFO Settlement Engine (`settleAmountForPerson`):** When a friend pays back ₹X, the engine covers oldest unsettled bills first, supports partial payments, and handles overpayment carry-overs.
- **Truncated Bank Name Matching (`KAVYA KOH` → Kavya):**
  - **Case A (Exact Alias Match):** Auto-runs FIFO settlement, marks split as settled, and pushes a confirmation alert.
  - **Case B (Prefix / Substring Match):** Generates a 1-Tap **Confirm & Save Alias** banner: *"Received ₹20 from KAVYA KOH. Settle against Kavya's balance & save alias?"*. Once confirmed, saves `KAVYA KOH` to exact aliases for future hands-free settlements!
  - **Case C (Truncated Gmail Preview Fallback):** If Gmail truncates the sender, shows a 1-tap banner on the Splits tab: *"₹20 credited via Axis Gmail (Sender hidden). Apply to a friend?"*.

---

### 4. 📊 OLED Financial Pulse, Donut Charts & Burn Rate Warnings
- **True Spend Isolation:** Spend graphs only query `SUM(myActualShare)` for `EXPENSE` items, rigorously excluding friend shares, credit card payments, and income.
- **Interactive Jetpack Compose Canvas Donut Chart:** Switch instantly between Category view and Campus Vendor view.
- **"Where to Control" Intelligent Cards:**
  - **Burn Rate Warning:** Flags categories whose spending % outpaces elapsed days in the billing month.
  - **High-Frequency Campus Leaks:** Highlights vendors with >5 transactions this week (e.g., frequent small spends at Akka shop).
  - **Week-over-Week Spike:** Alerts when spending in a category jumps significantly compared to the prior 7 days.
- **Proactive Budget Alerts:** Sends instant high-priority notifications when reaching 50%, 70%, 90%, and 100% of category or overall budgets.

---

### 5. 🤖 On-Device Local AI Financial Coach (MediaPipe GenAI)
Inspired by PennyWiseAI, CampusFoldTracker integrates Google's **MediaPipe LLM Inference API** (`tasks-genai:0.10.14`):
- **Local Neural Inference:** Runs directly on your device CPU/GPU with quantized `.bin` or `.task` models (e.g., Gemma-2B-IT, Qwen-0.5B/1.5B) loaded via Android's Storage Access Framework.
- **Safe Graceful Fallback:** If no model file is loaded, the app never crashes—it delivers instant database-driven financial insights with clear setup guides.
- **Database Context Injection:** When you chat with your AI Coach, live metrics are dynamically injected into the system prompt:
  - Current month's true spend (`₹`)
  - Top category spends & campus vendor totals
  - Pending debts owed by friends
  - PNB credit card remaining limits
- **AI Unstructured Fallback:** Toggle to automatically parse unrecognized bank SMS or non-standard email alerts into structured `{amount, type, merchant, senderName}` JSON.

---

### 6. 🛡️ In-Place Updates & Data Safety Engine
- **Permanent Signing Keystore (`app/keystore/campusfold.jks`):**
  - Fixes the `INSTALL_FAILED_UPDATE_INCOMPATIBLE` error permanently.
  - All GitHub Actions cloud builds are signed with the same persistent RSA-2048 keystore so new APKs install smoothly over old versions.
- **Auto-Incrementing `versionCode`:**
  - In `app/build.gradle.kts`, `versionCode` automatically increments with each GitHub Actions run (`GITHUB_RUN_NUMBER + 100`).
- **Safe Non-Destructive Migrations:**
  - Uses `fallbackToDestructiveMigrationOnDowngrade()` alongside strict migrations (e.g., `MIGRATION_1_2` for `accounts`), ensuring app updates never wipe user data.
- **1-Tap Offline JSON Backup & Restore:**
  - Export all transactions, categories, vendor mappings, accounts, and friends to an open JSON file via Android Storage Access Framework (SAF).

---

## 📲 Installation Guide

### Download Latest APK
1. Go to the [CampusFoldTracker GitHub Actions Page](https://github.com/bluntking/CampusFoldTracker/actions).
2. Click on the latest workflow run on `main`.
3. Scroll down to the **Artifacts** section and download `campusfold-debug-apk`.
4. Extract the ZIP file and install `app-debug.apk` on your Android device.

---

### ⚠️ Android 13/14/15 Restricted Settings Permission Bypass
On Android 13+, Google restricts sideloaded apps from accessing accessibility and notification listener services by default. Follow these steps to enable SMS & Gmail notification tracking:

```
1. Install and launch CampusFoldTracker once.
2. Open Android Settings > Apps > CampusFoldTracker.
3. Tap the three dots (⋮) in the top-right corner.
4. Select "Allow restricted settings" (Authenticate with Fingerprint/PIN).
5. Open CampusFoldTracker:
   - Tap "Grant Permissions" for SMS access.
   - Tap "Enable Gmail Access" to turn on the Gmail Notification Listener.
```

---

## 🏗️ Tech Stack & Dependencies

- **Language:** Kotlin 1.9.24
- **UI Toolkit:** Jetpack Compose (BOM `2024.06.00`), Material 3, Material Icons Extended
- **Database:** Android Room 2.6.1 (with KSP schema exports)
- **Local AI:** Google MediaPipe Tasks GenAI `0.10.14` (LLM Inference)
- **Preferences:** Jetpack DataStore Preferences `1.1.1`
- **Concurrency:** Kotlin Coroutines & Flow
- **CI/CD:** GitHub Actions (`build-apk.yml`) with automated Gradle wrapper compilation and artifact publishing.

---

## 📂 Project Directory Structure

```text
CampusFoldTracker/
├── .github/workflows/
│   └── build-apk.yml              # Cloud CI/CD workflow building and signing APK
├── app/
│   ├── keystore/
│   │   └── campusfold.jks         # Permanent signing keystore
│   ├── schemas/                   # Room exported schemas for non-destructive migrations
│   └── src/main/java/com/campusfold/tracker/
│       ├── ai/
│       │   └── LocalAiManager.kt  # MediaPipe LLM Inference & context injection
│       ├── data/
│       │   ├── dao/               # Room DAOs (Account, Category, Person, Split, Transaction, Vendor)
│       │   ├── entity/            # Room Entities with foreign keys & unique indices
│       │   ├── model/             # Composite models (PersonWithBalance, CategorySpending, etc.)
│       │   ├── pref/              # DataStore Preferences (PnbLimit, OverallBudget, etc.)
│       │   ├── AppDatabase.kt     # Room database with MIGRATION_1_2
│       │   └── TransactionRepository.kt
│       ├── receiver/
│       │   └── SmsReceiver.kt     # High-priority broadcast receiver for PNB/Axis SMS
│       ├── service/
│       │   └── GmailNotificationListener.kt  # NotificationListenerService for Axis Gmail credits
│       ├── ui/
│       │   ├── components/        # FoldCard, MonospaceCurrencyText, StatusPill
│       │   ├── screens/           # Dashboard, Accounts, Analytics, Splits, Rules & Budgets
│       │   ├── sheets/            # TransactionDetail, AiCoachSheet, SplitSheet
│       │   ├── theme/             # OLED Black & Neon Palette
│       │   ├── CampusFoldApp.kt   # Scaffold & Navigation Matrix
│       │   └── TransactionViewModel.kt
│       └── util/
│           ├── BackupManager.kt   # 1-Tap SAF JSON export/restore engine
│           ├── CampusSmsParser.kt # Regex parser for PNB CC 8598 & Axis XX2003
│           ├── InboxSmsScanner.kt # Historical inbox scanner (30/90 days)
│           └── NotificationHelper.kt
└── README.md
```

---

## 🔒 Privacy & Security

CampusFoldTracker is built with an uncompromising commitment to privacy:
- **No Analytics / Telemetry:** No Firebase, no telemetry, no tracking SDKs.
- **No Remote Servers:** All parsing, database storage, and AI inference happen 100% on-device.
- **Offline Sandboxed:** Financial data never leaves your smartphone.

---

## 📄 License
Licensed under the [MIT License](LICENSE).
