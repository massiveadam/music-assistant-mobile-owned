# Mobile coverage

Native Android is part of every Music Assistant update. The release source is this repository; GitHub Releases is the delivery channel Obtainium watches. Web source is `../../retheme/` in the full workspace.

| Feature | Android path |
| --- | --- |
| Private saves and My music | Home shortcuts, native library filters and hearts, same authenticated server state |
| Listen Later and named album collections | Home shortcut and album collection chooser; search, sorting, listened status and management |
| Home customization | Edit rows, visibility and order, scoped per server/user; device layout settings are separate from web |
| Artist catalog | Owned releases first; source, type, year, search and saved filters; versions, sorting and Show more |
| Album credits | Album credits panel, contributor albums, release matching and refresh |
| Discovery mix | Daily and manual rotation from visible shelves, with source labels; avoids recent listens and previous picks when alternatives exist |
| Album inbox | Native album Send album button, Home/Library inbox, recipient picker, optional note, open/find/read/archive; shared authenticated sidecar API |
| Review-site discovery | RYM/AOTY weekly releases and Pitchfork Best New Music; shared snapshots, scores and verified rating/review counts, dates, source/review links, count selector and native album lookup |
| Forgotten recommendations and radio metadata | Shared backend responses; home recommendation rows and native player |

The prior personal-music/artist/credits paths passed 50 tests and a signed-in native upgrade in `../../evidence/20261002-android-parity/`. The October 3 review-row and GitHub delivery checks are recorded in `../../evidence/20261003-mobile-catchup/`.

Physical phone playback and Android Auto still require hardware checks. NTS song recognition was a proof of concept and has no integrated web user path to port. WebRTC currently drops POST bodies; direct private/HTTPS connections support editing and credits.

For each new update, add its native path and evidence here, run the relevant native tests/build/upgrade, then verify GitHub's APK checksum. Do not mark delivery complete with only a local download.

## Inbox release verification

Inbox uses the same API as web and stays outside MA core tables. Per-recipient isolation, retries and provider permissions are checked in backend tests; native response tests cover account mismatch, duplicates and bounds. Release evidence is in `../../evidence/20261003-inbox/`. Live sender-to-recipient delivery is exercised with fixture accounts in a temporary database; no recommendation is sent to a real user for verification. Native signed-in upgrade and GitHub delivery checks are recorded there when completed.

Inbox release 0.14.11-owned passed 80 native tests, signed APK build and upgrade over 0.14.10 without clearing data. The existing account and Listen Later albums remained present. Inbox and send dialog were checked at 390 and 320 pixels; album controls scroll to keep Send and Collections reachable at 320. The inbox text contrast issue found by device review was corrected before publication. GitHub/local artifact verification is recorded with the release evidence.

## Editorial mobile layout and library freshness

Release 0.14.12-owned places scores and known rating/review counts on covers, reserves equal text slots, and uses 48dp native album/source controls. Source explanations are in a scrollable info dialog. Cards grow with Android text scaling. Native TASKS_UPDATED album-sync transitions and direct album add/remove events refresh Recently added without restarting the app or changing library dates. Web subscribes to the existing album sync helper and reserves space from the measured player height; its mobile player selectors, album action spacing and send-dialog layer are corrected. These web CSS fixes do not require a native player change. Evidence is in `../../evidence/20261003-mobile-editorial/`.

The final editorial candidate passed91 native tests and a signed-in upgrade preserving the account and saved Listen Later entries. Artwork now prefers an exact, unambiguous MA album match, with publisher artwork and initials as fallbacks. The shared recommendation plugin uses verified Beets dates for matched local imports and a separate stable fallback ledger; native and web refresh on album sync completion. Evidence includes320px larger-text and390px native checks, web action/dialog layering, permission-filter regressions and signed artifact delivery checks.
