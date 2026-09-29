/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.sidebar

import com.osfans.trime.ime.candidates.syllable.SyllableCandidate
import com.osfans.trime.ime.candidates.symbol.SymbolKeyboardKind
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

private fun syllable(
    text: String,
    comment: String = "",
    globalIndex: Int = 0,
) = SyllableCandidate(text, comment, globalIndex)

class SidebarContentResolverTest :
    StringSpec({
        "syllable candidates take the sidebar over every symbol set" {
            val entries =
                SidebarContentResolver.resolve(
                    syllables = listOf(syllable("zhe", "zhe'43", 0), syllable("ge", "ge", 3)),
                    composing = true,
                    keyboardKind = SymbolKeyboardKind.PUNCTUATION,
                    symbolsEnabled = true,
                    keyboardOnScreen = true,
                )
            entries shouldBe
                listOf(
                    SidebarEntry.Syllable("zhe", "zhe'43", 0),
                    SidebarEntry.Syllable("ge", "ge", 3),
                )
        }

        "the nine-key main keyboard gets the punctuation set while nothing is composed" {
            val entries =
                SidebarContentResolver.resolve(
                    syllables = emptyList(),
                    composing = false,
                    keyboardKind = SymbolKeyboardKind.PUNCTUATION,
                    symbolsEnabled = true,
                    keyboardOnScreen = true,
                )
            entries.map { (it as SidebarEntry.Symbol).text } shouldBe
                listOf("，", "。", "？", "！", "、", "——", "（）", "【】")
            entries.last() shouldBe SidebarEntry.Symbol("【】", 1)
        }

        "a composition hides the punctuation set and lets the theme keys show" {
            SidebarContentResolver.resolve(
                syllables = emptyList(),
                composing = true,
                keyboardKind = SymbolKeyboardKind.PUNCTUATION,
                symbolsEnabled = true,
                keyboardOnScreen = true,
            ).shouldBeEmpty()
        }

        "the nine-key number keyboard gets the calculator set" {
            val entries =
                SidebarContentResolver.resolve(
                    syllables = emptyList(),
                    composing = false,
                    keyboardKind = SymbolKeyboardKind.OPERATORS,
                    symbolsEnabled = true,
                    keyboardOnScreen = true,
                )
            entries.map { (it as SidebarEntry.Symbol).text } shouldBe
                listOf("+", "-", "*", "/", "=", "_", "（）", "【】")
            entries.first() shouldBe SidebarEntry.Symbol("+", 0)
        }

        "the calculator set survives a composition" {
            SidebarContentResolver.resolve(
                syllables = emptyList(),
                composing = true,
                keyboardKind = SymbolKeyboardKind.OPERATORS,
                symbolsEnabled = true,
                keyboardOnScreen = true,
            ).size shouldBe 8
        }

        "any other keyboard never shows symbols" {
            listOf(false, true).forEach { composing ->
                SidebarContentResolver.resolve(
                    syllables = emptyList(),
                    composing = composing,
                    keyboardKind = SymbolKeyboardKind.NONE,
                    symbolsEnabled = true,
                    keyboardOnScreen = true,
                ).shouldBeEmpty()
            }
        }

        "any window other than the keyboard hides the sidebar" {
            // the liquid keyboard panel, a menu or the clipboard takes the
            // keyboard area over: syllables are hidden just like the symbols
            val syllables = listOf(syllable("zhe", "zhe'43", 0))
            SidebarContentResolver.resolve(
                syllables = syllables,
                composing = true,
                keyboardKind = SymbolKeyboardKind.PUNCTUATION,
                symbolsEnabled = true,
                keyboardOnScreen = false,
            ).shouldBeEmpty()
            SidebarContentResolver.resolve(
                syllables = emptyList(),
                composing = false,
                keyboardKind = SymbolKeyboardKind.OPERATORS,
                symbolsEnabled = true,
                keyboardOnScreen = false,
            ).shouldBeEmpty()
            // and the ordinary rules apply again as soon as it is back
            SidebarContentResolver.resolve(
                syllables = syllables,
                composing = true,
                keyboardKind = SymbolKeyboardKind.PUNCTUATION,
                symbolsEnabled = true,
                keyboardOnScreen = true,
            ) shouldBe listOf(SidebarEntry.Syllable("zhe", "zhe'43", 0))
        }

        "turning the switch off hides the symbols only" {
            val withoutSymbols =
                SidebarContentResolver.resolve(
                    syllables = emptyList(),
                    composing = false,
                    keyboardKind = SymbolKeyboardKind.PUNCTUATION,
                    symbolsEnabled = false,
                    keyboardOnScreen = true,
                )
            withoutSymbols.shouldBeEmpty()
            SidebarContentResolver.resolve(
                syllables = listOf(syllable("xie", "xie'6", 2)),
                composing = true,
                keyboardKind = SymbolKeyboardKind.PUNCTUATION,
                symbolsEnabled = false,
                keyboardOnScreen = true,
            ) shouldBe listOf(SidebarEntry.Syllable("xie", "xie'6", 2))
        }
    })
