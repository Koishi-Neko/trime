/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.syllable

import android.content.Context
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.chad.library.adapter4.BaseQuickAdapter
import com.osfans.trime.data.theme.ThemeScope
import splitties.dimensions.dp

class SyllableSidebarViewAdapter(
    val scope: ThemeScope,
) : BaseQuickAdapter<SyllableCandidate, SyllableSidebarViewHolder>() {
    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long =
        items.getOrNull(position)?.hashCode()?.toLong() ?: RecyclerView.NO_ID

    /** Global index of the candidate rime currently highlights, or -1. */
    var highlightedIdx: Int = -1
        private set

    fun updateCandidates(
        syllables: List<SyllableCandidate>,
        highlightedIndex: Int,
    ) {
        highlightedIdx = highlightedIndex
        super.submitList(syllables, null)
    }

    override fun onCreateViewHolder(
        context: Context,
        parent: ViewGroup,
        viewType: Int,
    ): SyllableSidebarViewHolder {
        val ui = SyllableSidebarItemUi(context, scope)
        ui.root.layoutParams =
            RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                context.dp(scope.theme.style.candidateViewHeight),
            )
        return SyllableSidebarViewHolder(ui)
    }

    override fun onBindViewHolder(
        holder: SyllableSidebarViewHolder,
        position: Int,
        item: SyllableCandidate?,
    ) {
        item ?: return
        holder.ui.update(item, item.globalIndex == highlightedIdx)
    }
}
