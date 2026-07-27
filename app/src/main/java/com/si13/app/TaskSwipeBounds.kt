package com.si13.app

internal object TaskSwipeBounds {
    private const val OPEN_THRESHOLD = 0.38f

    fun translation(baseOffset: Float, gestureOffset: Float, actionWidth: Float): Float {
        return (baseOffset + gestureOffset).coerceIn(-actionWidth, 0f)
    }

    fun settleTarget(translation: Float, actionWidth: Float): Float {
        return if (translation <= -actionWidth * OPEN_THRESHOLD) -actionWidth else 0f
    }
}
