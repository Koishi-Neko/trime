/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.symbol

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

class SymbolSetsTest :
    StringSpec({
        "the punctuation set keeps the theme's tap and long-press order" {
            SymbolSets.Punctuation.map { it.text } shouldContainExactly
                listOf("，", "。", "？", "！", "、", "——", "（）", "【】")
        }

        "the calculator set offers the arithmetic symbols and both pairs" {
            SymbolSets.Operators.map { it.text } shouldContainExactly
                listOf("+", "-", "*", "/", "=", "_", "（）", "【】")
        }

        "both sets hold eight entries and move the caret only inside the pairs" {
            listOf(SymbolSets.Punctuation, SymbolSets.Operators).forEach { set ->
                set.size shouldBe 8
                set.filter { it.cursorBack != 0 }.map { it.text } shouldContainExactly listOf("（）", "【】")
                set.single { it.text == "（）" }.cursorBack shouldBe 1
                set.single { it.text == "【】" }.cursorBack shouldBe 1
            }
        }

        "the single symbols are committed as they are" {
            SymbolSets.Operators.filter { it.text.length == 1 }.forEach { it.cursorBack shouldBe 0 }
            SymbolSets.Punctuation.first().cursorBack shouldBe 0
        }
    })
