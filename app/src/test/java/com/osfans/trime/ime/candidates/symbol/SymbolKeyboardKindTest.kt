/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.symbol

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class SymbolKeyboardKindTest :
    StringSpec({
        "classifies the nine-key main keyboards as punctuation" {
            SymbolKeyboardKind.of("luna_jiugong") shouldBe SymbolKeyboardKind.PUNCTUATION
            SymbolKeyboardKind.of("luna_bihua") shouldBe SymbolKeyboardKind.PUNCTUATION
            SymbolKeyboardKind.of("jiugong") shouldBe SymbolKeyboardKind.PUNCTUATION
            SymbolKeyboardKind.of("t9") shouldBe SymbolKeyboardKind.PUNCTUATION
            SymbolKeyboardKind.of("t9_stroke") shouldBe SymbolKeyboardKind.PUNCTUATION
        }

        "classifies the nine-key number keyboards as operators" {
            SymbolKeyboardKind.of("jiugong_number") shouldBe SymbolKeyboardKind.OPERATORS
            SymbolKeyboardKind.of("luna_jiugong_number") shouldBe SymbolKeyboardKind.OPERATORS
            SymbolKeyboardKind.of("t9_numpad") shouldBe SymbolKeyboardKind.OPERATORS
        }

        "never shows symbols on a 26-key keyboard that borrows a nine-key name" {
            // the theme names its latin keyboards after the layout they fall
            // back to, so a nine-key marker leaks into a 26-key id — on the
            // device that put the sidebar over the q/a/z column of the
            // English keyboard reached with the 中/En key
            SymbolKeyboardKind.of("default_jiugong_letter") shouldBe SymbolKeyboardKind.NONE
            SymbolKeyboardKind.of("default_symbol") shouldBe SymbolKeyboardKind.NONE
            SymbolKeyboardKind.of("default") shouldBe SymbolKeyboardKind.NONE
            SymbolKeyboardKind.of("default_qwerty") shouldBe SymbolKeyboardKind.NONE
        }

        "the alphabet exclusion wins over the nine-key markers" {
            SymbolKeyboardKind.of("letter_jiugong") shouldBe SymbolKeyboardKind.NONE
            SymbolKeyboardKind.of("jiugong_letter") shouldBe SymbolKeyboardKind.NONE
            SymbolKeyboardKind.of("qwerty_jiugong") shouldBe SymbolKeyboardKind.NONE
            SymbolKeyboardKind.of("t9_qwerty") shouldBe SymbolKeyboardKind.NONE
            SymbolKeyboardKind.of("jiugong_26") shouldBe SymbolKeyboardKind.NONE
        }

        "shows nothing on the 26-key and other keyboards" {
            SymbolKeyboardKind.of("default") shouldBe SymbolKeyboardKind.NONE
            SymbolKeyboardKind.of("qwerty") shouldBe SymbolKeyboardKind.NONE
            SymbolKeyboardKind.of("qwerty0") shouldBe SymbolKeyboardKind.NONE
            SymbolKeyboardKind.of("number") shouldBe SymbolKeyboardKind.NONE
            SymbolKeyboardKind.of("symbols") shouldBe SymbolKeyboardKind.NONE
            SymbolKeyboardKind.of("letter") shouldBe SymbolKeyboardKind.NONE
            SymbolKeyboardKind.of("skb_dvorak") shouldBe SymbolKeyboardKind.NONE
            SymbolKeyboardKind.of("") shouldBe SymbolKeyboardKind.NONE
        }

        "matches the theme id regardless of case" {
            SymbolKeyboardKind.of("LUNA_JIUGONG") shouldBe SymbolKeyboardKind.PUNCTUATION
            SymbolKeyboardKind.of("Jiugong_Number") shouldBe SymbolKeyboardKind.OPERATORS
        }
    })
