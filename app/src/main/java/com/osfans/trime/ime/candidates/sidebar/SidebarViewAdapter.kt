/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.sidebar

import android.content.Context
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.chad.library.adapter4.BaseQuickAdapter
import com.osfans.trime.data.theme.ThemeScope
import splitties.dimensions.dp
import kotlin.math.max

class SidebarViewAdapter(
    val scope: ThemeScope,
) : BaseQuickAdapter<SidebarEntry, SidebarViewHolder>() {
    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long =
        items.getOrNull(position)?.hashCode()?.toLong() ?: RecyclerView.NO_ID

    /** Global index of the candidate rime currently highlights, or -1. */
    var highlightedIdx: Int = -1
        private set

    fun updateEntries(
        entries: List<SidebarEntry>,
        highlightedIndex: Int,
    ) {
        highlightedIdx = highlightedIndex
        super.submitList(entries, null)
    }

    override fun getItemViewType(
        position: Int,
        list: List<SidebarEntry>,
    ): Int = if (list.getOrNull(position) is SidebarEntry.Symbol) VIEW_TYPE_SYMBOL else VIEW_TYPE_SYLLABLE

    override fun onCreateViewHolder(
        context: Context,
        parent: ViewGroup,
        viewType: Int,
    ): SidebarViewHolder {
        val holder =
            if (viewType == VIEW_TYPE_SYMBOL) {
                SidebarViewHolder.Symbol(SymbolItemUi(context, scope))
            } else {
                SidebarViewHolder.Syllable(SyllableItemUi(context, scope))
            }
        // theme height with a finger-sized floor; the margins leave breathing
        // room between rows without shrinking the 52dp touch target
        val rowHeight = max(context.dp(scope.theme.style.candidateViewHeight), context.dp(MIN_ROW_HEIGHT_DP))
        val margin = context.dp(ROW_MARGIN_DP)
        holder.ui.root.layoutParams =
            RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                rowHeight,
            ).apply {
                topMargin = margin
                bottomMargin = margin
            }
        return holder
    }

    override fun onBindViewHolder(
        holder: SidebarViewHolder,
        position: Int,
        item: SidebarEntry?,
    ) {
        when (holder) {
            is SidebarViewHolder.Syllable -> {
                val syllable = item as? SidebarEntry.Syllable ?: return
                holder.ui.update(syllable, syllable.globalIndex == highlightedIdx)
            }

            is SidebarViewHolder.Symbol -> {
                val symbol = item as? SidebarEntry.Symbol ?: return
                holder.ui.update(symbol)
            }
        }
    }

    companion object {
        private const val VIEW_TYPE_SYLLABLE = 0
        private const val VIEW_TYPE_SYMBOL = 1

        /** Every row stays tappable even if the theme shrinks its candidates. */
        const val MIN_ROW_HEIGHT_DP = 52

        /** Vertical breathing room between two rows. */
        const val ROW_MARGIN_DP = 3
    }
}
