/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.sidebar

import android.content.Context
import android.graphics.Color
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.ThemeScope
import com.osfans.trime.ime.core.AutoScaleTextView
import com.osfans.trime.util.roundedRippleDrawable
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.above
import splitties.views.dsl.constraintlayout.below
import splitties.views.dsl.constraintlayout.bottomOfParent
import splitties.views.dsl.constraintlayout.centerHorizontally
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.view
import splitties.views.dsl.core.wrapContent
import splitties.views.gravityCenter
import splitties.views.horizontalPadding

/**
 * One syllable row of the sidebar: the syllable in the candidate text style,
 * its key sequence underneath in the comment style.
 *
 * The labels are sized for a sidebar, not for the candidate bar: the theme
 * defaults they would otherwise inherit (`candidateTextSize` 15sp,
 * `commentTextSize` 10sp) render too small inside the narrow 17% column,
 * so fixed, larger sizes are used instead. Colors and typefaces still come
 * from the theme keys.
 *
 * Both labels use [AutoScaleTextView] so a long syllable is squeezed into the
 * narrow column instead of being clipped.
 */
class SyllableItemUi(
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
            textSize = TEXT_SIZE_SP
            typeface = theme.fonts.candidate
            isSingleLine = true
            gravity = gravityCenter
            scaleMode = AutoScaleTextView.Mode.Proportional
        }

    private val comment =
        view(::AutoScaleTextView) {
            id = View.generateViewId()
            textSize = COMMENT_SIZE_SP
            typeface = theme.fonts.comment
            isSingleLine = true
            gravity = gravityCenter
            scaleMode = AutoScaleTextView.Mode.Proportional
        }

    override val root =
        constraintLayout {
            horizontalPadding = dp(theme.style.candidatePadding)
            // packed chain: the two labels sit together, centred vertically
            // in the (taller) row instead of hugging the top and the bottom
            add(
                comment,
                lParams(wrapContent, wrapContent) {
                    topOfParent()
                    above(text)
                    centerHorizontally()
                    bottomMargin = dp(LINE_GAP_DP)
                    verticalChainStyle = ConstraintLayout.LayoutParams.CHAIN_PACKED
                },
            )
            add(
                text,
                lParams(wrapContent, wrapContent) {
                    below(comment)
                    bottomOfParent()
                    centerHorizontally()
                },
            )
        }

    fun update(
        item: SidebarEntry.Syllable,
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

    companion object {
        /** Maximum size of the syllable label; longer ones get squeezed. */
        const val TEXT_SIZE_SP = 20f

        /** Size of the key-sequence label under the syllable. */
        const val COMMENT_SIZE_SP = 12f

        /** Gap between the key-sequence label and the syllable label. */
        const val LINE_GAP_DP = 2
    }
}
