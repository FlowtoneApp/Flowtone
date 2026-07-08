package ink.tenqui.flowtone.data.listening

import org.junit.Assert.assertEquals
import org.junit.Test

class ListeningStatsRankingTest {
    @Test
    fun ranksByDurationThenPlayCountThenStableOrder() {
        val ranked = rankListeningStats(
            items = listOf(
                RankItem(id = "stable-b", durationMs = 3_000L, playCount = 2, stableOrder = 3L),
                RankItem(id = "longest", durationMs = 5_000L, playCount = 1, stableOrder = 2L),
                RankItem(id = "more-plays", durationMs = 3_000L, playCount = 4, stableOrder = 4L),
                RankItem(id = "stable-a", durationMs = 3_000L, playCount = 2, stableOrder = 1L)
            ),
            durationSelector = { it.durationMs },
            playCountSelector = { it.playCount },
            stableOrderSelector = { it.stableOrder }
        )

        assertEquals(
            listOf("longest", "more-plays", "stable-a", "stable-b"),
            ranked.map { it.id }
        )
    }

    private data class RankItem(
        val id: String,
        val durationMs: Long,
        val playCount: Int,
        val stableOrder: Long
    )
}
