/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.symbol

/**
 * One row of a symbol set: the text a tap commits, and how far the caret has
 * to move back afterwards to land inside an opened pair.
 */
data class SymbolSpec(
    val text: String,
    /**
     * Characters the caret moves back after committing [text]. A bracket pair
     * commits both halves at once, so the caret has to walk back over the
     * closing half to end up between them.
     */
    val cursorBack: Int = 0,
)

/**
 * The two eight-entry sets the sidebar offers while nothing is being composed.
 *
 * Both are static: they spell out what the targeted theme already puts on the
 * leftmost column of its nine-key keyboards, so a tap on the sidebar commits
 * the same character the theme only reaches with a tap plus a long press.
 * [Punctuation] keeps the theme order — the four tap symbols first, then the
 * four long-press ones — and the bracket pairs are entered whole, with the
 * caret left between the two halves.
 */
object SymbolSets {
    /** Replaces the leftmost punctuation column of the nine-key main keyboard. */
    val Punctuation: List<SymbolSpec> =
        listOf(
            SymbolSpec("，"),
            SymbolSpec("。"),
            SymbolSpec("？"),
            SymbolSpec("！"),
            SymbolSpec("、"),
            SymbolSpec("——"),
            SymbolSpec("（）", cursorBack = 1),
            SymbolSpec("【】", cursorBack = 1),
        )

    /** Calculator symbols on the nine-key number keyboard. */
    val Operators: List<SymbolSpec> =
        listOf(
            SymbolSpec("+"),
            SymbolSpec("-"),
            SymbolSpec("*"),
            SymbolSpec("/"),
            SymbolSpec("="),
            SymbolSpec("_"),
            SymbolSpec("（）", cursorBack = 1),
            SymbolSpec("【】", cursorBack = 1),
        )
}
