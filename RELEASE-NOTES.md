# 0.14.21-owned

Offline listening for your local library. Open an album that has local files and choose Download for offline; the server converts it to space-saving Opus (about 1 MB per minute). Downloads live in the new Downloads screen (Home, Library, or the connection screen when the server is unreachable) and play with no connection, including lock-screen and headset controls. Apple Music and other streaming albums can't be downloaded. Downloading needs the direct or Sanchez HTTPS connection.

## Previous release: 0.14.20-owned


Search results keep the server's ranking. Popular Apple Music results are no longer pushed below alphabetical library or owned items, results by the artist you searched for come before covers and tributes, and an album on both Apple Music and Bandcamp opens the Apple copy. An exact title match in your library still comes first. Requires the matching server update (already live on Sanchez).

## Previous release: 0.14.19-owned

Search fills in slow sources automatically. Library results appear right away; if Apple Music (or another streaming source) is still searching on the server, the app quietly checks again every 1.5 seconds, up to 4 times, and adds its results in place. "Not fully checked" and Retry only appear if a source still hasn't answered after that.

## Previous release: 0.14.18-owned


Faster library-first artist/album browsing with capability-gated server support. Search reports incomplete sources and offers retry without discarding ready results. Artist pages can explicitly refresh catalogs and recover corroborated source links without changing your library mappings or private saves. Older servers retain compatibility.

Reduces background artwork/catalog requests and retains complete streaming-track metadata for playback. Editorial cards distinguish manual saved captures from scheduled refreshes; RYM/AOTY do not become automatic feeds.

Retains compact inbox cards and recipient-scoped artwork, unread notifications, My music, Listen Later/collections, album actions, NTS and import refresh. No app-data clearing or account reauthentication is required. Apple cooldowns still apply.

## Previous release: 0.14.17-owned

Inbox cards now have a compact album header and artwork from your available music sources. Open album is the primary action; Find album, Mark read and Archive are in an accessible overflow menu. Refresh uses an icon to keep the heading readable on small screens. Viewing artwork does not mark recommendations read.

Retains account-scoped inbox notifications, album action layout, truthful library icons, editorial scores/counts, NTS and Beets import refresh.
