/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.sidebar

import android.content.Context
import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
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

    /**
     * Height of one row in px, as the delegate reads it from the keyboard key
     * rows. Rows tile the covered column with no margins, so the eight symbols
     * show three at a time and the rest scrolls. Zero keeps the theme fallback,
     * for a list that is shown before the first keyboard is measured.
     */
    var rowHeight: Int = 0
        set(value) {
            if (field == value) return
            field = value
            notifyDataSetChanged()
        }

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
        holder.ui.root.layoutParams =
            RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                resolvedRowHeight(context),
            )
        return holder
    }

    override fun onBindViewHolder(
        holder: SidebarViewHolder,
        position: Int,
        item: SidebarEntry?,
    ) {
        // the geometry can change under a bound holder (another keyboard, a
        // scheme rebuild), so the row height follows the keyboard on every bind
        holder.ui.root.updateLayoutParams<RecyclerView.LayoutParams> {
            height = resolvedRowHeight(holder.ui.ctx)
        }
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

    /** Key row height once the keyboard is measured, the theme height before that. */
    private fun resolvedRowHeight(context: Context): Int =
        rowHeight.takeIf { it > 0 }
            ?: max(context.dp(scope.theme.style.candidateViewHeight), context.dp(MIN_ROW_HEIGHT_DP))

    companion object {
        private const val VIEW_TYPE_SYLLABLE = 0
        private const val VIEW_TYPE_SYMBOL = 1

        /** Fallback row height floor, used until the keyboard key rows are known. */
        const val MIN_ROW_HEIGHT_DP = 52
    }
}
