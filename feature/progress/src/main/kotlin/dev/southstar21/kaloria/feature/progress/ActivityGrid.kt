package dev.southstar21.kaloria.feature.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.southstar21.kaloria.core.designsystem.component.ContentCard
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** The current streak, the best and the total, over a grid of the last few weeks' logged days. */
@Composable
internal fun StreakTile(state: ProgressUiState, modifier: Modifier = Modifier) {
    val streak = state.streak
    ContentCard(modifier.fillMaxWidth(), iridescent = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.LocalFireDepartment,
                null,
                tint = if (streak.current > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(36.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${streak.current}", style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold))
                    Text(
                        pluralStringResource(R.plurals.progress_streak_unit, streak.current),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                Text(stringResource(R.string.progress_streak_current), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.progress_streak_best, streak.best), style = MaterialTheme.typography.titleSmall)
                Text(pluralStringResource(R.plurals.progress_streak_total, streak.totalDays, streak.totalDays), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(14.dp))
        ActivityGrid(state.recentDays, state.today)
    }
}

/** One square per day, a column per week, filled where anything was logged; today is outlined. */
@Composable
private fun ActivityGrid(days: Set<LocalDate>, today: LocalDate) {
    val filled = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
    // Which cells are filled, worked out once per change rather than on every draw.
    val cells = remember(days, today) {
        val start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(ACTIVITY_WEEKS - 1L)
        Array(ACTIVITY_WEEKS * 7) { i ->
            val day = start.plusDays(i.toLong())
            when {
                day.isAfter(today) -> Cell.Future
                day == today -> if (day in days) Cell.TodayFilled else Cell.TodayEmpty
                day in days -> Cell.Filled
                else -> Cell.Empty
            }
        }
    }
    val description = stringResource(R.string.progress_activity_description, days.size, ACTIVITY_WEEKS)
    Canvas(
        Modifier
            .fillMaxWidth()
            .aspectRatio(ACTIVITY_WEEKS / 7f)
            .semantics { contentDescription = description },
    ) {
        val cell = size.width / ACTIVITY_WEEKS
        val gap = cell * 0.18f
        val side = cell - gap
        val corner = CornerRadius(side * 0.3f)
        for (week in 0 until ACTIVITY_WEEKS) {
            for (dow in 0 until 7) {
                val c = cells[week * 7 + dow]
                if (c == Cell.Future) continue
                val topLeft = Offset(week * cell + gap / 2, dow * cell + gap / 2)
                drawRoundRect(if (c == Cell.Filled || c == Cell.TodayFilled) filled else empty, topLeft, Size(side, side), corner)
                if (c == Cell.TodayFilled || c == Cell.TodayEmpty) drawRoundRect(filled, topLeft, Size(side, side), corner, style = Stroke(1.5.dp.toPx()))
            }
        }
    }
}

private enum class Cell { Empty, Filled, TodayEmpty, TodayFilled, Future }
