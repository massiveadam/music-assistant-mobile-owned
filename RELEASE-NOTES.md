# Music Assistant Owned 0.16.1-owned

Artist pages now follow slow catalog requests automatically, so albums can appear
without a manual refresh when Apple Music finishes loading. Ready releases stay
visible; navigation cancels followups and retries are bounded. Offline sources
show their failure rather than being polled indefinitely.

Retains official Android0.16.0, private saves, album collections, inbox, credits,
editorial/NTS, owned discography and local-only offline downloads. The shared
server is updated to stable2.10.9, and Bandcamp uses the verified official
connection implementation instead of our obsolete TLS workaround.

Verified with native catalog tests and the selected data/home/detail/category
checks, signed build and upgrade over the existing signed-in app. Physical phone
audio and Android Auto remain outside the emulator verification.

Search keeps pending sources eligible for eight five-second followups rather than the previous six-second window. Android preserves hyphenated queries exactly like web.
