/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.sidebar

import android.content.Context
import android.graphics.Color
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.ThemeScope
import com.osfans.trime.ime.core.AutoScaleTextView
import com.osfans.trime.util.roundedRippleDrawable
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.bottomOfParent
import splitties.views.dsl.constraintlayout.centerHorizontally
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.matchConstraints
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.view
import splitties.views.dsl.core.wrapContent
import splitties.views.gravityCenter
import splitties.views.horizontalPadding

/**
 * One symbol row of the sidebar: a single large glyph (or a bracket pair),
 * centred in the row.
 *
 * Symbols never get a second line and never highlight, so they are their own
 * view type — the label is one [AutoScaleTextView] spanning the row and the
 * font size is set once, when the holder is created. That matters because
 * [AutoScaleTextView] caches its measured glyph bounds and only re-measures
 * when its text is replaced, not when the size changes; a size that varied
 * per bind would be drawn with stale metrics.
 *
 * The label is constrained to the row width, so a two-character pair that is
 * wider than the narrow column is squeezed instead of clipped.
 */
class SymbolItemUi(
    override val ctx: Context,
    private val scope: ThemeScope,
) : Ui {
    private val theme: Theme get() = scope.theme

    private val textColor: Int get() = scope.colors.candidateTextColor
    private val hlBackColor: Int get() = scope.colors.hilitedCandidateBackColor

    private val symbol =
        view(::AutoScaleTextView) {
            id = View.generateViewId()
            textSize = SYMBOL_SIZE_SP
            typeface = theme.fonts.candidate
            isSingleLine = true
            gravity = gravityCenter
            scaleMode = AutoScaleTextView.Mode.Proportional
        }

    override val root =
        constraintLayout {
            horizontalPadding = dp(theme.style.candidatePadding)
            add(
                symbol,
                lParams(matchConstraints, wrapContent) {
                    topOfParent()
                    bottomOfParent()
                    centerHorizontally()
                },
            )
        }

    fun update(item: SidebarEntry.Symbol) {
        val cornerRadius = ctx.dp(theme.style.candidateCornerRadius)
        root.background = roundedRippleDrawable(hlBackColor, cornerRadius, Color.TRANSPARENT)
        symbol.text = item.text
        symbol.setTextColor(textColor)
    }

    companion object {
        /** Size of the glyph; the row is single-line, so it can be large. */
        const val SYMBOL_SIZE_SP = 24f
    }
}
