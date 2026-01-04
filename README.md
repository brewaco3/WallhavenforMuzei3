Wallhaven for Muzei
===

Wallhaven.cc plugin for the Muzei 3 API.

Forked from [Pixiv for Muzei 3](https://github.com/yellowbluesky/PixivforMuzei3) with the goal of sourcing wallpapers from Wallhaven's public feeds.

**[Download from GitHub Releases](https://github.com/brewaco3/WallhavenforMuzei3/releases)**

Features
  - Pulls wallpapers from Wallhaven's public toplist and hot feeds
  - Supports SFW, Sketchy, and NSFW purity filters (NSFW requires [API token from Wallhaven Settings](https://wallhaven.cc/settings/account))
  - Filter against multiple criteria:
    - Aspect ratio
    - View count
  - Minimum resolution
  - Option to automatically clear cache on a daily basis
  - Option to save artwork to external user storage
  - No tracking or collection of user details

  [![Scc Count Badge](https://sloc.xyz/github/yellowbluesky/PixivforMuzei3/)](https://github.com/yellowbluesky/PixivforMuzei3/)

<img src="https://github.com/brewaco3/WallhavenforMuzei3/blob/master/fastlane/metadata/android/en-US/images/phoneScreenshots/1.png" width="250">
<img src="https://github.com/brewaco3/WallhavenforMuzei3/blob/master/fastlane/metadata/android/en-US/images/phoneScreenshots/2.png" width="250">
<img src="https://github.com/brewaco3/WallhavenforMuzei3/blob/master/fastlane/metadata/android/en-US/images/phoneScreenshots/3.png" width="250">
<img src="https://github.com/brewaco3/WallhavenforMuzei3/blob/master/fastlane/metadata/android/en-US/images/phoneScreenshots/4.png" width="250">
<img src="https://github.com/brewaco3/WallhavenforMuzei3/blob/master/fastlane/metadata/android/en-US/images/phoneScreenshots/5.png" width="250">

## [Contribute Guide](./CONTRIBUTE.md)

Contributors
---

- [alvince](https://github.com/alvince): Significant contributions to code and improvements in application safety
- [Notsfsssf](https://github.com/Notsfsssf): SNI Bypass
  - His Android Pixiv client [Pix-EzViewer](https://github.com/Notsfsssf/Pix-EzViewer) is excellent, try it out
- [SettingDust](https://github.com/SettingDust): Localisation
- [Linsui](https://github.com/linsui): F-Droid RFP and localisation

Build instructions
---

1. **Install dependencies**
   - Android Studio (Iguana or newer) with Android SDK Platform 35 and corresponding build tools.
   - Java 17 (bundled with modern Android Studio releases).
   - `adb` from the Android Platform Tools for deploying to a device.
2. **Clone the project and prepare the SDK path**
   ```bash
   git clone https://github.com/yellowbluesky/PixivforMuzei3.git
   cd PixivforMuzei3
   ```
   Ensure that `local.properties` contains a valid `sdk.dir=/absolute/path/to/Android/Sdk`. Android Studio populates this automatically when you open the project; for command-line builds you can create the file manually.
3. **Build a debug APK**
   ```bash
   ./gradlew :app:assembleDebug
   ```
   The resulting APK is written to `app/build/outputs/apk/debug/app-debug.apk` and uses the application ID `com.brewaco3.muzei.wallhaven`.
4. **(Optional) Build a release APK**
   Provide signing credentials in `gradle.properties` or pass them via the command line, then run:
   ```bash
   ./gradlew :app:assembleRelease
   ```
   The signed artifact is located under `app/build/outputs/apk/release/`.
5. **Install on a device**
   Enable developer options and USB debugging on your Android phone, connect it via USB, then run:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
   Replace the APK path with the release variant if you built a signed package.
