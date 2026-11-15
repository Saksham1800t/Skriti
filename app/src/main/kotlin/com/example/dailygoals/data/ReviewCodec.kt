package com.example.dailygoals.data

import com.example.dailygoals.engine.GoalChange
import com.example.dailygoals.engine.GoalFailureRate
import com.example.dailygoals.engine.NextWeek
import com.example.dailygoals.engine.Recommendation
import com.example.dailygoals.engine.WeeklyReview
import org.json.JSONArray
import org.json.JSONObject

/** Stores a weekly review as text, so a review made once is shown exactly as it was, whatever changes later. */
object ReviewCodec {
    fun encode(review: WeeklyReview, names: Map<Long, String>): String = JSONObject().apply {
        put("habits", review.habits)
        put("completion", review.completionPercent)
        put("failures", review.failures)
        put("rescues", review.rescues)
        put("pauses", review.emergencyPauses)
        put("streak", review.bestStreak)
        put("change", review.consistencyChangePoints)
        review.improved?.let { put("improved", JSONObject().put("id", it.goalId).put("name", it.name).put("delta", it.deltaPoints)) }
        review.worst?.let { put("worst", JSONObject().put("id", it.goalId).put("name", it.name).put("failed", it.failed).put("due", it.due)) }
        put("reasons", pairs(review.reasons))
        put("triggers", pairs(review.triggers))
        review.recommendation?.let { put("recommendation", recommendation(it)) }
        put("names", JSONObject().apply { names.forEach { (id, name) -> put(id.toString(), name) } })
    }.toString()

    fun encodeDecisions(decisions: Map<Long, NextWeek>): String =
        JSONObject().apply { decisions.forEach { (id, choice) -> put(id.toString(), choice.name) } }.toString()

    /** The review with the user's choices, or null when the stored text cannot be read. */
    fun decode(summary: String, decisions: String): Pair<WeeklyReview, Map<Long, String>>? = runCatching {
        val json = JSONObject(summary)
        val chosen = JSONObject(decisions)
        val names = json.getJSONObject("names").let { n -> n.keys().asSequence().associate { it.toLong() to n.getString(it) } }
        WeeklyReview(
            habits = json.getInt("habits"),
            completionPercent = json.getInt("completion"),
            failures = json.getInt("failures"),
            rescues = json.getInt("rescues"),
            emergencyPauses = json.getInt("pauses"),
            bestStreak = json.getInt("streak"),
            consistencyChangePoints = json.getInt("change"),
            improved = json.optJSONObject("improved")?.let { GoalChange(it.getLong("id"), it.getString("name"), it.getInt("delta")) },
            worst = json.optJSONObject("worst")?.let { GoalFailureRate(it.getLong("id"), it.getString("name"), it.getInt("failed"), it.getInt("due")) },
            reasons = readPairs(json.getJSONArray("reasons")),
            triggers = readPairs(json.getJSONArray("triggers")),
            recommendation = json.optJSONObject("recommendation")?.let(::readRecommendation),
            decisions = names.keys.associateWith { id -> chosen.optString(id.toString(), NextWeek.KEEP.name).let { runCatching { NextWeek.valueOf(it) }.getOrDefault(NextWeek.KEEP) } },
        ) to names
    }.getOrNull()

    private fun pairs(list: List<Pair<String, Int>>) = JSONArray().apply { list.forEach { put(JSONObject().put("label", it.first).put("count", it.second)) } }

    private fun readPairs(array: JSONArray): List<Pair<String, Int>> = List(array.length()) { array.getJSONObject(it).let { o -> o.getString("label") to o.getInt("count") } }

    private fun recommendation(r: Recommendation): JSONObject = when (r) {
        is Recommendation.MoveWindow -> JSONObject().put("type", "MOVE").put("id", r.goalId).put("minutes", r.averageLateMinutes)
        is Recommendation.AddMinimum -> JSONObject().put("type", "MINIMUM").put("id", r.goalId)
        is Recommendation.PauseIt -> JSONObject().put("type", "PAUSE").put("id", r.goalId)
    }

    private fun readRecommendation(o: JSONObject): Recommendation? = when (o.getString("type")) {
        "MOVE" -> Recommendation.MoveWindow(o.getLong("id"), o.getInt("minutes"))
        "MINIMUM" -> Recommendation.AddMinimum(o.getLong("id"))
        "PAUSE" -> Recommendation.PauseIt(o.getLong("id"))
        else -> null
    }
}
