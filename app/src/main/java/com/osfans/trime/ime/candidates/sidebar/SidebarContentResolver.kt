/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.sidebar

import com.osfans.trime.ime.candidates.syllable.SyllableCandidate
import com.osfans.trime.ime.candidates.symbol.SymbolKeyboardKind
import com.osfans.trime.ime.candidates.symbol.SymbolSets

/**
 * Decides what the sidebar shows, in priority order. Nothing at all is shown
 * unless the keyboard window is the one on screen: the sidebar decorates the
 * keyboard, and another window (the liquid keyboard panel, a menu, the
 * clipboard, …) takes that area over completely.
 *
 * 1. syllable candidates — the behaviour that predates the symbol sets: while
 *    a T9 scheme has syllables to offer, they own the sidebar;
 * 2. on the nine-key main keyboard, while nothing is being composed, the
 *    punctuation set replacing the theme's leftmost column;
 * 3. on the nine-key number keyboard, the calculator set;
 * 4. nothing while a composition is up, because the leftmost column of the
 *    nine-key keyboard then holds function keys (分词/上页/下页/Esc) that the
 *    overlay must not cover — the theme's own keys show through instead;
 * 5. nothing on any other keyboard.
 *
 * Rule 3 is not gated on the composition state: the number keyboard commits
 * its digits directly, and its left column carries symbols and digits rather
 * than the function keys rule 4 protects.
 *
 * The decision is a pure function of its inputs, so it is testable without the
 * rime runtime, the preferences or a view hierarchy.
 */
object SidebarContentResolver {
    fun resolve(
        syllables: List<SyllableCandidate>,
        composing: Boolean,
        keyboardKind: SymbolKeyboardKind,
        symbolsEnabled: Boolean,
        keyboardOnScreen: Boolean,
    ): List<SidebarEntry> {
        // the keyboard area belongs to another window while this is false
        if (!keyboardOnScreen) return emptyList()
        if (syllables.isNotEmpty()) {
            return syllables.map { SidebarEntry.Syllable(it.text, it.comment, it.globalIndex) }
        }
        if (!symbolsEnabled) return emptyList()
        val symbols =
            when (keyboardKind) {
                SymbolKeyboardKind.NONE -> null
                SymbolKeyboardKind.PUNCTUATION -> if (composing) null else SymbolSets.Punctuation
                SymbolKeyboardKind.OPERATORS -> SymbolSets.Operators
            }
        return symbols.orEmpty().map { SidebarEntry.Symbol(it.text, it.cursorBack) }
    }
}
