# CampusFoldTracker 💸

[![Releases](https://img.shields.io/github/v/release/bluntking/CampusFoldTracker?color=00E676&label=Releases&logo=github)](https://github.com/bluntking/CampusFoldTracker/releases)
[![Direct APK Download](https://img.shields.io/badge/Direct%20APK-Download%20(No%20ZIP)-00E5FF.svg?logo=android)](https://github.com/bluntking/CampusFoldTracker/releases)
[![Build Android APK](https://github.com/bluntking/CampusFoldTracker/actions/workflows/build-apk.yml/badge.svg)](https://github.com/bluntking/CampusFoldTracker/actions/workflows/build-apk.yml)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-black.svg?style=flat&logo=android)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.24-7F52FF.svg?style=flat&logo=kotlin)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202024.06.00-4285F4.svg?style=flat&logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Room](https://img.shields.io/badge/Room%20DB-2.6.1-orange.svg?style=flat)](https://developer.android.com/training/data-storage/room)
[![MediaPipe](https://img.shields.io/badge/MediaPipe%20GenAI-0.10.14-00C49F.svg?style=flat&logo=google)](https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

> ### 📱 [Download Latest APK from GitHub Releases](https://github.com/bluntking/CampusFoldTracker/releases)
> Direct Android `.apk` downloads (no `.zip` extraction needed). Each new version is automatically published with full commit changelogs and installs in-place over your existing installation without data loss.

---

**CampusFoldTracker** (launcher display: **CampusFold**) is an ultra-fast, 100% offline-first Android expense tracker, split-bill financial matrix, and local AI money coach built natively in Kotlin and Jetpack Compose. Styled in a bespoke **Fold Money OLED dark aesthetic** (`#0A0A0C` background, `#16161A` matte charcoal elevated cards, and neon accent highlights), it is purpose-engineered for Indian campus university life.

---

## 🚀 Key Highlights & Architectural Pillars

### 1. 📭 Multi-Account SMS & Notification Parsing
- **Zero Cloud / Bank Credential Risk:** Operates 100% locally on device with no backend servers, no Plaid/Yodlee tokens, and no financial data transmission.
- **PNB Credit Card 8598:**
  - Automatically parses debits: captures amount, date/time, UPI Ref, raw merchant, and live Available Credit Limit (`Avl limit Rs. 59396.93`).
  - **Credit Card Bill Anti-Double-Counting:** Payment credits towards PNB CC 8598 are logged strictly as `CC_BILL_TRANSFER` (internal transfer), avoiding inflating expense or income metrics.
- **Axis Bank XX2003 (SMS + Notification Listener):**
  - **SMS Receiver:** Parses debit/credit notifications from AXISBK.
  - **`GmailNotificationListener` (`NotificationListenerService`):** Strictly filters notifications from `com.google.android.gm` for Axis Bank credit alerts (`INR XX.XX was credited to your A/c` / `UPI/P2A/...`).
  - Seamlessly captures incoming UPI settlements even when UPI banking app notifications are muted.
- **Multi-Account Manager:**
  - Built-in support for PNB CC 8598, Axis Bank XX2003, Federal (Jupiter) 2548, PNB Savings 5557, Axis Savings 5476, and custom accounts with live balance tracking.

---

### 2. 👥 Normalized Bill Splitting & "Paid Friend for My Share"
- **Zero Balance Drift:** Friend balances are never stored as static figures; net balances are dynamically computed on the fly via Room queries:
  ```sql
  SELECT SUM(amountOwed - amountPaidSoFar) FROM split_items WHERE personId = :personId AND isSettled = 0
  ```
- **FIFO Settlement Engine:** When a friend pays you back ₹X, the engine automatically covers the oldest unsettled bills first, supports partial payments, and prompts to log overpayment as advance income.
- **🤝 Paid Friend for My Share:**
  - When your friend pays for dinner, groceries, or cabs and you UPI them your share, mark the debit transaction with **1 tap** using the *"Paid Friend for My Share"* card.
  - Automatically formats the merchant name as `"Paid to [Friend] (My Share)"` and assigns your true category (*Food & Dining*, *Travel*, etc.) so your spending analysis reflects real living expenses.
  - Available in **Transaction Details**, **Manual Add Dialog**, and as a **"Paid Share"** shortcut on every friend card in the Splits tab.
- **Smart Credit Matching:** Detects incoming UPI payments from friends and prompts 1-tap settlement confirmations.

---

### 3. 📊 Dynamic OLED Financial Pulse & Analytics Matrix
- **True Spend Isolation:** Spend graphs only query `SUM(myActualShare)` for `EXPENSE` items, rigorously excluding friend shares, credit card payments, and income.
- **Dynamic Month & Week Switcher:**
  - Switch effortlessly between **Month** and **Week** views with the top segmented controller.
  - Page backwards/forwards across past months or weeks (`<` `Period Label` `>`), or tap **Filter** to jump to any past period.
  - The Donut chart, vendor rankings, category progress bars, and budget pacing dynamically re-compute in real time for the selected period.
- **Comparative Expense Trajectory (WoW & MoM):**
  - Smooth multi-line area charts comparing your current week/month spending trajectory against the prior period in background grey.
- **"Where to Control" Intelligent Audits:**
  - **Burn Rate Warning:** Flags categories whose spending % outpaces elapsed days in the billing cycle.
  - **High-Frequency Campus Leaks:** Highlights vendors with >5 transactions in the week (e.g. repeated canteen or chai visits).
  - **Week-over-Week Spike:** Alerts when spending in a category jumps significantly compared to the prior 7 days.
  - **Proactive Budget Alerts:** Sends instant high-priority notifications when reaching 50%, 70%, 90%, and 100% of category or overall targets.

---

### 4. 📝 Expense Descriptions & Memory Notes
- Add optional personal notes/descriptions to any expense (e.g., *"Dinner with Rahul at BBQ Nation"*, *"Bought engineering drawing board"*, *"Split hostel groceries"*).
- Notes are displayed in subtle italic cyan text on transaction feed cards across the **Home** and **Dashboard** tabs.
- Fully supported in offline JSON backups and Room DB migrations.

---

### 5. 🏪 Campus Vendor Alias Normalization
Decipher cryptic POS merchant names like `Sekar store` or `CHAATTERBOX` instantly:

| Raw Bank / UPI Keyword | Friendly Campus Vendor | Default Category |
| :--- | :--- | :--- |
| `Sekar store` | **Akka shop** | Chai & Smoke |
| `KARTHIK` | **Bistro** | Food |
| `CHAATTERBOX` | **Chaaterbox** | Food |
| `Mohan` | **Chennai beverage** | Beverages |
| `HungerBox` | **Hungerbox** | Food |
| `DD STORE` | **Utility store** | Utilities |

*Fully editable: Add new vendor keywords, edit friendly names, or set custom category rules.*

---

### 6. 🤖 On-Device Local AI Financial Coach (MediaPipe GenAI)
Inspired by PennyWiseAI, CampusFoldTracker integrates Google's **MediaPipe LLM Inference API** (`tasks-genai:0.10.14`):
- **Local Neural Inference:** Runs directly on your device CPU/GPU with quantized `.bin` or `.task` models (e.g., Gemma-2B-IT, Qwen-0.5B/1.5B) loaded via Android's Storage Access Framework.
- **Safe Graceful Fallback:** If no model file is loaded, the app never crashes—it delivers instant database-driven financial insights with clear setup guides.
- **Database Context Injection:** Live metrics are dynamically injected into the system prompt:
  - Current month's true spend (`₹`)
  - Top category spends & campus vendor totals
  - Pending debts owed by friends
  - PNB credit card remaining limits
- **AI Unstructured Fallback:** Automatically parse unrecognized bank SMS or non-standard email alerts into structured `{amount, type, merchant, senderName}` JSON.

---

### 7. 🛡️ In-Place Updates & Data Safety Engine
- **Seamless In-Place Updates:**
  - All GitHub Actions cloud builds are signed with a persistent keystore so new APKs install directly over old versions without uninstalling.
- **Auto-Incrementing Versioning:**
  - `versionCode` automatically increments with each cloud build (`GITHUB_RUN_NUMBER + 100`) and the clean launcher name **CampusFold** displays on your home screen.
- **Safe Non-Destructive Migrations:**
  - Strict Room database migrations (`MIGRATION_1_2`, `MIGRATION_2_3`), ensuring app updates never wipe user data.
- **1-Tap Offline JSON Backup & Restore:**
  - Export all transactions, categories, vendor mappings, accounts, and friends to an open JSON file via Android Storage Access Framework (SAF).

---

## 📲 Installation Guide

### Download Latest APK
1. Visit the [CampusFoldTracker Releases Page](https://github.com/bluntking/CampusFoldTracker/releases).
2. Download the latest `CampusFold-1.0.XX.apk` directly to your phone (no ZIP unzipping needed!).
3. Tap the downloaded APK to install or upgrade in-place.

---

### ⚠️ Android 13/14/15 Restricted Settings Permission Bypass
On Android 13+, Google restricts sideloaded apps from accessing notification listeners by default. Follow these steps to enable SMS & Gmail notification tracking:

```text
1. Install and launch CampusFoldTracker once.
2. Open Android Settings > Apps > CampusFold (or CampusFoldTracker).
3. Tap the three dots (⋮) in the top-right corner.
4. Select "Allow restricted settings" (Authenticate with Fingerprint/PIN).
5. Open CampusFoldTracker:
   - Tap "Grant Permissions" for SMS access.
   - Tap "Enable Gmail Access" to turn on the Notification Listener for Axis Bank alerts.
```

---

## 🏗️ Tech Stack & Dependencies

- **Language:** Kotlin 1.9.24
- **UI Toolkit:** Jetpack Compose (BOM `2024.06.00`), Material 3, Material Icons Extended
- **Database:** Android Room 2.6.1 (with KSP schema exports & SQL migrations)
- **Local AI:** Google MediaPipe Tasks GenAI `0.10.14` (LLM Inference)
- **Preferences:** Jetpack DataStore Preferences `1.1.1`
- **Concurrency:** Kotlin Coroutines & Flow
- **CI/CD:** GitHub Actions (`build-apk.yml`) with automated Gradle wrapper compilation, test suite execution, and automatic changelog-enabled release publishing.

---

## 🔒 Security & Keystore Notice

- **100% Offline & Private:** No tracking, no analytics, no remote servers. Financial data never leaves your smartphone.
- **Signing Keystore Notice:**
  - The repository includes a development signing key (`app/keystore/campusfold.jks`) so GitHub Actions can automatically sign APKs for easy sideloading without manual secret setup.
  - If you intend to publish this app publicly on the Google Play Store or distribute production releases, do **not** use the public test key. Create a private production keystore and store it securely in GitHub Actions repository secrets or use Google Play App Signing.

---

## 📄 License
Licensed under the [MIT License](LICENSE).
