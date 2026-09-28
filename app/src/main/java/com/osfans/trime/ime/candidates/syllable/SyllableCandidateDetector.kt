/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.syllable

import com.osfans.trime.core.CandidateProto

/**
 * Recognises the "syllable candidates" that T9-style schemes prepend to the
 * candidate list.
 *
 * librime exposes no candidate type through JNI, so the two kinds of entry are
 * told apart by shape rather than by metadata:
 *
 * - [CandidateProto.text] is 1-6 plain lowercase ASCII letters, eg. `zhe`
 * - [CandidateProto.comment] is that syllable followed by the key sequence it
 *   covers, eg. `zhe'43` or `zhei'3`
 *
 * Both patterns have to match for a candidate to count as a syllable; a
 * candidate matching only one of them is left alone. That is what keeps this
 * from firing on unrelated lists: Chinese words carry Han text, and English
 * completions carry no `keys'keys` comment.
 *
 * Both patterns are [Regex]es matched against the whole value and may be
 * replaced from the candidate settings, see [SyllableCandidateRules].
 */
class SyllableCandidateDetector(
    private val textPattern: Regex = DEFAULT_TEXT_PATTERN,
    private val commentPattern: Regex = DEFAULT_COMMENT_PATTERN,
) {
    fun isSyllable(candidate: CandidateProto): Boolean =
        textPattern.matches(candidate.text) && commentPattern.matches(candidate.comment)

    companion object {
        val DEFAULT_TEXT_PATTERN = Regex("[a-z]{1,6}")
        val DEFAULT_COMMENT_PATTERN = Regex("[a-z]+'[0-9']+")

        val Default = SyllableCandidateDetector()
    }
}
