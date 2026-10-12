# Mobile coverage

## October 12 free source-recovery release — backend/web live, APK delivery in progress

The reserved 0.14.18-owned / 32 candidate now also handles optional search source-status and discography unlinked-source fields, search notices/retry, and explicit read-only source recovery. Search uses the normal backend budget, not the short browsing deadline. Unsupported servers retain ordinary behavior; route/account generations reject stale responses.

109 targeted native tests passed and the signed release build/lint completed. The candidate was installed over the earlier unpublished code-32 candidate without uninstalling/clearing data. Signed-in Home retained My music, Inbox, Listen Later/Hotel Usona, saved music and Dining Room TV; native Library rendered. Signer matches published code 31. This is an additional 32→32 test install, not a newly demonstrated 31→32 upgrade for these source-recovery changes; the preceding candidate's 31→32 upgrade is recorded below. October 12 approved rollout deployed all 13 backend modules and web release 3383e9b1598fa3f2. Native Aphex Twin rendered the recovery control/incomplete-source notice against this shared live backend; Home and Album collections retained Listen Later (8), Hotel Usona and the selected player. A transient post-restart collections timeout recovered on the next read. Authenticated server search returned Marina Herlop with both Apple and Bandcamp mappings, while failed Apple album reads kept five Bandcamp releases and reported failure. Source checkpoint and GitHub publication are the next delivery gates; physical phone/audio remain unverified. See `../../evidence/20261012-free-fixes-deploy/README.md` and preparation evidence. No emails or paid credentials; RYM/AOTY remain manual captures.

## Focused backend artist-browsing follow-up — deployed October 12

`../../maintenance/performance/artist-browsing/` is live for existing web/native album-groups requests: skip optional Apple favorite-rating waits, retaining full pagination and private-save overlays. It adds no client API fields and needs no APK release. Seven isolated tests cover the wire response, normal callers, cache/account isolation and cache age. Signed-in WayDroid retained home/library state and opened Aphex Twin's discography with 11 owned releases, matching web; test environment stalled initially and recovered without app-data clearing. Web's warmed group sample returned 41 releases in 255 ms; no native cold-load/audio timing is claimed. See `../../evidence/20261012-artist-browsing-deploy/README.md`. Larger candidate/APK delivery remains separate and pending.

## October 11 responsiveness candidate — not published

0.14.18-owned / 32 adds capability-gated, library-first browsing; retains ready tracklists on failed enrichment; avoids automatic similar-artist and all-provider editorial-artwork requests; and echoes complete original streaming-track DTOs for playback. Null DTO fields are omitted and provider availability/audio formats are retained. Only an unsupported optional capability probe is quiet on older servers; authentication and normal RPC errors keep their handling.

107 targeted native tests pass and signed release APKs build. A signed-in 31→32 WayDroid upgrade retained My music, Listen Later and the selected player without clearing data. Home/album screens were checked at 390px on the earlier candidate; the final RPC-compatibility correction rebuilt successfully and was installed over the candidate, retaining the signed-in state. Native UI ran against the **unchanged live backend**, not the staged interactive modules. See `../../evidence/20261011-responsiveness/README.md` for backend-parser contract and signing evidence. No hardware press-to-audio timing, new-backend native end-to-end check, GitHub publication, or Obtainium delivery is claimed. RYM/AOTY are still manual saved captures; new source status metadata requires separate promotion.

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

## Editorial mobile layout and library freshness

Release 0.14.12-owned places scores and known rating/review counts on covers, reserves equal text slots, and uses 48dp native album/source controls. Source explanations are in a scrollable info dialog. Cards grow with Android text scaling. Native TASKS_UPDATED album-sync transitions and direct album add/remove events refresh Recently added without restarting the app or changing library dates. Web subscribes to the existing album sync helper and reserves space from the measured player height; its mobile player selectors, album action spacing and send-dialog layer are corrected. These web CSS fixes do not require a native player change. Evidence is in `../../evidence/20261003-mobile-editorial/`.


Library import refresh (0.14.14-owned / 28): home observes successful album and track scan completions because filesystem track scans create albums after the album task ends. Preserves NTS and editorial artwork from release 27. Actual four-album import batch and signed upgrade verification are recorded in evidence/20261003-recent-import-batch.


Album actions and library icons, release 29: native album controls use two equal-width rows; save sits beside Play. Credits expand below the grid. Library icon uses actual MA membership rather than ownership inferred from a provider. Web already has responsive album actions and has no OWNED pill, so this layout change is native only. Verification evidence: evidence/20261004-album-actions.

Verified: 102 native checks, signed build/lint, and upgrade from 28 preserving login and collections. Actual album actions measured 175x48dp at 390px and 140x48dp at 320px with 1.3 font scale; save target is 48x48dp. Send and collection dialogs and Credits expansion checked without sending or changing saved music. Screenshots and bounds in evidence/20261004-album-actions. Physical ARM64 phone execution remains unchecked.


Inbox notification, release 30: compact unread count on Home Inbox and Library inbox icon. A single foreground observer refreshes every 30 seconds and on resume; read/archive signals cancel stale reads and refresh the count. Open inbox also polls without resetting its loaded list. Account/server changes clear the count. Web shows the same count beside Inbox in its sidebar and on the phone menu button. Evidence: evidence/20261004-inbox-notifications.

Verified: 104 native tests (including badge appearance, clearing and 99+ display), signed release build/lint, and upgrade from release 29 preserving login and Listen Later entries. At 390px the Home Inbox shortcut and read-only empty inbox fit above the player. Web notification tests cover foreground polling, resume, request overlap, failures and account changes. Positive browser counts used client-only fixtures; no albums were sent and no inbox records were altered. Physical ARM64 phone execution remains unchecked.


Inbox layout release 31 / 0.14.17-owned: compact 64dp album artwork/header, 48dp Open album and overflow controls, secondary actions in a menu, and icon refresh. Visible card artwork resolves through the recipient-authenticated read-only open API, without changing read/archive state. Existing account/server guards and unread observer are retained. Mobile web inbox was inspected at 320px and 390px; buttons already wrap with 44px targets and remain reachable above the player after scrolling. Evidence: ../../evidence/20261005-inbox-layout.

Verified: 107 native tests passed, signed build/lint passed, and release 30 upgraded to 31 without clearing app data. Signed-in inbox remained two messages/one unread after artwork resolution and overflow-menu inspection. Both cards fit above the player at 390px and 320px with 1.3 font scale; screenshots retained. These real messages resolve to unavailable items for Adam, so artwork correctly falls back to initials. No messages were sent, opened, marked read or archived. Physical ARM64 execution remains unchecked.
