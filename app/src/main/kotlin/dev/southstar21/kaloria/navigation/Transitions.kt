package dev.southstar21.kaloria.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.unit.IntOffset
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import dev.southstar21.kaloria.feature.scan.BarcodeScanKey
import dev.southstar21.kaloria.feature.scan.LabelScanKey

/*
 * Motion after iOS: everything moves on springs, so it starts fast, settles softly and can be
 * interrupted mid-flight. Tabs glide sideways toward the tab picked; screens opened on top slide
 * in from the right over the one beneath (which drifts left and fades), and the back gesture
 * drags them away again; the camera scanners rise from the bottom like a sheet.
 *
 * Screens are see-through over one shared field, so the screen being covered fades out quickly
 * rather than staying visible under the one sliding over it.
 */

/** Metadata key under which every entry carries its own NavKey, so the specs can tell what's on top of a scene. */
private const val MOTION_KEY = "dev.southstar21.kaloria.navigation.key"

/** Tags an entry with its key; the transition specs read it back from the scenes changing. */
internal fun NavEntry<NavKey>.withMotionKey(key: NavKey): NavEntry<NavKey> = NavEntry(key, contentKey, metadata + (MOTION_KEY to key)) { Content() }

/** The screen on top of a scene. */
private val Scene<NavKey>.top: NavKey?
    get() = entries.lastOrNull()?.metadata?.get(MOTION_KEY) as NavKey?

private enum class Kind { Tab, Push, Sheet }

private fun NavKey?.kind() = when {
    TopLevelDestination.entries.any { it.key == this } -> Kind.Tab
    this is BarcodeScanKey || this is LabelScanKey -> Kind.Sheet
    else -> Kind.Push
}

/** +1 when moving to a tab further right, -1 when moving left. */
private fun direction(from: NavKey?, to: NavKey?): Int {
    val a = TopLevelDestination.entries.indexOfFirst { it.key == from }
    val b = TopLevelDestination.entries.indexOfFirst { it.key == to }
    return if (b >= a) 1 else -1
}

/** The one spring screens move on: quick, with a whisper of overshoot. */
private fun <T> glide(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.86f, stiffness = 380f)

/** Tabs settle a little faster, like the dock's drop arriving. */
private fun <T> tabGlide(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.84f, stiffness = 460f)

private fun tab(direction: Int) = ContentTransform(
    targetContentEnter = slideInHorizontally(tabGlide<IntOffset>()) { direction * it / 5 } + fadeIn(tween(220, delayMillis = 40)),
    initialContentExit = slideOutHorizontally(tabGlide<IntOffset>()) { -direction * it / 7 } + fadeOut(tween(140)),
)

private fun push() = ContentTransform(
    targetContentEnter = slideInHorizontally(glide<IntOffset>()) { it } + fadeIn(tween(160)),
    initialContentExit = slideOutHorizontally(glide<IntOffset>()) { -it / 4 } + fadeOut(tween(180)),
    targetContentZIndex = 1f,
)

private fun pop() = ContentTransform(
    targetContentEnter = slideInHorizontally(glide<IntOffset>()) { -it / 4 } + fadeIn(tween(220, delayMillis = 60)),
    initialContentExit = slideOutHorizontally(glide<IntOffset>()) { it } + fadeOut(tween(220)),
    targetContentZIndex = -1f,
)

// The screen under a sheet only fades: scaling it would redraw its glass at a new size every frame
// while the camera is also running and sliding.
private fun sheetUp() = ContentTransform(
    targetContentEnter = slideInVertically(sheetRise<IntOffset>()) { it } + fadeIn(tween(160)),
    initialContentExit = fadeOut(tween(220)),
    targetContentZIndex = 1f,
)

private fun sheetDown() = ContentTransform(
    targetContentEnter = fadeIn(tween(240, delayMillis = 80)),
    initialContentExit = slideOutVertically(sheetDrop<IntOffset>()) { it } + fadeOut(tween(300, delayMillis = 60)),
    targetContentZIndex = -1f,
)

/**
 * A sheet rises without overshoot: the camera screens are opaque, so rising even a few pixels past
 * the top would open a strip of background along the bottom edge.
 */
private fun <T> sheetRise(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 380f)

/** A sheet drops away a touch faster than it rose, with no overshoot to bounce off the bottom. */
private fun <T> sheetDrop(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 420f)

/** Something new on top: a tab, a pushed screen or a scanner sheet. */
internal fun forward(): AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = {
    when (targetState.top.kind()) {
        Kind.Tab -> tab(direction(initialState.top, targetState.top))
        Kind.Sheet -> sheetUp()
        Kind.Push -> push()
    }
}

/** Back: whatever was closed leaves the way it came. */
internal fun back(): AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = {
    when (initialState.top.kind()) {
        Kind.Tab -> tab(direction(initialState.top, targetState.top))
        Kind.Sheet -> sheetDown()
        Kind.Push -> pop()
    }
}

/** The back gesture, before anything has changed: the screen on top follows the finger out. */
internal fun predictiveBack(): AnimatedContentTransitionScope<Scene<NavKey>>.(Int) -> ContentTransform = {
    when (initialState.top.kind()) {
        Kind.Tab -> tab(-1)
        Kind.Sheet -> sheetDown()
        Kind.Push -> pop()
    }
}
