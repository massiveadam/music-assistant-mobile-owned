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
| Review-site discovery | RYM/AOTY weekly releases and Pitchfork Best New Music; shared snapshots, scores, dates, source/review links, count selector and native album lookup |
| Forgotten recommendations and radio metadata | Shared backend responses; home recommendation rows and native player |

The prior personal-music/artist/credits paths passed 50 tests and a signed-in native upgrade in `../../evidence/20261002-android-parity/`. The October 3 review-row and GitHub delivery checks are recorded in `../../evidence/20261003-mobile-catchup/`.

Physical phone playback and Android Auto still require hardware checks. NTS song recognition was a proof of concept and has no integrated web user path to port. WebRTC currently drops POST bodies; direct private/HTTPS connections support editing and credits.

For each new update, add its native path and evidence here, run the relevant native tests/build/upgrade, then verify GitHub's APK checksum. Do not mark delivery complete with only a local download.

## Upcoming inbox and music sending

Adam is currently implementing the web inbox/music sending feature in a separate task. Its native sending, receiving, inbox state and applicable notification/user-account behavior are required in the same feature release once the shared API and web flow are ready. This catch-up does not change that work in progress or claim inbox parity is complete.

Catch-up verification: all 70 selected native tests passed, including 13 editorial contract/matching tests and 7 discovery mix tests. The homepage tests target recommendation rows explicitly and reveal the collapsing toolbar before refresh. Release build and installed-upgrade evidence are recorded outside the repository in the catch-up evidence directory.
