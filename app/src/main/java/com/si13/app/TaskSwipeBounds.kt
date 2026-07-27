package com.si13.app

internal object TaskSwipeBounds {
    fun translation(baseOffset: Float, gestureOffset: Float, actionWidth: Float): Float {
        return (baseOffset + gestureOffset).coerceIn(-actionWidth, 0f)
    }
}
