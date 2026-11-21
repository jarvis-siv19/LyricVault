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

class HybridMergeTest {

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
    fun merge_unionOfBothEngines() {
        val keywordOnly = makeIdea(1, "Keyword only")
        val vectorOnly = makeIdea(2, "Vector only")
        val keyword = VaultMatchResult(
            freshUnusedMatches = listOf(item(keywordOnly, 60, "Shared words")),
            querySummary = "q"
        )
        val vector = listOf(item(vectorOnly, 70, "Similar meaning & mood"))

        val merged = AiMatchingService.mergeResults(keyword, vector)

        assertEquals(2, merged.totalCount)
        assertEquals("q", merged.querySummary)
        // Higher vector score sorts first.
        assertEquals(2L, merged.freshUnusedMatches[0].ideaDetail.idea.id)
    }

    @Test
    fun merge_sameIdea_dedupesAndKeepsHigherScore() {
        val idea = makeIdea(1, "Both engines")
        val keyword = VaultMatchResult(
            freshUnusedMatches = listOf(
                item(idea, 55, "Shared words", listOf("night"))
            )
        )
        val vector = listOf(item(idea, 80, "Similar meaning & mood"))

        val merged = AiMatchingService.mergeResults(keyword, vector)

        assertEquals(1, merged.totalCount)
        val only = merged.freshUnusedMatches[0]
        assertEquals(80, only.scorePercent)
        assertEquals("Shared words • Similar meaning & mood", only.matchReason)
        assertEquals(listOf("night"), only.matchedTerms)
    }

    @Test
    fun merge_sameReason_notDuplicated() {
        val idea = makeIdea(1, "Same reason")
        val keyword = VaultMatchResult(
            freshUnusedMatches = listOf(item(idea, 55, "Similar meaning & mood"))
        )
        val vector = listOf(item(idea, 80, "Similar meaning & mood"))

        val merged = AiMatchingService.mergeResults(keyword, vector)

        assertEquals("Similar meaning & mood", merged.freshUnusedMatches[0].matchReason)
    }

    @Test
    fun merge_splitsFreshAndUsed() {
        val fresh = makeIdea(1, "Fresh", usedCount = 0)
        val used = makeIdea(2, "Used", usedCount = 3)
        val keyword = VaultMatchResult(
            freshUnusedMatches = listOf(item(fresh, 60, "Shared words")),
            alreadyUsedMatches = listOf(item(used, 65, "Shared words"))
        )
        // Vector engine finds the used idea with a higher score.
        val vector = listOf(item(used, 90, "Similar meaning & mood"))

        val merged = AiMatchingService.mergeResults(keyword, vector)

        assertEquals(1, merged.freshUnusedMatches.size)
        assertEquals(1, merged.alreadyUsedMatches.size)
        assertEquals(90, merged.alreadyUsedMatches[0].scorePercent)
        assertTrue(merged.freshUnusedMatches.none { it.ideaDetail.idea.usedCount > 0 })
    }

    @Test
    fun merge_emptyVector_returnsKeyword() {
        val idea = makeIdea(1, "Keyword only")
        val keyword = VaultMatchResult(
            freshUnusedMatches = listOf(item(idea, 60, "Shared words")),
            querySummary = "q"
        )

        val merged = AiMatchingService.mergeResults(keyword, emptyList())

        assertEquals(1, merged.totalCount)
        assertEquals(60, merged.freshUnusedMatches[0].scorePercent)
        assertEquals("q", merged.querySummary)
    }

    @Test
    fun merge_capsAt25FreshAnd15Used() {
        val fresh = (1L..30L).map { makeIdea(it, "Fresh $it") }
        val used = (31L..50L).map { makeIdea(it, "Used $it", usedCount = 1) }
        val keyword = VaultMatchResult(
            freshUnusedMatches = fresh.map { item(it, 50, "Shared words") },
            alreadyUsedMatches = used.map { item(it, 50, "Shared words") }
        )

        val merged = AiMatchingService.mergeResults(keyword, emptyList())

        assertEquals(25, merged.freshUnusedMatches.size)
        assertEquals(15, merged.alreadyUsedMatches.size)
    }
}
