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
 * - [CandidateProto.comment] is that syllable, either followed by the key
 *   sequence it still covers (`zhe'43`, `xi'36`) or bare (`ge`, `gen`) when
 *   the syllable consumes the whole remaining key sequence — the lua
 *   translator emits the bare form for the last syllable of a chain, since
 *   there is no remainder to report. Either way the comment opens with the
 *   syllable itself: it repeats the text, or it continues it after an
 *   apostrophe. This structural rule holds for every real candidate the
 *   format produces and is what keeps English completions with a
 *   part-of-speech comment (`the`/`pron`) out.
 *
 * The text pattern and the structural rule have to pass for a candidate to
 * count as a syllable; the comment pattern then gates the character classes.
 * A candidate failing any of them is left alone, which keeps this from firing
 * on unrelated lists: Chinese words carry Han text, and English completions
 * carry no `syllable'keys` comment.
 *
 * The bare-comment form shares its shape with an English completion whose
 * comment repeats the word (`you`/`you`): such an entry is treated as a
 * syllable. In the target T9 scheme no dictionary candidate has that shape,
 * and a misclassified entry still selects correctly through its global
 * index, so only its placement (sidebar vs. bar) is affected.
 *
 * Both patterns are [Regex]es matched against the whole value and may be
 * replaced from the candidate settings, see [SyllableCandidateRules]. The
 * structural rule is part of the format, not of the patterns: custom
 * patterns refine the character classes (case, length, …), they cannot turn
 * a comment that does not open with the text into a syllable comment.
 */
class SyllableCandidateDetector(
    private val textPattern: Regex = DEFAULT_TEXT_PATTERN,
    private val commentPattern: Regex = DEFAULT_COMMENT_PATTERN,
) {
    fun isSyllable(candidate: CandidateProto): Boolean {
        if (!textPattern.matches(candidate.text)) return false
        // the comment is the writing the candidate fixes: it repeats the
        // syllable (bare form) or continues it with the remaining key
        // sequence after an apostrophe (xi'36)
        val comment = candidate.comment
        if (comment != candidate.text && !comment.startsWith(candidate.text + APOSTROPHE)) {
            return false
        }
        return commentPattern.matches(comment)
    }

    companion object {
        const val APOSTROPHE: String = "'"

        val DEFAULT_TEXT_PATTERN = Regex("[a-z]{1,6}")
        val DEFAULT_COMMENT_PATTERN = Regex("[a-z]+('[0-9']+)?")

        val Default = SyllableCandidateDetector()
    }
}
