package dev.southstar21.kaloria.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.navigation3.ui.NavDisplay

/** Springs shared by every transition, so the app moves with one consistent feel. */
private fun <T> spatial() = spring<T>(dampingRatio = 0.82f, stiffness = 380f)

/** Pushed screens (search, editor, scanners) rise in over the current one. */
internal fun riseIn(): ContentTransform =
    (slideInVertically(spatial()) { it / 10 } + fadeIn(tween(220)) + scaleIn(spatial(), initialScale = 0.96f))
        .togetherWith(fadeOut(tween(160)) + scaleOut(tween(220), targetScale = 0.98f))

/** …and drop back down when closed. */
internal fun dropOut(): ContentTransform =
    (fadeIn(tween(220)) + scaleIn(tween(260), initialScale = 0.98f))
        .togetherWith(slideOutVertically(spatial()) { it / 8 } + fadeOut(tween(180)) + scaleOut(tween(220), targetScale = 0.96f))

/** Predictive back: the closing screen shrinks and fades as the gesture progresses. */
internal fun shrinkBack(): ContentTransform =
    fadeIn(tween(200)).togetherWith(scaleOut(tween(300), targetScale = 0.88f) + fadeOut(tween(300)))

/** Switching tabs: Material fade-through, out quickly, then in with a small scale-up. */
internal fun fadeThrough(): ContentTransform =
    (fadeIn(tween(220, delayMillis = 90)) + scaleIn(spatial(), initialScale = 0.92f))
        .togetherWith(fadeOut(tween(90)))

/** Metadata for top-level tab entries, overriding the push/pop defaults with fade-through. */
internal val TopLevelMetadata: Map<String, Any> =
    NavDisplay.transitionSpec { fadeThrough() } +
        NavDisplay.popTransitionSpec { fadeThrough() } +
        NavDisplay.predictivePopTransitionSpec { fadeThrough() }
