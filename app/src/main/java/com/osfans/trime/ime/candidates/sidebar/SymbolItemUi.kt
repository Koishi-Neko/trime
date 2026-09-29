/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.sidebar

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
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
import timber.log.Timber

/**
 * One symbol row of the sidebar: a single glyph, or a bracket pair, centred in
 * a key-sized row.
 *
 * The row is styled as a keyboard key rather than as a candidate: the label
 * takes the theme key text color and key font, the press highlight is the
 * theme key highlight, and the corner radius is the theme key round corner.
 * Nothing is hard-coded, so a dark or light scheme restyles the column the way
 * it restyles the keys.
 *
 * Symbols never get a second line and never highlight as candidates, so they
 * are their own view type: the label is one [AutoScaleTextView] spanning the
 * row, and its size is set once, when the holder is created. That matters
 * because [AutoScaleTextView] caches its measured glyph bounds and only
 * re-measures when its text is replaced, not when the size changes; a size
 * that varied per bind would be drawn with stale metrics.
 *
 * The label is constrained to the row width, so a two-character pair that is
 * wider than the narrow column is squeezed instead of clipped.
 */
class SymbolItemUi(
    override val ctx: Context,
    private val scope: ThemeScope,
) : Ui {
    private val theme: Theme get() = scope.theme

    // a symbol row must not take the keyboard down for a colour or a font: the
    // fallbacks only apply while the theme scope has nothing to give
    private val textColor: Int get() = runCatching { scope.colors.keyTextColor }.getOrDefault(Color.WHITE)
    private val hlBackColor: Int get() = runCatching { scope.colors.hilitedKeyBackColor }.getOrDefault(Color.TRANSPARENT)

    /**
     * The theme key font, or the platform default when the theme asks for a
     * font the device does not have (resolving one reads the user data dir).
     */
    private fun keyTypeface(): Typeface =
        runCatching { theme.fonts.key }
            .onFailure { Timber.w(it, "Sidebar: cannot load the key font, using the default one") }
            .getOrDefault(Typeface.DEFAULT)

    private val symbol =
        view(::AutoScaleTextView) {
            id = View.generateViewId()
            textSize = theme.style.keyTextSize.takeIf { it > 0f } ?: SYMBOL_SIZE_SP
            typeface = keyTypeface()
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
        val cornerRadius = ctx.dp(theme.style.roundCorner).toFloat()
        root.background = roundedRippleDrawable(hlBackColor, cornerRadius, Color.TRANSPARENT)
        symbol.text = item.text
        symbol.setTextColor(textColor)
    }

    companion object {
        /** Glyph size when the theme carries no key text size. */
        const val SYMBOL_SIZE_SP = 24f
    }
}
