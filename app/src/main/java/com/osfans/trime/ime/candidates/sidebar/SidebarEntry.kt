/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.sidebar

/**
 * One row of the sidebar.
 *
 * The sidebar renders two unrelated kinds of content in the same view — the
 * syllable candidates the compact bar filtered out, and the symbol sets that
 * replace the theme's leftmost symbol column — so its rows are typed on the
 * union rather than on candidates alone, and the adapter picks a view type per
 * kind instead of rebinding one row layout to both.
 */
sealed interface SidebarEntry {
    /** Main label of the row. */
    val label: String

    /** Second line of the row; empty for the single-line ones. */
    val subLabel: String

    data class Syllable(
        val text: String,
        val comment: String,
        /** Position of this candidate in the whole rime candidate list. */
        val globalIndex: Int,
    ) : SidebarEntry {
        override val label: String get() = text

        override val subLabel: String get() = comment
    }

    data class Symbol(
        val text: String,
        /** Characters the caret walks back after committing [text]. */
        val cursorBack: Int,
    ) : SidebarEntry {
        override val label: String get() = text

        override val subLabel: String get() = ""
    }
}
