package com.si13.forgetty

internal object TaskSwipeBounds {
    private const val OPEN_THRESHOLD = 0.38f

    fun swipeOffset(baseOffset: Float, gestureOffset: Float, actionWidth: Float): Float {
        return (baseOffset + gestureOffset).coerceIn(-actionWidth, 0f)
    }

    fun overlayTranslation(swipeOffset: Float, actionWidth: Float): Float {
        return (actionWidth + swipeOffset).coerceIn(0f, actionWidth)
    }

    fun settleTarget(swipeOffset: Float, actionWidth: Float): Float {
        return if (swipeOffset <= -actionWidth * OPEN_THRESHOLD) -actionWidth else 0f
    }
}
