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
 * Matching therefore goes by markers in the id instead of exact names:
 *
 * - a nine-key marker (`jiugong`, `bihua`, `t9`, …) is required, so the
 *   26-key keyboards (`default`, `qwerty`, …) never get a sidebar and can
 *   never have their `q`/`a`/`z` column covered;
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
        private val NINE_KEY_MARKERS = listOf("jiugong", "bihua", "t9")
        private val NUMBER_MARKERS = listOf("number", "digit", "numpad")

        fun of(keyboardId: String): SymbolKeyboardKind {
            val id = keyboardId.lowercase()
            if (NINE_KEY_MARKERS.none(id::contains)) return NONE
            return if (NUMBER_MARKERS.any(id::contains)) OPERATORS else PUNCTUATION
        }
    }
}
