package com.si13.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskSourceTrackerTest {
    @Test
    fun guestToAuthenticatedUserChangesTaskSource() {
        val tracker = TaskSourceTracker()
        tracker.markObserved(null)

        assertTrue(tracker.hasSourceChanged("user-1"))
    }

    @Test
    fun sameAuthenticatedUserDoesNotRestartTaskSource() {
        val tracker = TaskSourceTracker()
        tracker.markObserved("user-1")

        assertFalse(tracker.hasSourceChanged("user-1"))
    }

    @Test
    fun authenticatedUserToGuestChangesTaskSource() {
        val tracker = TaskSourceTracker()
        tracker.markObserved("user-1")

        assertTrue(tracker.hasSourceChanged(null))
    }
}
