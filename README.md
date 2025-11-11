Wallhaven for Muzei
===

Wallhaven.cc plugin for the Muzei 3 API.

Forked from [Pixiv for Muzei 3](https://github.com/yellowbluesky/PixivforMuzei3) with the goal of sourcing wallpapers from Wallhaven's public feeds.

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
     alt="Get it on F-Droid"
     height="80">](https://f-droid.org/packages/com.brewaco3.muzei.wallhaven/)
[<img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png"
     alt="Get it on Google Play"
     height="80">](https://play.google.com/store/apps/details?id=com.brewaco3.muzei.wallhaven)

Features
  - Pulls wallpapers from Wallhaven's public toplist and hot feeds
  - Supports SFW, Sketchy, and (after logging in) NSFW purity filters
  - Filter against multiple criteria:
    - Aspect ratio
    - View count
  - Minimum resolution
  - Option to automatically clear cache on a daily basis
  - Option to save artwork to external user storage
  - No tracking or collection of user details

  [![Scc Count Badge](https://sloc.xyz/github/yellowbluesky/PixivforMuzei3/)](https://github.com/yellowbluesky/PixivforMuzei3/)

<img src="https://github.com/yellowbluesky/PixivforMuzei3/blob/master/fastlane/metadata/android/en-US/images/phoneScreenshots/1.png" width="250">
<img src="https://github.com/yellowbluesky/PixivforMuzei3/blob/master/fastlane/metadata/android/en-US/images/phoneScreenshots/2.png" width="250">
<img src="https://github.com/yellowbluesky/PixivforMuzei3/blob/master/fastlane/metadata/android/en-US/images/phoneScreenshots/3.png" width="250">
<img src="https://github.com/yellowbluesky/PixivforMuzei3/blob/master/fastlane/metadata/android/en-US/images/phoneScreenshots/4.png" width="250">
<img src="https://github.com/yellowbluesky/PixivforMuzei3/blob/master/fastlane/metadata/android/en-US/images/phoneScreenshots/5.png" width="250">

How the wallpaper pipeline works
---

1. **Work scheduling** – Muzei asks the provider for a new image, which triggers `WallhavenArtWorker` via `WorkManager`. The worker coordinates cache maintenance, purity filtering, and download orchestration.
2. **Feed selection** – Depending on the chosen mode, the worker creates a `ContentsHelper` (for toplist/hot) or other helper classes to hit Wallhaven's REST API through `RestClient`. Each request includes the Firefox user agent and, when present, the stored Wallhaven session cookie so that NSFW pages return data.
3. **Artwork harvesting** – Helper classes deserialize the JSON into `RankingArtwork` models. The worker loops through the feed, discarding images that violate user filters (resolution, purity, blocked IDs) until enough items are ready for Muzei.
4. **Download and storage** – `WallhavenImageDownloadService` streams the selected wallpaper into the app's cache or shared storage, updating the Muzei content provider once the file is written.

Wallhaven login for NSFW content
---

1. Open **Settings → Login**. If you are already logged in, pressing the preference will clear the saved Wallhaven session.
2. The in-app browser loads `https://wallhaven.cc/login` with the Firefox desktop user agent. After you authenticate, the app captures the resulting cookies and stores them locally (visible under the login preference).
3. Future feed calls automatically attach this cookie via `WallhavenMuzeiSupervisor`, unlocking the NSFW purity option for both toplist and hot feeds.

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
