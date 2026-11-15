package com.example.dailygoals.ui.insights

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dailygoals.R
import com.example.dailygoals.engine.GoalHistory
import com.example.dailygoals.engine.InsightRange
import com.example.dailygoals.engine.Mistake
import com.example.dailygoals.engine.Observation
import com.example.dailygoals.model.FailSource
import com.example.dailygoals.ui.components.SegmentedChoice
import com.example.dailygoals.ui.formatMinute
import com.example.dailygoals.ui.shortName
import java.time.format.DateTimeFormatter

private enum class Page { LIST, GOAL, MISTAKES }

@Composable
fun InsightsScreen(viewModel: InsightsViewModel, onOpenReviews: () -> Unit, modifier: Modifier = Modifier) {
    var page by rememberSaveable { mutableStateOf(Page.LIST) }
    var goalId by rememberSaveable { mutableStateOf(0L) }
    var range by rememberSaveable { mutableStateOf(InsightRange.WEEK) }
    val state by remember(range) { viewModel.state(range) }.collectAsStateWithLifecycle()
    val data = state ?: return
    when (page) {
        Page.LIST -> InsightsList(data, range, { range = it }, onOpenGoal = { goalId = it; page = Page.GOAL }, onMistakes = { page = Page.MISTAKES }, onReviews = onOpenReviews, modifier = modifier)
        Page.GOAL -> data.goals.firstOrNull { it.goal.id == goalId }?.let { GoalInsightPage(it, data.mistakes.filter { m -> m.goalId == goalId }.size, onBack = { page = Page.LIST }, modifier = modifier) } ?: run { page = Page.LIST }
        Page.MISTAKES -> MistakesPage(data, onBack = { page = Page.LIST }, modifier = modifier)
    }
}

@Composable
private fun InsightsList(
    data: InsightsState,
    range: InsightRange,
    onRange: (InsightRange) -> Unit,
    onOpenGoal: (Long) -> Unit,
    onMistakes: () -> Unit,
    onReviews: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(stringResource(R.string.insights_title), fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        SegmentedChoice(
            options = listOf(
                InsightRange.WEEK to stringResource(R.string.insights_week),
                InsightRange.MONTH to stringResource(R.string.insights_month),
                InsightRange.ALL to stringResource(R.string.insights_all),
            ),
            selected = range,
            onSelect = onRange,
            modifier = Modifier.padding(top = 12.dp),
        )
        Label(R.string.insights_goals)
        if (data.goals.isEmpty()) Text(stringResource(R.string.insights_no_goals), color = MaterialTheme.colorScheme.onSurfaceVariant)
        data.goals.forEach { insight ->
            val report = insight.report
            Card(onClick = { onOpenGoal(insight.goal.id) }) {
                Column(Modifier.weight(1f)) {
                    Text(insight.goal.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (report.hasEnoughData) {
                            stringResource(R.string.insights_line, report.successPercent, report.detected)
                        } else {
                            stringResource(R.string.insights_not_enough, report.dueDays, PatternRulesMin)
                        },
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (report.hasEnoughData) Text("${report.successPercent}%", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
        Label(R.string.insights_all_goals)
        Card(onClick = onMistakes) {
            Text(stringResource(R.string.insights_mistakes), Modifier.weight(1f), fontSize = 15.sp)
            Text(data.mistakes.size.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Card(onClick = onReviews) {
            Text(stringResource(R.string.insights_reviews), Modifier.weight(1f), fontSize = 15.sp)
        }
        Card {
            Text(stringResource(R.string.insights_pauses_used), Modifier.weight(1f), fontSize = 15.sp)
            Text(data.emergencyPausesThisMonth.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Card {
            Text(stringResource(R.string.insights_recoveries_rescues), Modifier.weight(1f), fontSize = 15.sp)
            Text("${data.recoveries} · ${data.rescues}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private const val PatternRulesMin = com.example.dailygoals.engine.PatternRules.MIN_DUE_DAYS

@Composable
private fun GoalInsightPage(insight: GoalInsight, mistakes: Int, onBack: () -> Unit, modifier: Modifier) {
    val report = insight.report
    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp)) {
        BackHeader(insight.goal.name, onBack)
        if (!report.hasEnoughData) {
            Card { Column { Text(stringResource(R.string.insights_not_enough_title), fontWeight = FontWeight.SemiBold); Text(stringResource(R.string.insights_not_enough_body, report.dueDays), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        } else {
            Text("${report.successPercent}%", fontSize = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            Text(stringResource(R.string.insights_success_caption, report.dueDays), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Label(R.string.insights_says)
            if (report.observations.isEmpty()) Text(stringResource(R.string.insights_nothing_yet), color = MaterialTheme.colorScheme.onSurfaceVariant)
            report.observations.forEach { Note(sentence(it)) }
        }
        Label(R.string.insights_figures)
        Figure(R.string.insights_figure_counts, "${report.met} / ${report.dueDays}")
        report.worstWeekday?.let { Figure(R.string.insights_figure_worst_day, it.shortName()) }
        report.topReason?.let { Figure(R.string.insights_figure_reason, it) }
        report.topTrigger?.let { Figure(R.string.insights_figure_trigger, it) }
        report.averageLateMinutes?.let { Figure(R.string.insights_figure_late, stringResource(R.string.form_grace_minutes, it)) }
        Label(R.string.insights_sources)
        Figure(R.string.insights_source_self, report.selfReported.toString())
        Figure(R.string.insights_source_detected, report.detected.toString())
        Figure(R.string.insights_source_corrected, report.corrected.toString())
        Figure(R.string.insights_mistakes, mistakes.toString())
        insight.goal.meaning.plan?.let {
            Label(R.string.insights_plan)
            Note(it)
        }
    }
}

@Composable
private fun sentence(observation: Observation): String = when (observation) {
    is Observation.WeekdayWeekend -> stringResource(R.string.insights_obs_weekday, observation.weekdayPercent, observation.weekendPercent)
    is Observation.FailureBand -> stringResource(R.string.insights_obs_band, observation.percent, formatMinute(observation.startHour * 60), formatMinute(((observation.startHour + 2) % 24) * 60))
    is Observation.CommonCause -> stringResource(if (observation.isTrigger) R.string.insights_obs_trigger else R.string.insights_obs_reason, observation.label, observation.percent)
    is Observation.LateCompletions -> stringResource(R.string.insights_obs_late, observation.percentLate, observation.averageMinutes)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MistakesPage(data: InsightsState, onBack: () -> Unit, modifier: Modifier) {
    var goal by rememberSaveable { mutableStateOf(0L) }
    var trigger by rememberSaveable { mutableStateOf("") }
    var search by rememberSaveable { mutableStateOf("") }
    val shown = remember(data, goal, trigger, search) {
        data.mistakes.filter { m -> (goal == 0L || m.goalId == goal) && (trigger.isEmpty() || m.trigger == trigger || m.tag == trigger) && matches(m, search) }
    }
    val triggers = remember(data) { data.mistakes.flatMap { listOfNotNull(it.trigger, it.tag) }.distinct().sorted() }
    val format = remember { DateTimeFormatter.ofPattern("EEE d MMM") }
    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp)) {
        BackHeader(stringResource(R.string.insights_mistakes), onBack)
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            label = { Text(stringResource(R.string.insights_search)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            FilterChip(selected = goal == 0L, onClick = { goal = 0L }, label = { Text(stringResource(R.string.insights_all_goals_chip)) })
            data.goals.forEach { g -> FilterChip(selected = goal == g.goal.id, onClick = { goal = if (goal == g.goal.id) 0L else g.goal.id }, label = { Text(g.goal.name) }) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            triggers.forEach { t -> FilterChip(selected = trigger == t, onClick = { trigger = if (trigger == t) "" else t }, label = { Text(t) }) }
        }
        if (shown.isEmpty()) Text(stringResource(R.string.insights_no_mistakes), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp))
        shown.forEach { m ->
            Card {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(m.goalName, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(stringResource(if (m.detected) R.string.insights_source_detected_tag else R.string.insights_source_self_tag), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(m.date.format(format) + (m.minuteOfDay?.let { " · " + formatMinute(it) } ?: ""), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val details = listOfNotNull(m.tag, m.trigger, m.mood?.let { stringResource(R.string.insights_mood_value, it) }, m.before, m.place).joinToString(" · ")
                    if (details.isNotEmpty()) Text(details, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                    m.note?.let { Text("“$it”", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp)) }
                }
            }
        }
        val causes = remember(data) { GoalHistory.commonCause(data.mistakes) }
        if (causes.isNotEmpty()) {
            Label(R.string.insights_common_cause)
            causes.forEach { (name, cause) -> Figure(R.string.insights_common_cause_line, "$name: $cause") }
        }
    }
}

private fun matches(m: Mistake, query: String): Boolean {
    val q = query.trim().lowercase()
    return q.isEmpty() || listOfNotNull(m.note, m.tag, m.trigger, m.before, m.place, m.goalName).any { it.lowercase().contains(q) }
}

@Composable
internal fun BackHeader(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        com.example.dailygoals.ui.components.IconButtonBox(com.example.dailygoals.ui.components.AppIconKind.CHEVRON_LEFT, stringResource(R.string.form_close), onBack)
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 16.dp))
    }
}

@Composable
internal fun Label(text: Int) {
    Text(stringResource(text), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 20.dp, bottom = 6.dp))
}

@Composable
internal fun Card(onClick: (() -> Unit)? = null, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(bottom = 8.dp)
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(14.dp),
        content = content,
    )
}

@Composable
internal fun Figure(label: Int, value: String) {
    Card {
        Text(stringResource(label), Modifier.weight(1f), fontSize = 14.sp)
        Text(value, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun Note(text: String) {
    Card { Text(text, fontSize = 14.sp) }
}
