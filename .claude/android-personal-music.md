# Personal music and Android parity

The Android client uses native Compose screens and the same authenticated Sanchez sidecar APIs as the web app. Whole album collection references are separate from playlists, shared owned catalog and app saves. No native shared favorites or old unplayed recommendation data is migrated.

Considered designs: (1) local preferences per device, later synchronization; (2) server-confirmed per-user repository using the existing API and current authenticated session. Chose (2): avoids duplicate ownership rules, cross-device conflicts and account leakage. No new database or server mount is necessary.

PersonalApi uses ServiceClient's proven current token and server identity. Repository serializes reads and writes and checks both account/server/session and generation before publishing; logout/disconnect invalidates caches and queued writes. Errors retain the last confirmed state for the same user. No tokens in public state, logs, snapshots or backups.

Direct private HTTP and public HTTPS support full functionality. Upstream WebRTC HTTP proxy currently omits POST bodies: read-only shelves work there; edits explain that the HTTPS Sanchez connection is required. Never send a private token to an unrelated fallback origin.

Verification: native account/server race tests, sidecar DTO contract tests, native Compose user-path tests and signed APK build. Android distribution must derive real package/version and verify signing continuity; upstream sync happens in a review branch, not during publication.
