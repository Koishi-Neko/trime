/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.sidebar

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

private fun gridOf(
    rows: Int = 4,
    columns: Int = 4,
    keyWidth: Int = 200,
    keyHeight: Int = 60,
    lastRowHeight: Int = keyHeight,
): List<SidebarKeyBounds> =
    buildList {
        for (row in 0 until rows) {
            val height = if (row == rows - 1) lastRowHeight else keyHeight
            val y = row * keyHeight
            for (column in 0 until columns) {
                add(SidebarKeyBounds(x = column * keyWidth, y = y, width = keyWidth, height = height, row = row))
            }
        }
    }

class SidebarGeometryTest :
    StringSpec({
        "covers every key row but the bottom one" {
            val bounds = SidebarGeometry.column(gridOf(), horizontalGap = 6, verticalGap = 12)

            bounds shouldBe SidebarBounds(left = 3, top = 6, width = 194, height = 168, rows = 3)
        }

        "leaves the bottom row free for the theme's own key" {
            val keys = gridOf()
            val bounds = SidebarGeometry.column(keys, horizontalGap = 6, verticalGap = 12)!!

            val bottomRowTop = keys.filter { it.row == 3 }.minOf { it.y }
            (bounds.top + bounds.height) shouldBe (bottomRowTop - 12 / 2)
        }

        "follows the theme gaps, down to none at all" {
            SidebarGeometry.column(gridOf(), horizontalGap = 0, verticalGap = 0) shouldBe
                SidebarBounds(left = 0, top = 0, width = 200, height = 180, rows = 3)
        }

        "takes the widest key of the left column" {
            val keys =
                gridOf().map {
                    if (it.row == 1 && it.x == 0) it.copy(width = 300) else it
                }

            val bounds = SidebarGeometry.column(keys, horizontalGap = 0, verticalGap = 0)!!

            bounds.left shouldBe 0
            bounds.width shouldBe 300
        }

        "follows the left column when it does not start at zero" {
            val keys = gridOf().map { it.copy(x = it.x + 30) }

            val bounds = SidebarGeometry.column(keys, horizontalGap = 0, verticalGap = 0)!!

            bounds.left shouldBe 30
            bounds.width shouldBe 200
        }

        "keeps the bottom row's extra height out of the column" {
            val bounds =
                SidebarGeometry.column(
                    gridOf(lastRowHeight = 90),
                    horizontalGap = 0,
                    verticalGap = 0,
                )!!

            bounds.height shouldBe 180
            bounds.rows shouldBe 3
        }

        "splits the column into equal rows for the list" {
            val bounds = SidebarGeometry.column(gridOf(), horizontalGap = 6, verticalGap = 12)!!

            bounds.rowHeight shouldBe 56
        }

        "has no column when there is no keyboard to read" {
            SidebarGeometry.column(emptyList(), horizontalGap = 6, verticalGap = 12) shouldBe null
        }

        "has no column when the bottom row cannot be spared" {
            SidebarGeometry.column(gridOf(rows = 1), horizontalGap = 6, verticalGap = 12) shouldBe null
        }

        "has no column when the gaps swallow the keys" {
            SidebarGeometry.column(
                gridOf(keyWidth = 6),
                horizontalGap = 6,
                verticalGap = 12,
            ) shouldBe null
            SidebarGeometry.column(
                gridOf(keyHeight = 10),
                horizontalGap = 0,
                verticalGap = 30,
            ) shouldBe null
        }
    })
