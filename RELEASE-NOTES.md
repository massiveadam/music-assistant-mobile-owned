# Music Assistant Owned 0.16.0-owned

Updated to official Android 0.16.0 stable with its navigation, playback dispatch,
search history/source controls and Sendspin updates. Private saves remain separate
from Music Assistant's shared favorites.

Retains Listen Later and named collections, inbox/unread, editorial/NTS shelves,
credits, owned-first discography, source recovery and local-only offline downloads.
Restores the album Save, Listen Later, Send, Collections, Credits and Download
controls whose call sites were missing in the previous client release.

The server remains stable 2.10.8. The web app receives compatible official fixes
without adopting the newer frontend's incompatible API requirement.

Verified with native tests, the signed release build and an upgrade over the
existing signed-in Waydroid app. Physical phone audio and Android Auto remain
outside this emulator check.
