package com.example.ytshortsblocker.service

/**
 * THE ONE FILE TO EDIT WHEN YOUTUBE BREAKS DETECTION.
 *
 * Everything that depends on YouTube's internals lives here. No detection rules are hardcoded
 * anywhere else — [ShortsDetector] only executes what this file declares.
 *
 * Captured from YouTube on 2026-08-11, phone UI language: English.
 */
object ShortsSignature {

    const val YOUTUBE_PACKAGE = "com.google.android.youtube"

    /**
     * Primary signal: view IDs that exist only while the Shorts player is on screen.
     * A node must ALSO be visible to the user to count — YouTube keeps offscreen fragments alive.
     *
     * Verified absent from: home feed, search results, the normal video player.
     */
    val STRONG_VIEW_IDS = listOf(
        "reel_recycler",
        "reel_player_page_container",
    )

    /**
     * Fallback signal: content descriptions of the Shorts action rail (the vertical button strip).
     * Used only if every STRONG_VIEW_ID fails, so detection survives an ID rename.
     *
     * These are LOCALIZED. On a non-English phone they must be re-captured in that language.
     * Compared lowercase, as substrings.
     */
    val ACTION_RAIL_DESCRIPTIONS = listOf(
        "remix",
        "see more videos using this sound",
        "share this video",
    )

    /** How many DISTINCT entries above must be visible at once. Two avoids accidental matches. */
    const val ACTION_RAIL_MIN_MATCHES = 2

    /**
     * DO NOT ADD THESE — kept as documentation of things that look right and are wrong.
     *
     * - "reel_time_bar": present on the home feed and the normal player too. Always-on container.
     * - contentDescription "Shorts": the bottom navigation tab, present on every screen.
     * - "... - play Short": Shorts thumbnails inside the home feed, not the Shorts player.
     * - "like this video along with N other people": the normal player uses the same wording.
     */
    val KNOWN_FALSE_POSITIVES = listOf("reel_time_bar", "Shorts", "play Short")

    fun qualifiedId(id: String): String = "$YOUTUBE_PACKAGE:id/$id"
}
