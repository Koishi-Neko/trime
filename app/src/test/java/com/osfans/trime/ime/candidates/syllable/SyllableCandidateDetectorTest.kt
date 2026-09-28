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

        "recognises a syllable that consumes the whole remaining key sequence" {
            // the lua translator emits a bare comment when there is no
            // remainder to report (ge/he after zhe'43, gen/hen for wo'436,
            // zhen for wo'9436) — verified against the real package
            detector.isSyllable(candidate("ge", "ge")) shouldBe true
            detector.isSyllable(candidate("he", "he")) shouldBe true
            detector.isSyllable(candidate("gen", "gen")) shouldBe true
            detector.isSyllable(candidate("zhen", "zhen")) shouldBe true
            detector.isSyllable(candidate("die", "die")) shouldBe true
        }

        "rejects text that is not 1-6 plain lowercase letters" {
            detector.isSyllable(candidate("abcdefg", "abcdefg'1")) shouldBe false
            detector.isSyllable(candidate("", "'1")) shouldBe false
            detector.isSyllable(candidate("Zhe", "Zhe'43")) shouldBe false
            detector.isSyllable(candidate("zhe1", "zhe1'43")) shouldBe false
            detector.isSyllable(candidate("这", "zhe'43")) shouldBe false
        }

        "rejects a comment that is not a syllable with its key sequence" {
            // English completions carry letters in `text` but no key sequence
            detector.isSyllable(candidate("the")) shouldBe false
            detector.isSyllable(candidate("the", "pron")) shouldBe false
            detector.isSyllable(candidate("zhe", "zhe'")) shouldBe false
            detector.isSyllable(candidate("zhe", "'43")) shouldBe false
            detector.isSyllable(candidate("zhe", "ZHE'43")) shouldBe false
        }

        "accepts the bare-comment ambiguity of an english completion" {
            // a comment repeating the word has the bare-syllable shape; in the
            // target scheme no dictionary candidate looks like this, and even
            // then the global index keeps selection correct — only placement
            // (sidebar vs. bar) changes
            detector.isSyllable(candidate("you", "you")) shouldBe true
        }

        "rejects a Han candidate with a key sequence comment" {
            detector.isSyllable(candidate("这", "'43")) shouldBe false
            detector.isSyllable(candidate("这个", "zhe'43")) shouldBe false
        }

        "honours custom patterns" {
            // patterns refine the character classes; the structural rule (the
            // comment opens with the text) still applies
            val custom =
                SyllableCandidateDetector(
                    textPattern = Regex("[a-zA-Z]{1,7}"),
                    commentPattern = Regex("[a-zA-Z]+('[0-9']+)?"),
                )
            custom.isSyllable(candidate("ZHE", "ZHE'43")) shouldBe true
            custom.isSyllable(candidate("abcdefg", "abcdefg'1")) shouldBe true
            custom.isSyllable(candidate("Gen", "Gen")) shouldBe true
            // the default still rejects what the custom pattern now accepts
            detector.isSyllable(candidate("ZHE", "ZHE'43")) shouldBe false
            detector.isSyllable(candidate("abcdefg", "abcdefg'1")) shouldBe false
            // a comment that does not open with the text stays rejected
            custom.isSyllable(candidate("abcdefg", "abcdefg:43")) shouldBe false
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

        "filters bare-comment syllables of a continuation round" {
            // zhe'43 selected: the lua translator re-emits candidates for the
            // remaining 43 with comments `ge` / `he` (no remainder to report)
            val all =
                arrayOf(
                    candidate("ge", "ge"),
                    candidate("he", "he"),
                    candidate("这个", "zhe ge"),
                    candidate("这和", "zhe he"),
                )
            val split = SyllableCandidateSplit.of(all, detector)

            split.displayed.map { it.text } shouldContainExactly listOf("这个", "这和")
            split.displayedGlobalIndices.toList() shouldContainExactly listOf(2, 3)
            split.syllables.map { it.text } shouldContainExactly listOf("ge", "he")
            split.syllables.map { it.globalIndex } shouldContainExactly listOf(0, 1)
        }

        "copes with a list that is empty" {
            val split = SyllableCandidateSplit.of(arrayOf(), detector)
            split.displayed.toList() shouldBe emptyList()
            split.displayedGlobalIndices.toList() shouldBe emptyList()
            split.syllables shouldBe emptyList()
        }
    })
