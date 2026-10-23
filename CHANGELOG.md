# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

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
