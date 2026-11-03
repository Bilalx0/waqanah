# Waqanah 🛡️

Waqanah is an un-bypassable Android self-discipline and focus enforcement application designed for hardware-level accountability. Built using Android's **Device Owner** administrative mode, it eliminates soft bypasses by suspending distractive applications, locking network-level DNS filters, and blocking unauthorized uninstallation.

---

## Key Features

* **Device Owner Enforcement:** Uses OS-level administrative rights (`setPackagesSuspended`, `setApplicationHidden`) to completely hide target apps from the launcher—no bypass overlay screens.
* **Real-Time Interception:** Background engine (`ShieldService`) detects newly installed or sideloaded unauthorized apps (e.g., distractive browsers, VPNs) and suspends them within seconds.
* **Network-Level Filtering:** Enforces system-wide Private DNS (CleanBrowsing / Cloudflare Family) to block adult content across all browsers without using a toggleable local VPN.
* **Anti-Bypass System:** Prevents app uninstallation (`setUninstallBlocked`) and restricts system settings changes (e.g., date/time manipulation, VPN configuration changes) during active focus sessions.

---

## Tech Stack

* **Language:** Kotlin
* **UI Framework:** Jetpack Compose (Material 3)
* **Architecture:** Android Jetpack, Coroutines, DataStore
* **Platform:** Android SDK (Target API 35+)

---

## Setup & Development

### Prerequisites
* Android Studio (Latest)
* Android SDK 35+
* ADB (Android Debug Bridge) installed on host machine
* Android Emulator (API 34+) or physical device with developer options enabled
