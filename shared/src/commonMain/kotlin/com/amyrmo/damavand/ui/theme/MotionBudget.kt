package com.amyrmo.damavand.ui.theme

import androidx.compose.runtime.compositionLocalOf

enum class MotionBudget { Full, Throttled, Paused }

val LocalMotionBudget = compositionLocalOf { MotionBudget.Full }
