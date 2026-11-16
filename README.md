# Skriti

A private, offline goal tracker for Android. Give each goal its own time slot and its own rules for when it counts as missed. Skriti reminds you, shows what is due now, and can block the apps that pull you off track. No account, no internet, no ads.

## What it does
- **Goals to do or to avoid.** Daily, on chosen weekdays, every few days, a number of times a week or month, or on a monthly pattern. Any time of day or between two times.
- **Your rules for a miss.** A goal counts as missed when the day ends and it is not done, when its time slot ends, or when you press "I missed it". Extra time after the end is optional.
- **A smaller win.** Give a goal a minimum, for example 120 minutes with a minimum of 20. Reaching the minimum keeps your streak. Goals counted in minutes have a timer.
- **Today.** A 24-hour dial, goals grouped by deadline, a week strip and a month calendar. Each goal shows how many of the last 30 days you kept and your streaks.
- **Reminders.** Once or repeating, getting firmer as the deadline nears, with a Done button. An optional real alarm over the lock screen.
- **If I miss it.** Nothing, a notice, blocking the apps you choose, or blocking plus a warning wallpaper on your home screen. An emergency unblock and an always-allowed list keep it safe.
- **Avoid goals.** Choose the apps that tempt you. When one opens, Skriti asks "Did you just use it?" and only your answer counts. Or block the apps for the goal's time slot.
- **Insights.** Mistakes with the trigger, mood and place you noted, patterns once there is enough data, a weekly review every Sunday, and suggestions that never change a goal by themselves.
- **Widgets, backups and breaks.** A home-screen widget, backup and restore, and a break for a trip or an illness.

## Install
**Recommended: install with Obtainium.** Obtainium is a free installer app that installs and updates apps straight from a project's GitHub releases. Installing this way worked on a phone where a downloaded APK was blocked by Play Protect.
1. Install Obtainium: search "Obtainium" in the F-Droid app, or download it from https://github.com/ImranR98/Obtainium/releases.
2. Open Obtainium, tap "Add App", paste `https://github.com/Saksham1800t/Skriti` and tap "Add".
3. Tap "Install" next to Skriti and allow Obtainium to install apps when Android asks. Obtainium also tells you when a new version is out.

On a phone that already has Obtainium, this link opens the Add App screen with Skriti filled in: `obtainium://add/https://github.com/Saksham1800t/Skriti`

**Or download the APK** from the project's Releases page and open it. Android asks once to allow installs from that source.

To use blocking you must switch on Skriti's blocker in Accessibility settings. If the switch is greyed out, open Skriti's App info, use the three-dot menu and choose "Allow restricted settings", then try again. Android turns the blocker off whenever the app is updated or force-stopped, and Skriti shows a red banner on Today until you switch it on again.

Play Protect may stop the install. Some phones, including phones in India, show "App blocked to protect your device" with only an OK button for an app installed from a downloaded file. A test build that asked for almost no permissions was blocked too, so it is not one setting in Skriti. You can install from a computer with USB debugging on (`adb install skriti-0.4.0.apk`), which Play Protect does not check, or turn off "Scan apps with Play Protect" in the Play Store for the install and turn it back on afterwards. The second lowers your phone's protection, so only do it if you trust the download. A Google Play version may follow.

## Skriti and Skriti Lite
Skriti has app blocking, which needs Android's accessibility service. Some phones, including phones in India, block a downloaded app that asks for that service. Skriti Lite is the same app without app blocking, the "did you just use it?" prompt and the accessibility service, so it installs without that block. Both use the same app id, so Lite can later be updated to the full version.

## Permissions
| Permission | Used for |
|---|---|
| Notifications | Reminders and the weekly review (asked when you first save a goal with reminders) |
| Exact alarms | Reminders at the exact minute and the optional alarm |
| Full-screen alerts | The loud reminder and the alarm over the lock screen |
| Display over other apps | Optional; one way of showing the block screen |
| Foreground service | Playing the alarm sound, and keeping the blocker running if you ask |
| Run at start-up | Rebuilding reminders after a reboot |
| Battery exemption | Optional; helps reminders arrive on phones that stop background apps |
| Set wallpaper | Showing and removing the warning wallpaper |
| Accessibility service | Knowing which app is in front, by name only, to block it or ask about it |

Skriti has no internet permission and never will. See [PRIVACY.md](PRIVACY.md).

## Your data and backups
All data stays on the phone. Uninstalling deletes it, so save a backup first:
- Settings, Backup, "Save backup file" writes a JSON file with everything. "Save for a spreadsheet" writes one row per recorded day and cannot be restored.
- "Restore backup" reads a JSON file. "Add to what is here" keeps what is on the phone and adds the rest; "Replace everything" starts from the backup.
- Before the app upgrades its database, it copies the old database file next to it.

## Known limits
- Android 10 or newer is required. It is tested on Android 17 on one phone.
- If you force-stop the app, Android cancels its alarms until you open it again. Alarms survive reboots, updates and clock changes.
- Some phone brands (Xiaomi, Oppo, Vivo, Samsung and others) stop background apps. Exempt Skriti from battery optimisation and check any autostart list.
- A time slot cannot cross midnight.
- A block does not silence an app; Skriti presses pause on whatever is playing.
- English only.

## Build from source
Requirements: JDK 17 and the Android SDK (platform 37, build-tools 37).
1. Set `JAVA_HOME` to the JDK.
2. Create `local.properties` with the SDK path, for example `sdk.dir=D\:/dev/android-sdk`.
3. `./gradlew assembleFullDebug` (`gradlew.bat assembleFullDebug` on Windows). The APK is `app/build/outputs/apk/full/debug/app-full-debug.apk`. `assembleLiteDebug` builds Skriti Lite.
4. `./gradlew testFullDebugUnitTest` runs the unit tests.

For a signed release build, create `keystore.properties` in the project root with `storeFile`, `storePassword`, `keyAlias` and `keyPassword`, then run `./gradlew assembleFullRelease` or `./gradlew assembleLiteRelease`. The file is not committed. Without it the release build is signed with the debug key and is for local testing only. A debug build installs next to a release build as "Skriti debug" and has a Time shift control for testing.

## Notes for Android 17
Android 17 restricts audio started from the background and activity launches from the background. Skriti plays the alarm only from a short foreground service, shows Done and I missed it as broadcasts rather than activity launches, and draws its block screen in an accessibility overlay, which needs no extra permission. Google's pages list no change to exact alarms, full-screen intents, live wallpapers, widgets or accessibility services. The block method was tried on a Moto G57 Power: it appears 30 to 250 ms after the app opens, works from the launcher and recents, after a restart, after lock and unlock, and in split-screen. Android switches the service off when the app is force-stopped or updated.

## Licence
Skriti is free software under the Apache License 2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
