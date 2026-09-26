package com.pranav.atmini.core.l0.resolver

import android.content.Context
import android.content.Intent
import com.pranav.atmini.core.l0.model.ResolutionResult

data class AppCandidate(
    val packageName: String,
    val label: String,
    val score: Double
)

class AppResolver(private val context: Context) {

    private val ambiguityDeltaThreshold = 0.12

    fun resolveApp(query: String): ResolutionResult<AppCandidate> {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val apps = pm.queryIntentActivities(mainIntent, 0)

        val candidates = apps.map { app ->
            val label = app.loadLabel(pm).toString()
            val score = calculateSimilarity(query.lowercase(), label.lowercase())
            AppCandidate(app.activityInfo.packageName, label, score)
        }.filter { it.score > 0.60 }.sortedByDescending { it.score }

        if (candidates.isEmpty()) return ResolutionResult.NoMatch
        if (candidates.size == 1) return ResolutionResult.SingleMatch(candidates[0])

        val top = candidates[0]
        val second = candidates[1]

        if (top.score >= 0.98) return ResolutionResult.SingleMatch(top)

        return if ((top.score - second.score) < ambiguityDeltaThreshold) {
            ResolutionResult.Ambiguous(candidates.take(3), query)
        } else {
            ResolutionResult.SingleMatch(top)
        }
    }

    private fun calculateSimilarity(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0
        if (s2.contains(s1) || s1.contains(s2)) return 0.85
        val maxLen = maxOf(s1.length, s2.length)
        if (maxLen == 0) return 1.0
        return 1.0 - (levenshtein(s1, s2).toDouble() / maxLen)
    }

    private fun levenshtein(lhs: CharSequence, rhs: CharSequence): Int {
        var cost = IntArray(lhs.length + 1) { it }
        var newCost = IntArray(lhs.length + 1) { 0 }
        for (i in 1..rhs.length) {
            newCost[0] = i
            for (j in 1..lhs.length) {
                val match = if (lhs[j - 1] == rhs[i - 1]) 0 else 1
                newCost[j] = minOf(cost[j] + 1, newCost[j - 1] + 1, cost[j - 1] + match)
            }
            val swap = cost
            cost = newCost
            newCost = swap
        }
        return cost[lhs.length]
    }
}
