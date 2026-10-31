Contribute Guide
===

Build app
---

### Product flavors

dimension `deliver` defined for multi package install on devices

- prod  
  for release product
- dev  
  for debug local with application-id suffix `.dev`

```bash
# build & install the dev flavor onto a connected device
$ ./gradlew app:installDevDebug

# build the release product (needs keystore.properties, see README)
$ ./gradlew app:assembleProdRelease

# run the unit tests
$ ./gradlew app:testDevDebugUnitTest
```

### Code layout

- `provider/` — the Muzei art provider, the worker that fetches and downloads wallpapers, and the
  pure filtering logic in `ArtworkFilter.kt`
- `provider/network/` — Retrofit/OkHttp setup and the interceptors that add Wallhaven's expected headers
- `settings/` — the settings activity and its tabs

Everything the user can change lives under a `pref_` key in the default shared preferences; the worker
reads those keys directly, so keep the keys in the preference XML and the worker in step.
