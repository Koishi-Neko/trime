/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.symbol

/**
 * Which symbol set a keyboard gets, if any.
 *
 * The sidebar covers the leftmost column of the keyboard, so it may only be
 * shown on layouts whose left column carries symbols: the nine-key (九宫格)
 * keyboards. Which layout a keyboard is comes from its theme id — the
 * `preset_keyboards` key of the theme yaml, the only layout information the
 * runtime carries, and theme data rather than a fixed enum.
 *
 * Matching goes by markers in the id instead of exact names, and the 26-key
 * family is excluded first, before any nine-key marker is looked at:
 *
 * - an alphabet marker (`letter`, `qwerty`, `26`) or an id starting with
 *   `default` is a 26-key layout and never gets a sidebar, so its `q`/`a`/`z`
 *   column can never be covered. This one has to win over the nine-key
 *   markers: the theme names its latin keyboards after the layout they fall
 *   back to, which makes `default_jiugong_letter` carry a nine-key marker
 *   while being a 26-key keyboard;
 * - otherwise a nine-key marker (`jiugong`, `bihua`, `t9`, …) is required;
 * - inside the nine-key family, a number marker (`number`, `digit`, …)
 *   selects the calculator set, anything else the punctuation set.
 *
 * A theme naming its nine-key keyboards differently needs the markers
 * extended; the id actually in use is logged by the sidebar, see SIDEBAR.md.
 */
enum class SymbolKeyboardKind {
    /** Not a nine-key keyboard: the sidebar never shows symbols on it. */
    NONE,

    /** Nine-key main keyboard: the punctuation set. */
    PUNCTUATION,

    /** Nine-key number keyboard: the calculator set. */
    OPERATORS,
    ;

    companion object {
        /** Latin layouts, which the sidebar must never cover. */
        private val ALPHABET_MARKERS = listOf("letter", "qwerty", "26")

        /** Latin layouts the theme names after the keyboard they fall back to. */
        private const val ALPHABET_PREFIX = "default"

        private val NINE_KEY_MARKERS = listOf("jiugong", "bihua", "t9")
        private val NUMBER_MARKERS = listOf("number", "digit", "numpad")

        fun of(keyboardId: String): SymbolKeyboardKind {
            val id = keyboardId.lowercase()
            if (isAlphabet(id)) return NONE
            if (NINE_KEY_MARKERS.none(id::contains)) return NONE
            return if (NUMBER_MARKERS.any(id::contains)) OPERATORS else PUNCTUATION
        }

        private fun isAlphabet(id: String): Boolean =
            id.startsWith(ALPHABET_PREFIX) || ALPHABET_MARKERS.any(id::contains)
    }
}
