package com.si13.forgetty

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileUiStateTest {
    private val user = AuthUser("user-1", "Ada", "ada@example.com", null)

    @Test
    fun profileCardRequiresSignedInUser() {
        assertTrue(ProfileUiState(user = user, isOnline = true).showProfileCard)
        assertTrue(ProfileUiState(user = user, isOnline = false).showProfileCard)
        assertFalse(ProfileUiState(user = null, isOnline = true).showProfileCard)
    }

    @Test
    fun signOutVisibilityRequiresSignedInUser() {
        assertTrue(ProfileUiState(user = user).showSignOut)
        assertFalse(ProfileUiState().showSignOut)
    }

    @Test
    fun progressCalculatesEmptyMixedAndCompleteTaskLists() {
        assertEquals(ProfileProgress(0, 0, 0), ProfileProgress.from(emptyList()))
        assertEquals(
            ProfileProgress(1, 2, 33),
            ProfileProgress.from(listOf(task(false), task(true), task(false)))
        )
        assertEquals(ProfileProgress(2, 0, 100), ProfileProgress.from(listOf(task(true), task(true))))
    }

    private fun task(completed: Boolean) = Task("id-$completed", "Task", completed, 1, 1)
}
