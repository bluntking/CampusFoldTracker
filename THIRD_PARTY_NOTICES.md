# Third-Party Software Notices and Information

This project contains code or incorporates components adapted from open-source software, as detailed below.

---

## PennyWise AI

* **Project:** PennyWise AI (pennywiseai-tracker)
* **Copyright:** © 2024–2026 Sarim Khan and Contributors
* **License:** GNU Affero General Public License v3.0 (AGPL-3.0)
* **Source Code:** https://github.com/sarim2000/pennywiseai-tracker

This application incorporates concepts, patterns, and code adaptations originally developed as part of PennyWise AI, including:
1. On-device LLM inference using Google LiteRT-LM, isolated service process architecture, and model download integrity verification (SHA-256).
2. The `:parser-core` module containing financial SMS, OTP, and bank notification parser logic, pattern definitions, and test suites, licensed under the GNU Affero General Public License v3.0. Original copyright headers and licenses are retained on all copied files.
3. The rules engine (`RuleEngine`, rule domain models, conditions, and actions in `com.campusfold.tracker.rules`) adapted from PennyWise AI's rule engine architecture.
4. UI component structures, design system tokens (Spacing, Dimensions, Material 3 rounded scales, color palettes, Balance card presentation, floating bottom navigation, and onboarding flow architecture) inspired by PennyWise AI's presentation layer.

Under the terms of the AGPL-3.0 license:
A copy of the GNU Affero General Public License v3.0 is available at https://www.gnu.org/licenses/agpl-3.0.html.
