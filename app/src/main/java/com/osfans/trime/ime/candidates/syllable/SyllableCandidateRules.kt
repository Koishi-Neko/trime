/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.syllable

import com.osfans.trime.core.CandidateProto
import com.osfans.trime.data.prefs.AppPrefs

/**
 * Resolves the syllable rules from the current preferences and applies them to
 * a candidate list.
 *
 * The compact candidate bar and the syllable sidebar both consume the same
 * [com.osfans.trime.core.Candidates.Bulk] update and must agree on which
 * entries are syllables, so the classification lives here rather than being
 * duplicated — and possibly drifting — between the two views.
 *
 * The splitting itself is [SyllableCandidateSplit.of]; this object only adds
 * the preference lookups so that the rules stay unit-testable.
 */
object SyllableCandidateRules {
    private val prefs by lazy { AppPrefs.defaultInstance().candidates }

    private var cachedPatterns: Pair<String, String>? = null
    private var cachedDetector: SyllableCandidateDetector = SyllableCandidateDetector.Default

    /** Whether the sidebar is on. While off, nothing is ever split out. */
    val enabled: Boolean
        get() = prefs.syllableSidebar.getValue()

    private fun detector(): SyllableCandidateDetector {
        val patterns =
            prefs.syllableTextPattern.getValue() to prefs.syllableCommentPattern.getValue()
        if (patterns != cachedPatterns) {
            cachedDetector = SyllableCandidateDetector(
                textPattern = patterns.first.asPatternOr(SyllableCandidateDetector.DEFAULT_TEXT_PATTERN),
                commentPattern = patterns.second.asPatternOr(SyllableCandidateDetector.DEFAULT_COMMENT_PATTERN),
            )
            cachedPatterns = patterns
        }
        return cachedDetector
    }

    fun split(candidates: Array<CandidateProto>): SyllableCandidateSplit {
        if (!enabled) return SyllableCandidateSplit.identity(candidates)
        return SyllableCandidateSplit.of(candidates, detector())
    }

    /** A hand-edited pattern that does not compile falls back to the built-in one. */
    private fun String.asPatternOr(fallback: Regex): Regex =
        runCatching { Regex(this) }.getOrElse { fallback }
}
