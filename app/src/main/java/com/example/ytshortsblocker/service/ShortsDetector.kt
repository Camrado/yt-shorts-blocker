package com.example.ytshortsblocker.service

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Decides whether the current window is the Shorts player. Pure logic — no state, no Android
 * service. Everything version-specific comes from [ShortsSignature].
 */
object ShortsDetector {

    /** Safety limits for the fallback walk, so a huge tree can never stall the service. */
    private const val MAX_NODES = 300
    private const val MAX_DEPTH = 25

    fun detect(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        if (root.packageName?.toString() != ShortsSignature.YOUTUBE_PACKAGE) return false

        // Fast path: ask the system to find these IDs directly. This runs inside the accessibility
        // framework's own index rather than walking the tree ourselves, and it is what runs
        // virtually every time.
        for (id in ShortsSignature.STRONG_VIEW_IDS) {
            val matches = root.findAccessibilityNodeInfosByViewId(ShortsSignature.qualifiedId(id))
                ?: continue
            // Visibility matters: YouTube keeps the Shorts fragment in the tree after you leave it.
            if (matches.any { it.isVisibleToUser }) return true
        }

        // Slow path: only reached if YouTube renamed the IDs. Bounded and early-exiting.
        return matchesActionRail(root)
    }

    /**
     * Looks for the Shorts action rail by content description. Requires several DISTINCT phrases
     * so that one coincidental match cannot trigger a block.
     */
    private fun matchesActionRail(root: AccessibilityNodeInfo): Boolean {
        val found = HashSet<String>()
        val stack = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        stack.addLast(root to 0)
        var visited = 0

        while (stack.isNotEmpty()) {
            if (visited >= MAX_NODES) return false
            val (node, depth) = stack.removeLast()
            visited++
            if (depth > MAX_DEPTH) continue

            if (node.isVisibleToUser) {
                val description = node.contentDescription?.toString()?.lowercase()
                if (description != null) {
                    for (phrase in ShortsSignature.ACTION_RAIL_DESCRIPTIONS) {
                        if (description.contains(phrase)) {
                            found.add(phrase)
                            if (found.size >= ShortsSignature.ACTION_RAIL_MIN_MATCHES) return true
                        }
                    }
                }
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                stack.addLast(child to depth + 1)
            }
        }
        return false
    }
}
