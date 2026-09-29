/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.sidebar

/**
 * One key as the sidebar geometry needs it: its frame in keyboard coordinates
 * (px), and the row it belongs to.
 */
data class SidebarKeyBounds(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val row: Int,
)

/**
 * The rectangle the sidebar covers, in the coordinates of the keyboard area —
 * the same origin the keys use — together with the number of key rows it
 * spans, so the rows can be tiled inside it.
 */
data class SidebarBounds(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
    val rows: Int,
) {
    /** Height of one covered row; the sidebar tiles its rows without gaps. */
    val rowHeight: Int get() = if (rows > 0) height / rows else height
}

/**
 * Geometry of the column the sidebar overlays.
 *
 * The sidebar replaces the leftmost key column of the keyboard, so it takes
 * the bounds of exactly that column: the leftmost key of every row except the
 * last one. The bottom row stays uncovered, which is what keeps the 「符」 key
 * the theme puts in its left corner reachable while the sidebar is up.
 *
 * The bounds are the *drawn* bounds of those keys, i.e. inset by half of the
 * keyboard gaps, because a key view keeps half a gap as padding on each side.
 * Insetting here means the caller does not need to know the theme gaps, and
 * the column comes out exactly as wide as the key it covers — a key of 15.5%
 * of the keyboard stays 15.5% minus one horizontal gap.
 *
 * Everything is plain arithmetic on the key frames, so it is unit-testable
 * without a keyboard, a theme or a view.
 */
object SidebarGeometry {
    fun column(
        keys: List<SidebarKeyBounds>,
        horizontalGap: Int,
        verticalGap: Int,
    ): SidebarBounds? {
        if (keys.isEmpty()) return null
        val rows = keys.map { it.row }.distinct().sorted()
        // the bottom row has to stay free, so a single-row keyboard has no column
        if (rows.size < 2) return null
        val coveredRows = rows.dropLast(1).toSet()
        val coveredKeys = keys.filter { it.row in coveredRows }
        if (coveredKeys.isEmpty()) return null

        val columnX = coveredKeys.minOf { it.x }
        val columnKeys = coveredKeys.filter { it.x == columnX }
        val left = columnX + horizontalGap / 2
        val right = columnKeys.maxOf { it.x + it.width } - horizontalGap / 2
        val top = coveredKeys.minOf { it.y } + verticalGap / 2
        val bottom = coveredKeys.maxOf { it.y + it.height } - verticalGap / 2
        val width = right - left
        val height = bottom - top
        if (width <= 0 || height <= 0) return null
        return SidebarBounds(
            left = left,
            top = top,
            width = width,
            height = height,
            rows = coveredRows.size,
        )
    }
}
