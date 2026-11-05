package dev.southstar21.kaloria.feature.today

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import dev.southstar21.kaloria.core.designsystem.component.GlassDefaults
import dev.southstar21.kaloria.core.designsystem.component.GlassStyle
import dev.southstar21.kaloria.core.designsystem.component.GreetingHeader
import dev.southstar21.kaloria.core.designsystem.component.LocalGlassBackdrop
import dev.southstar21.kaloria.core.designsystem.component.glass
import dev.southstar21.kaloria.core.designsystem.component.grouped
import dev.southstar21.kaloria.core.designsystem.liquid.LiquidTabs
import dev.southstar21.kaloria.core.designsystem.liquid.consumeHorizontalDrags
import dev.southstar21.kaloria.core.ui.dayTitle
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import kotlin.math.roundToInt

/** "Good evening" over "607 of 2,200 kcal today" on today; the day's name on other days. */
@Composable
internal fun Header(state: TodayUiState, greeting: String, onToday: () -> Unit, onOpenSettings: () -> Unit) {
    val eaten = (state.totals.energyKcal ?: 0.0).roundToInt()
    val isToday = state.date == state.today
    val goal = state.goals.energyKcal
    val subtitle = when {
        goal != null && isToday -> stringResource(R.string.today_header_of_goal_today, eaten.grouped(), goal.grouped())
        goal != null -> stringResource(R.string.today_header_of_goal, eaten.grouped(), goal.grouped())
        isToday -> stringResource(R.string.today_header_eaten_today, eaten.grouped())
        else -> stringResource(R.string.today_header_eaten, eaten.grouped())
    }
    Row(verticalAlignment = Alignment.Top) {
        AnimatedContent(
            targetState = state.date,
            transitionSpec = {
                val forward = targetState > initialState
                (slideInHorizontally(spring(dampingRatio = 0.8f, stiffness = 400f)) { if (forward) it / 4 else -it / 4 } + fadeIn())
                    .togetherWith(slideOutHorizontally(spring(dampingRatio = 0.8f, stiffness = 400f)) { if (forward) -it / 4 else it / 4 } + fadeOut())
            },
            modifier = Modifier.weight(1f),
            label = "day",
        ) { day ->
            GreetingHeader(if (day == state.today) greeting else day.dayTitle(state.today), subtitle)
        }
        if (!isToday) {
            TextButton(onClick = onToday) { Text(stringResource(R.string.today_go_to_today)) }
        }
        // A 48dp touch target around the 44dp glass button.
        IconButton(onClick = onOpenSettings, modifier = Modifier.size(48.dp)) {
            Box(Modifier.size(44.dp).glass(CircleShape, GlassStyle.Card), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Settings, stringResource(R.string.today_settings), Modifier.size(20.dp))
            }
        }
    }
}

/**
 * The week around [selected] as Liquid Glass tabs, like the dock: tap a day or press and drag the
 * drop of glass across the week. Swiping the content still moves day by day, past the week's ends.
 */
@Composable
internal fun WeekStrip(selected: LocalDate, today: LocalDate, onSelect: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val firstDay = WeekFields.of(locale).firstDayOfWeek
    val start = selected.with(TemporalAdjusters.previousOrSame(firstDay))
    val fullDate = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale) }
    LiquidTabs(
        count = 7,
        selectedIndex = ChronoUnit.DAYS.between(start, selected).toInt(),
        onSelect = { onSelect(start.plusDays(it.toLong())) },
        // Sideways drags here move the drop, not the day under the whole screen.
        modifier = modifier.fillMaxWidth().consumeHorizontalDrags(),
        height = 64.dp,
        paneShape = GlassDefaults.Shape,
        // It scrolls with the content, so it refracts only the field, never itself.
        backdrop = LocalGlassBackdrop.current,
        floating = false,
    ) { index, tinted ->
        val day = start.plusDays(index.toLong())
        val number = when {
            tinted -> MaterialTheme.colorScheme.primary
            day == today -> MaterialTheme.colorScheme.primary
            day > today -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            else -> MaterialTheme.colorScheme.onSurface
        }
        // Screen readers hear the full date rather than "W" and "5".
        val description = day.format(fullDate)
        Text(
            day.dayOfWeek.getDisplayName(TextStyle.NARROW, locale),
            style = MaterialTheme.typography.labelSmall,
            color = if (tinted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clearAndSetSemantics { contentDescription = description },
        )
        Text(day.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium, color = number, modifier = Modifier.clearAndSetSemantics { })
    }
}
