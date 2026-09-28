/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.syllable

import com.osfans.trime.core.CandidateProto
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

private fun candidate(
    text: String,
    comment: String = "",
) = CandidateProto(text, comment, "")

class SyllableCandidateDetectorTest :
    StringSpec({
        val detector = SyllableCandidateDetector.Default

        "recognises a syllable followed by its key sequence" {
            detector.isSyllable(candidate("zhe", "zhe'43")) shouldBe true
            detector.isSyllable(candidate("xie", "xie'43")) shouldBe true
            detector.isSyllable(candidate("zhei", "zhei'3")) shouldBe true
            detector.isSyllable(candidate("a", "a'1")) shouldBe true
            detector.isSyllable(candidate("abcdef", "abcdef'1")) shouldBe true
        }

        "rejects text that is not 1-6 plain lowercase letters" {
            detector.isSyllable(candidate("abcdefg", "abcdefg'1")) shouldBe false
            detector.isSyllable(candidate("", "'1")) shouldBe false
            detector.isSyllable(candidate("Zhe", "Zhe'43")) shouldBe false
            detector.isSyllable(candidate("zhe1", "zhe1'43")) shouldBe false
            detector.isSyllable(candidate("这", "zhe'43")) shouldBe false
        }

        "rejects a comment that is not syllable'keystrokes" {
            // English completions carry letters in `text` but no key sequence
            detector.isSyllable(candidate("the")) shouldBe false
            detector.isSyllable(candidate("you", "you")) shouldBe false
            detector.isSyllable(candidate("the", "pron")) shouldBe false
            detector.isSyllable(candidate("zhe", "zhe'")) shouldBe false
            detector.isSyllable(candidate("zhe", "'43")) shouldBe false
            detector.isSyllable(candidate("zhe", "ZHE'43")) shouldBe false
        }

        "rejects a Han candidate with a key sequence comment" {
            detector.isSyllable(candidate("这", "'43")) shouldBe false
            detector.isSyllable(candidate("这个", "zhe'43")) shouldBe false
        }

        "honours custom patterns" {
            val custom =
                SyllableCandidateDetector(
                    textPattern = Regex("[a-z]+"),
                    commentPattern = Regex("[a-z]+:[0-9]+"),
                )
            custom.isSyllable(candidate("abcdefg", "abcdefg:43")) shouldBe true
            custom.isSyllable(candidate("zhe", "zhe'43")) shouldBe false
        }
    })

class SyllableCandidateSplitTest :
    StringSpec({
        val detector = SyllableCandidateDetector.Default

        "keeps the original list untouched when there is no syllable" {
            val all = arrayOf(candidate("这", "zhe'43"), candidate("the"), candidate("着"))
            val split = SyllableCandidateSplit.of(all, detector)
            // identity: the very same array, so an unfiltered caller is unaffected
            split.displayed shouldBe all
            split.displayedGlobalIndices.toList() shouldContainExactly listOf(0, 1, 2)
            split.syllables shouldBe emptyList()
        }

        "filters syllables out of the bar and keeps their global indices" {
            val all =
                arrayOf(
                    candidate("zhe", "zhe'43"),
                    candidate("这", "zhe'43"),
                    candidate("着", "zhe'43"),
                    candidate("xie", "xie'43"),
                    candidate("些", "xie'43"),
                )
            val split = SyllableCandidateSplit.of(all, detector)

            split.displayed.map { it.text } shouldContainExactly listOf("这", "着", "些")
            split.displayedGlobalIndices.toList() shouldContainExactly listOf(1, 2, 4)
            split.syllables.map { it.text } shouldContainExactly listOf("zhe", "xie")
            split.syllables.map { it.globalIndex } shouldContainExactly listOf(0, 3)
            split.syllables.map { it.comment } shouldContainExactly listOf("zhe'43", "xie'43")
        }

        "copes with a list that is empty" {
            val split = SyllableCandidateSplit.of(arrayOf(), detector)
            split.displayed.toList() shouldBe emptyList()
            split.displayedGlobalIndices.toList() shouldBe emptyList()
            split.syllables shouldBe emptyList()
        }
    })
