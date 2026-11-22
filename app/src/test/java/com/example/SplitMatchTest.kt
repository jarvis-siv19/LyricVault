package com.example

import com.example.ai.AiMatchingService
import com.example.data.local.BlockType
import com.example.data.local.IdeaBlockEntity
import com.example.data.local.IdeaNoteEntity
import com.example.data.local.IdeaNoteWithDetails
import com.example.domain.matching.VaultMatchItem
import com.example.domain.matching.VaultMatchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the issue #8 split: AI (vector) matches and keyword matches
 * stay in separate sections instead of being merged into one list.
 */
class SplitMatchTest {

    private fun makeIdea(id: Long, title: String, usedCount: Int = 0): IdeaNoteWithDetails {
        return IdeaNoteWithDetails(
            idea = IdeaNoteEntity(id = id, title = title, usedCount = usedCount),
            blocks = listOf(
                IdeaBlockEntity(
                    id = id * 10,
                    ideaId = id,
                    orderIndex = 0,
                    blockType = BlockType.TEXT,
                    textContent = title
                )
            ),
            usages = emptyList()
        )
    }

    private fun item(
        detail: IdeaNoteWithDetails,
        score: Int,
        reason: String,
        terms: List<String> = emptyList()
    ) = VaultMatchItem(
        ideaDetail = detail,
        scorePercent = score,
        matchReason = reason,
        matchedTerms = terms,
        bestSnippet = detail.displayHeadline
    )

    @Test
    fun split_keywordPassesThroughUntouched() {
        val idea = makeIdea(1, "Keyword only")
        val keyword = VaultMatchResult(
            freshUnusedMatches = listOf(item(idea, 60, "Shared words", listOf("night"))),
            querySummary = "q"
        )

        val split = AiMatchingService.splitResults(keyword, emptyList())

        assertEquals(keyword, split.keywordMatches)
        assertEquals("q", split.aiMatches.querySummary)
        assertEquals(1, split.totalCount)
    }

    @Test
    fun split_vectorGoesToAiSectionOnly() {
        val keywordOnly = makeIdea(1, "Keyword only")
        val vectorOnly = makeIdea(2, "Vector only")
        val keyword = VaultMatchResult(
            freshUnusedMatches = listOf(item(keywordOnly, 60, "Shared words"))
        )
        val vector = listOf(item(vectorOnly, 70, "Similar meaning & mood"))

        val split = AiMatchingService.splitResults(keyword, vector)

        assertEquals(1, split.keywordMatches.totalCount)
        assertEquals(1, split.aiMatches.totalCount)
        assertEquals(2L, split.aiMatches.freshUnusedMatches[0].ideaDetail.idea.id)
        assertTrue(split.keywordMatches.freshUnusedMatches.none { it.ideaDetail.idea.id == 2L })
    }

    @Test
    fun split_sameIdeaAppearsInBothSections() {
        // No cross-engine dedupe: each section keeps its own score/reason.
        val idea = makeIdea(1, "Both engines")
        val keyword = VaultMatchResult(
            freshUnusedMatches = listOf(item(idea, 55, "Shared words", listOf("night")))
        )
        val vector = listOf(item(idea, 80, "Similar meaning & mood"))

        val split = AiMatchingService.splitResults(keyword, vector)

        assertEquals(1, split.keywordMatches.totalCount)
        assertEquals(1, split.aiMatches.totalCount)
        assertEquals(2, split.totalCount)
        val kw = split.keywordMatches.freshUnusedMatches[0]
        assertEquals(55, kw.scorePercent)
        assertEquals("Shared words", kw.matchReason)
        assertEquals(listOf("night"), kw.matchedTerms)
        val ai = split.aiMatches.freshUnusedMatches[0]
        assertEquals(80, ai.scorePercent)
        assertEquals("Similar meaning & mood", ai.matchReason)
    }

    @Test
    fun split_aiSectionSplitsFreshAndUsed() {
        val fresh = makeIdea(1, "Fresh", usedCount = 0)
        val used = makeIdea(2, "Used", usedCount = 3)
        val keyword = VaultMatchResult()
        val vector = listOf(
            item(fresh, 60, "Similar meaning & mood"),
            item(used, 90, "Similar meaning & mood")
        )

        val split = AiMatchingService.splitResults(keyword, vector)

        assertEquals(1, split.aiMatches.freshUnusedMatches.size)
        assertEquals(1, split.aiMatches.alreadyUsedMatches.size)
        assertEquals(90, split.aiMatches.alreadyUsedMatches[0].scorePercent)
        assertTrue(split.aiMatches.freshUnusedMatches.none { it.ideaDetail.idea.usedCount > 0 })
    }

    @Test
    fun split_aiSectionSortedByScoreDesc() {
        val low = makeIdea(1, "Low")
        val high = makeIdea(2, "High")
        val vector = listOf(
            item(low, 40, "Similar meaning & mood"),
            item(high, 85, "Similar meaning & mood")
        )

        val split = AiMatchingService.splitResults(VaultMatchResult(), vector)

        assertEquals(2L, split.aiMatches.freshUnusedMatches[0].ideaDetail.idea.id)
        assertEquals(1L, split.aiMatches.freshUnusedMatches[1].ideaDetail.idea.id)
    }

    @Test
    fun split_emptyVector_emptyAiSection() {
        val idea = makeIdea(1, "Keyword only")
        val keyword = VaultMatchResult(
            freshUnusedMatches = listOf(item(idea, 60, "Shared words"))
        )

        val split = AiMatchingService.splitResults(keyword, emptyList())

        assertEquals(0, split.aiMatches.totalCount)
        assertEquals(1, split.keywordMatches.totalCount)
    }

    @Test
    fun split_capsAiAt25FreshAnd15Used() {
        val fresh = (1L..30L).map { makeIdea(it, "Fresh $it") }
        val used = (31L..50L).map { makeIdea(it, "Used $it", usedCount = 1) }
        val vector = (fresh + used).map { item(it, 50, "Similar meaning & mood") }

        val split = AiMatchingService.splitResults(VaultMatchResult(), vector)

        assertEquals(25, split.aiMatches.freshUnusedMatches.size)
        assertEquals(15, split.aiMatches.alreadyUsedMatches.size)
    }
}
