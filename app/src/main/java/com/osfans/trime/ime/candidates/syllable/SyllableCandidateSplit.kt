/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.syllable

import com.osfans.trime.core.CandidateProto

/**
 * A syllable candidate as the sidebar shows it, together with the position it
 * occupies in the whole rime candidate list.
 *
 * The position is the *global* index: it is the index the candidate would be
 * selected by from the candidate bar, and the one `selectCandidate` expects
 * with `global = true`. The sidebar cannot use its own row index, because the
 * syllables it shows are filtered out of the bar and the remaining candidates
 * shift.
 */
data class SyllableCandidate(
    val text: String,
    val comment: String,
    val globalIndex: Int,
)

/**
 * A candidate list seen through the syllable rules: [displayed] is what the
 * compact candidate bar still renders, [syllables] is what the sidebar takes
 * over, and [displayedGlobalIndices] maps a bar position back to its global
 * candidate index.
 *
 * When nothing was split out, [displayed] is the very array that was passed in
 * and [displayedGlobalIndices] is the identity mapping, so a caller that does
 * not filter behaves exactly as it did before.
 */
class SyllableCandidateSplit(
    val displayed: Array<CandidateProto>,
    val displayedGlobalIndices: IntArray,
    val syllables: List<SyllableCandidate>,
) {
    companion object {
        /** The list untouched, as if the feature did not exist. */
        fun identity(candidates: Array<CandidateProto>) = SyllableCandidateSplit(
            displayed = candidates,
            displayedGlobalIndices = IntArray(candidates.size) { it },
            syllables = emptyList(),
        )

        /** Splits [candidates] into what the bar keeps and what the sidebar takes. */
        fun of(
            candidates: Array<CandidateProto>,
            detector: SyllableCandidateDetector,
        ): SyllableCandidateSplit {
            val displayed = ArrayList<CandidateProto>(candidates.size)
            val displayedIndices = ArrayList<Int>(candidates.size)
            val syllables = ArrayList<SyllableCandidate>()
            candidates.forEachIndexed { index, candidate ->
                if (detector.isSyllable(candidate)) {
                    syllables.add(SyllableCandidate(candidate.text, candidate.comment, index))
                } else {
                    displayed.add(candidate)
                    displayedIndices.add(index)
                }
            }
            if (syllables.isEmpty()) return identity(candidates)
            return SyllableCandidateSplit(
                displayed = displayed.toTypedArray(),
                displayedGlobalIndices = displayedIndices.toIntArray(),
                syllables = syllables,
            )
        }
    }
}
