/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.syllable

import android.os.Build
import android.view.ContextThemeWrapper
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.osfans.trime.R
import com.osfans.trime.core.Candidates
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.daemon.launchOnReady
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.ThemeScope
import com.osfans.trime.ime.broadcast.InputBroadcastReceiver
import splitties.dimensions.dp
import splitties.views.dsl.recyclerview.recyclerView
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.instance

/**
 * The vertical list of syllable candidates, floating over the leftmost
 * column of the keyboard (where the theme keeps its punctuation keys),
 * under the candidate bar.
 *
 * It is a plain sibling of the candidate bar: both react to the same
 * [Candidates.Bulk] update and split it with [SyllableCandidateRules], so the
 * syllables shown here are exactly the ones the bar drops. Selecting a row
 * goes through the same `selectCandidate` call the bar uses, with the
 * candidate's global index.
 *
 * The view overlays the keyboard rather than taking a column next to it:
 * the keyboard keeps the exact bounds it has without the sidebar, and the
 * sidebar is added after it so it draws on top and takes the touches in its
 * bounds (its background is opaque, hiding the punctuation keys underneath).
 * The view is `GONE` whenever there is nothing to show, which lets the
 * touches reach the punctuation keys again.
 */
class SyllableSidebarDelegate(override val di: DI) :
    DIAware,
    InputBroadcastReceiver {
    private val context: ContextThemeWrapper by instance()
    private val rime: RimeSession by instance()
    private val scope: ThemeScope by instance()

    private val theme: Theme get() = scope.theme

    private val adapter =
        SyllableSidebarViewAdapter(scope).apply {
            setOnItemClickListener { _, _, position ->
                val item = items.getOrNull(position) ?: return@setOnItemClickListener
                rime.launchOnReady { it.selectCandidate(item.globalIndex, global = true) }
            }
        }

    val view: RecyclerView by lazy {
        context.recyclerView(R.id.syllable_sidebar_view) {
            visibility = View.GONE
            itemAnimator = null
            adapter = this@SyllableSidebarDelegate.adapter
            layoutManager = LinearLayoutManager(context)
            isFocusable = false
            isFocusableInTouchMode = false
            // keep touches in the column from falling through to the
            // punctuation keys underneath
            isClickable = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                defaultFocusHighlightEnabled = false
            }
            applyBackground(this)
        }
    }

    /**
     * Restyles after a scheme switch. Rows re-read their colors on the next
     * bind, so a plain refresh is enough to repaint the visible ones.
     */
    fun refreshColors() {
        applyBackground(view)
        adapter.notifyDataSetChanged()
    }

    private fun applyBackground(target: RecyclerView) {
        target.background =
            scope.decorDrawable(
                "candidate_background",
                "candidate_border_color",
                context.dp(theme.style.candidateBorder),
                context.dp(theme.style.candidateBorderRound),
            )
    }

    override fun onCandidateListUpdate(data: Candidates.Bulk) {
        val syllables = SyllableCandidateRules.split(data.candidates).syllables
        adapter.updateCandidates(syllables, data.highlighted)
        view.visibility = if (syllables.isNotEmpty()) View.VISIBLE else View.GONE
    }
}
