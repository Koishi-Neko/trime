/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.sidebar

import androidx.recyclerview.widget.RecyclerView
import splitties.views.dsl.core.Ui

sealed class SidebarViewHolder(
    open val ui: Ui,
) : RecyclerView.ViewHolder(ui.root) {
    class Syllable(
        override val ui: SyllableItemUi,
    ) : SidebarViewHolder(ui)

    class Symbol(
        override val ui: SymbolItemUi,
    ) : SidebarViewHolder(ui)
}
