package com.si13.forgetty

internal class TaskSourceTracker {
    private var hasObservedSource = false
    private var observedUserId: String? = null

    fun markObserved(userId: String?) {
        observedUserId = userId
        hasObservedSource = true
    }

    fun hasSourceChanged(currentUserId: String?): Boolean {
        return hasObservedSource && observedUserId != currentUserId
    }
}
