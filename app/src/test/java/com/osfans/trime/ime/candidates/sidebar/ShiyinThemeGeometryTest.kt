/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.sidebar

import com.charleskorn.kaml.YamlMap
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.ThemeTestSupport
import com.osfans.trime.ime.candidates.symbol.SymbolKeyboardKind
import com.osfans.trime.util.mapping
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import java.io.File

/**
 * The theme the sidebar is built for, as it is deployed on the device. It lives
 * outside the repository (it is the T9 theme under development), so the cases
 * that need it are skipped when it is not there; the synthetic grids in
 * [SidebarGeometryTest] cover the same code without a fixture.
 */
private val THEME_FILE =
    File(System.getenv("SHIYIN_THEME") ?: "/mnt/c/Users/15263/Desktop/知识信息库/rime-t9-shiyin/theme/shiyin.trime.yaml")

/** Target screen and keyboard size in px: 1080x2712 at 2.75x, keyboard_height 250dp. */
private const val KEYBOARD_WIDTH_PX = 1080
private const val KEYBOARD_HEIGHT_PX = 688

/** Where a row ends: the key width weights of a keyboard row add up to 100. */
private const val ROW_WEIGHT_TOTAL = 100f

/**
 * Decodes the fixture the way the deployed artifact is read: the DSL subset is
 * not expanded (this theme patches by librime path, which the expander refuses
 * on purpose), so the raw mapping is decoded directly.
 */
private fun decodeThemeFile(file: File): Theme {
    val mapping = ThemeTestSupport.yaml.parseToYamlNode(file.readText()).mapping
    return ThemeTestSupport.yaml.decodeFromYamlNode<Theme>(mapping as YamlMap)
}

/**
 * The key frames [com.osfans.trime.ime.keyboard.Keyboard] would compute for
 * [keyboardId]: keys run left to right in row order, a row ends once its width
 * weights reach [ROW_WEIGHT_TOTAL], rows share the keyboard height evenly.
 */
private fun keyFrames(
    theme: Theme,
    keyboardId: String,
): List<SidebarKeyBounds> {
    val config = theme.presetKeyboards.getValue(keyboardId)
    val defaultWeight = config.width.takeIf { it > 0f } ?: ROW_WEIGHT_TOTAL
    val rows = mutableListOf<MutableList<Float>>()
    var current = mutableListOf<Float>()
    var weight = 0f
    config.keys.forEach { key ->
        val keyWeight = key.width.takeIf { it > 0f } ?: defaultWeight
        current.add(keyWeight)
        weight += keyWeight
        if (weight >= ROW_WEIGHT_TOTAL - 0.5f) {
            rows.add(current)
            current = mutableListOf()
            weight = 0f
        }
    }
    if (current.isNotEmpty()) rows.add(current)
    // a keyboard may declare no keys at all
    if (rows.isEmpty()) return emptyList()

    val rowHeight = KEYBOARD_HEIGHT_PX / rows.size
    return rows.flatMapIndexed { row, weights ->
        var x = 0
        weights.map { keyWeight ->
            val width = (keyWeight * KEYBOARD_WIDTH_PX / ROW_WEIGHT_TOTAL).toInt()
            SidebarKeyBounds(x = x, y = row * rowHeight, width = width, height = rowHeight, row = row).also {
                x += width
            }
        }
    }
}

class ShiyinThemeGeometryTest :
    StringSpec({
        if (!THEME_FILE.isFile) {
            "shipped theme (skipped, fixture not found)" {
                println("ShiyinThemeGeometryTest: no fixture at $THEME_FILE")
            }
        } else {
            val theme by lazy { decodeThemeFile(THEME_FILE) }

            "decodes the shipped theme" {
                (theme.name.isNotEmpty()) shouldBe true
                theme.presetKeyboards.keys shouldContainAll listOf("luna_jiugong", "jiugong_number")
            }

            "the nine-key keyboards are the ones the symbol sets belong to" {
                SymbolKeyboardKind.of("luna_jiugong") shouldBe SymbolKeyboardKind.PUNCTUATION
                SymbolKeyboardKind.of("jiugong_number") shouldBe SymbolKeyboardKind.OPERATORS
            }

            "luna_jiugong gives the sidebar its left punctuation column, bottom row free" {
                val config = theme.presetKeyboards.getValue("luna_jiugong")
                val frames = keyFrames(theme, "luna_jiugong")
                frames.map { it.row }.distinct().size shouldBe 4

                val bounds =
                    requireNotNull(SidebarGeometry.column(frames, config.horizontalGap, config.verticalGap)) {
                        "luna_jiugong should yield a covered column"
                    }

                val leftColumnWidth = (15.5f * KEYBOARD_WIDTH_PX / ROW_WEIGHT_TOTAL).toInt()
                val rowHeight = KEYBOARD_HEIGHT_PX / 4
                bounds.left shouldBe config.horizontalGap / 2
                bounds.width shouldBe leftColumnWidth - config.horizontalGap
                bounds.top shouldBe config.verticalGap / 2
                bounds.height shouldBe 3 * rowHeight - config.verticalGap
                bounds.rows shouldBe 3
                bounds.rowHeight shouldBe bounds.height / 3

                // the column stops above the bottom row, where the theme puts 「符」
                val bottomRowTop = frames.filter { it.row == 3 }.minOf { it.y }
                (bounds.top + bounds.height <= bottomRowTop) shouldBe true
            }

            "every keyboard of the theme yields bounds or nothing, never an exception" {
                theme.presetKeyboards.forEach { (id, config) ->
                    val frames = keyFrames(theme, id)
                    if (frames.isEmpty()) return@forEach
                    val bounds = SidebarGeometry.column(frames, config.horizontalGap, config.verticalGap)
                    if (bounds != null) {
                        (bounds.width > 0) shouldBe true
                        (bounds.height > 0) shouldBe true
                        (bounds.rows >= 1) shouldBe true
                        (bounds.rows < frames.map { it.row }.distinct().size) shouldBe true
                    }
                    println("ShiyinThemeGeometryTest: $id -> $bounds")
                }
            }

            "the keyboards without a column fall back instead of throwing" {
                val config = theme.presetKeyboards.getValue("luna_jiugong")
                val frames = keyFrames(theme, "luna_jiugong")

                SidebarGeometry.column(emptyList(), config.horizontalGap, config.verticalGap) shouldBe null
                SidebarGeometry.column(
                    frames.filter { it.row == 0 },
                    config.horizontalGap,
                    config.verticalGap,
                ) shouldBe null
                SidebarGeometry.column(
                    frames.map { it.copy(width = 0, height = 0) },
                    config.horizontalGap,
                    config.verticalGap,
                ) shouldBe null
            }
        }
    })
