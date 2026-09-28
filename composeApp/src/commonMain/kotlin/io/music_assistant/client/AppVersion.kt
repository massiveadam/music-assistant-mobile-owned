package io.music_assistant.client

/**
 * The running app's version name.
 *
 * Set once at startup from the platform (see MyApplication on Android). The
 * in-app updater compares this against GitHub release tags, so it must reflect
 * the actual installed build rather than a hardcoded value.
 */
object AppVersion {
    var versionName: String = "unknown"
}
