package dev.southstar21.kaloria.navigation

import androidx.navigation3.runtime.NavKey
import dev.southstar21.kaloria.feature.foods.FoodEditorKey
import dev.southstar21.kaloria.feature.settings.SettingsKey
import dev.southstar21.kaloria.feature.today.TodayKey

// Every change to the back stack goes through these. A screen's callbacks can still fire while it
// slides away (a second tap on its back arrow, an editor's "done" after a manual back), so each one
// checks that the screen asking is still the one on top.

/** Opens [key], unless it's already on top: a double tap opens one screen, not two. */
internal fun MutableList<NavKey>.push(key: NavKey) {
    if (lastOrNull() != key) add(key)
}

/** Closes [key] if it's on top, and never the last screen: a late "back" can't close the one beneath. */
internal fun MutableList<NavKey>.pop(key: NavKey) {
    if (size > 1 && last() == key) removeAt(lastIndex)
}

/** System back: closes whatever is on top, but never the last screen. */
internal fun MutableList<NavKey>.back() {
    if (size > 1) removeAt(lastIndex)
}

/** Swaps [from] for [to], if [from] is still on top. */
internal fun MutableList<NavKey>.replace(from: NavKey, to: NavKey) {
    if (lastOrNull() != from) return
    removeAt(lastIndex)
    add(to)
}

/** Switching tabs resets to that tab's root; Today always stays at the bottom so back exits from it. */
internal fun MutableList<NavKey>.navigateTopLevel(key: NavKey) {
    if (lastOrNull() == key) return
    clear()
    add(TodayKey)
    if (key != TodayKey) add(key)
}

/**
 * Opens a shortcut's screen over Today. While a form with possibly unsaved edits is open (a food
 * editor, the goals in Settings) it goes on top instead, so back returns to the form as it was.
 */
internal fun MutableList<NavKey>.openShortcut(key: NavKey) {
    when {
        lastOrNull() == key -> Unit

        any { it is FoodEditorKey || it == SettingsKey } -> add(key)

        else -> {
            clear()
            add(TodayKey)
            add(key)
        }
    }
}
