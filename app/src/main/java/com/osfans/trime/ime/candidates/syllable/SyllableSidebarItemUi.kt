/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.syllable

import android.content.Context
import android.graphics.Color
import android.view.View
import androidx.core.view.isVisible
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.ThemeScope
import com.osfans.trime.ime.core.AutoScaleTextView
import com.osfans.trime.util.roundedRippleDrawable
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.below
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
 * One row of the syllable sidebar: the syllable in the candidate text style,
 * its key sequence underneath in the comment style.
 *
 * Both labels use [AutoScaleTextView] so a long syllable is squeezed into the
 * narrow column instead of being clipped.
 */
class SyllableSidebarItemUi(
    override val ctx: Context,
    private val scope: ThemeScope,
) : Ui {
    private val theme: Theme get() = scope.theme

    // read at bind time so a scheme switch restyles already created rows
    private val textColor: Int get() = scope.colors.candidateTextColor
    private val commentColor: Int get() = scope.colors.commentTextColor
    private val hlTextColor: Int get() = scope.colors.hilitedCandidateTextColor
    private val hlCommentColor: Int get() = scope.colors.hilitedCommentTextColor
    private val hlBackColor: Int get() = scope.colors.hilitedCandidateBackColor

    private val text =
        view(::AutoScaleTextView) {
            id = View.generateViewId()
            textSize = theme.style.candidateTextSize
            typeface = theme.fonts.candidate
            isSingleLine = true
            gravity = gravityCenter
            scaleMode = AutoScaleTextView.Mode.Proportional
        }

    private val comment =
        view(::AutoScaleTextView) {
            id = View.generateViewId()
            textSize = theme.style.commentTextSize
            typeface = theme.fonts.comment
            isSingleLine = true
            gravity = gravityCenter
            scaleMode = AutoScaleTextView.Mode.Proportional
        }

    override val root =
        constraintLayout {
            horizontalPadding = dp(theme.style.candidatePadding)
            add(
                comment,
                lParams(wrapContent, wrapContent) {
                    topOfParent()
                    centerHorizontally()
                },
            )
            add(
                text,
                lParams(wrapContent, matchConstraints) {
                    below(comment)
                    bottomOfParent()
                    centerHorizontally()
                },
            )
        }

    fun update(
        item: SyllableCandidate,
        highlighted: Boolean,
    ) {
        val cornerRadius = ctx.dp(theme.style.candidateCornerRadius)
        root.background =
            roundedRippleDrawable(
                hlBackColor,
                cornerRadius,
                if (highlighted) hlBackColor else Color.TRANSPARENT,
            )
        text.text = item.text
        text.setTextColor(if (highlighted) hlTextColor else textColor)
        comment.text = item.comment
        comment.setTextColor(if (highlighted) hlCommentColor else commentColor)
        comment.isVisible = item.comment.isNotEmpty()
    }
}
