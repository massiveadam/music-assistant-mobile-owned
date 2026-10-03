# Custom Music Assistant Android client

Every applicable Music Assistant feature or behavior change must reach both the web client and this native client. Keep `MOBILE-PARITY.md` current and check the actual native user path before marking a feature complete.

Preserve Adam's existing changes and signing key. Check the data/UI tests, signed release build and upgrade over the existing app without uninstalling or clearing data. Increment both versionCode and versionName. Follow the parent project's `ANDROID.md` and `.agents/skills/verify-music-assistant/SKILL.md` when working in the full workspace.

Release through `massiveadam/music-assistant-mobile-owned` GitHub Releases with `Music-Assistant-Owned.apk` and its SHA-256 checksum. Obtainium uses this repository. A local APK or local download page alone does not deliver an update to Obtainium. Commit and push the verified source, then publish the checked artifact; never replace an existing version with different bytes. The parent project's `build-and-deploy-android.sh --publish-only` verifies and publishes to both destinations.
