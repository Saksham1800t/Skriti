package com.example.dailygoals.ui.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dailygoals.R
import com.example.dailygoals.SkritiApp
import com.example.dailygoals.data.ReviewCodec
import com.example.dailygoals.engine.NextWeek
import com.example.dailygoals.engine.Recommendation
import com.example.dailygoals.engine.WeeklyReview
import com.example.dailygoals.engine.WeeklyReviewRules
import com.example.dailygoals.ui.components.AccentButton
import com.example.dailygoals.ui.components.SegmentedChoice
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private val WEEK_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")

/** Past reviews, newest first. Opening one shows it as it was made. */
@Composable
fun ReviewListScreen(onBack: () -> Unit, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as SkritiApp
    val rows by app.repository.weeklyReviews.collectAsStateWithLifecycle(initialValue = emptyList())
    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp)) {
        BackHeader(stringResource(R.string.insights_reviews), onBack)
        if (rows.isEmpty()) Text(stringResource(R.string.review_none), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp))
        rows.forEach { row ->
            val start = LocalDate.parse(row.weekStart)
            val decoded = ReviewCodec.decode(row.summary, row.decisions)?.first
            Card(onClick = { onOpen(row.weekStart) }) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.review_week_range, start.format(WEEK_FORMAT), start.plusDays(6).format(WEEK_FORMAT)), fontWeight = FontWeight.SemiBold)
                    decoded?.let { Text(stringResource(R.string.review_list_line, it.completionPercent, it.failures), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

@Composable
fun WeeklyReviewScreen(weekStart: String, onBack: () -> Unit, onModify: (Long) -> Unit, modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as SkritiApp
    val rows by app.repository.weeklyReviews.collectAsStateWithLifecycle(initialValue = emptyList())
    val row = rows.firstOrNull { it.weekStart == weekStart }
    val decoded = remember(row) { row?.let { ReviewCodec.decode(it.summary, it.decisions) } }
    var review by remember(decoded) { mutableStateOf(decoded?.first) }
    val names = decoded?.second.orEmpty()
    val scope = rememberCoroutineScope()
    var saved by remember(weekStart) { mutableStateOf(false) }
    val start = LocalDate.parse(weekStart)
    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp)) {
        BackHeader(stringResource(R.string.review_week_range, start.format(WEEK_FORMAT), start.plusDays(6).format(WEEK_FORMAT)), onBack)
        Spacer(Modifier.height(16.dp))
        val current = review ?: run {
            Text(stringResource(R.string.review_missing), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp))
            return@Column
        }
        Summary(current)
        current.improved?.let {
            Label(R.string.review_worked)
            Note(stringResource(R.string.review_worked_line, it.name, it.deltaPoints))
        }
        current.worst?.let {
            Label(R.string.review_didnt)
            Note(stringResource(R.string.review_didnt_line, it.name, it.failed, it.due))
        }
        if (current.reasons.isNotEmpty() || current.triggers.isNotEmpty()) {
            Label(R.string.review_why)
            current.reasons.forEach { Figure(R.string.review_reason_label, "${it.first} · ${it.second}") }
            current.triggers.forEach { Figure(R.string.review_trigger_label, "${it.first} · ${it.second}") }
        }
        current.recommendation?.let {
            Label(R.string.review_suggestion)
            Note(suggestion(it, names))
        }
        Label(R.string.review_next_week)
        current.decisions.keys.sorted().forEach { id ->
            Card {
                Column(Modifier.fillMaxWidth()) {
                    Text(names[id].orEmpty(), fontWeight = FontWeight.SemiBold)
                    SegmentedChoice(
                        options = NextWeek.entries.map { it to stringResource(choiceLabel(it)) },
                        selected = current.decisions.getValue(id),
                        onSelect = { choice -> review = WeeklyReviewRules.withDecision(current, id, choice) },
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
        AccentButton(stringResource(R.string.review_save), onClick = {
            scope.launch {
                val modify = app.repository.applyReviewChoices(weekStart, current.decisions)
                saved = true
                modify?.let(onModify)
            }
        })
        if (saved) Text(stringResource(R.string.review_saved), fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
        Text(stringResource(R.string.review_choices_help), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp))
    }
}

private fun choiceLabel(choice: NextWeek) = when (choice) {
    NextWeek.KEEP -> R.string.review_keep
    NextWeek.MODIFY -> R.string.review_modify
    NextWeek.PAUSE -> R.string.review_pause
    NextWeek.DELETE -> R.string.review_delete
}

@Composable
private fun Summary(review: WeeklyReview) {
    Card {
        Column(Modifier.weight(1f)) {
            Text("${review.completionPercent}%", fontSize = 30.sp, fontWeight = FontWeight.Bold)
            val change = review.consistencyChangePoints
            Text(
                when {
                    change == null -> stringResource(R.string.review_first_week)
                    change >= 0 -> stringResource(R.string.review_up, change)
                    else -> stringResource(R.string.review_down, -change)
                },
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
            Text(pluralStringResource(R.plurals.review_habits, review.habits, review.habits), fontSize = 12.5.sp)
            Text(stringResource(R.string.review_failures_rescues, review.failures, review.rescues), fontSize = 12.5.sp)
            Text(pluralStringResource(R.plurals.review_pauses, review.emergencyPauses, review.emergencyPauses) + " · " + stringResource(R.string.review_streak, review.bestStreak), fontSize = 12.5.sp)
        }
    }
}

@Composable
private fun suggestion(r: Recommendation, names: Map<Long, String>): String {
    val name = names[r.goalId].orEmpty()
    return when (r) {
        is Recommendation.MoveWindow -> stringResource(R.string.review_suggest_move, name, r.averageLateMinutes)
        is Recommendation.AddMinimum -> stringResource(R.string.review_suggest_minimum, name)
        is Recommendation.PauseIt -> stringResource(R.string.review_suggest_pause, name)
    }
}
