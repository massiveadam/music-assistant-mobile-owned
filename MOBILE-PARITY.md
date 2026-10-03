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

Physical phone playback and Android Auto still require hardware checks. NTS supporter identification is integrated on web and Android, with the same per-account feed and connected-service lookup. WebRTC currently drops POST bodies; direct private/HTTPS connections support editing and credits.

For each new update, add its native path and evidence here, run the relevant native tests/build/upgrade, then verify GitHub's APK checksum. Do not mark delivery complete with only a local download.

## Inbox release verification

Inbox uses the same API as web and stays outside MA core tables. Per-recipient isolation, retries and provider permissions are checked in backend tests; native response tests cover account mismatch, duplicates and bounds. Release evidence is in `../../evidence/20261003-inbox/`. Live sender-to-recipient delivery is exercised with fixture accounts in a temporary database; no recommendation is sent to a real user for verification. Native signed-in upgrade and GitHub delivery checks are recorded there when completed.

Inbox release 0.14.11-owned passed 80 native tests, signed APK build and upgrade over 0.14.10 without clearing data. The existing account and Listen Later albums remained present. Inbox and send dialog were checked at 390 and 320 pixels; album controls scroll to keep Send and Collections reachable at 320. The inbox text contrast issue found by device review was corrected before publication. GitHub/local artifact verification is recorded with the release evidence.


## NTS release 0.14.13-owned

Live NTS artist/title identification and the last three broadcast entries appear in the native full player. Find on my services returns exact recording choices, album/version labels and safe HTTPS service links. Unknown markers remain unknown, stale feeds do not claim a current song, and account/station/foreground changes discard old requests. Connect the supporter account in the Sanchez HTTPS browser page; no password enters the Android app. Administrator-enabled server broadcast sharing feeds the usual player/Chromecast metadata path without sharing account credentials or personal service links. Album metadata requires unambiguous exact catalog matches.

The release also retains inbox and the preceding 0.14.12 shelf/library-sync update. Final build, signed-in upgrade and GitHub/local artifact checks belong in ../../evidence/20261003-nts-supporter/. TV pixels and broadcast/audio timing require the actual receiver and are not covered by emulator tests.
