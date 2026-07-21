package com.si13.app

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import io.qameta.allure.android.runners.AllureAndroidJUnit4
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AllureAndroidJUnit4::class)
class LoginBottomSheetTest {

    @Before
    fun clearAuthStateBeforeTest() {
        clearAuthState()
    }

    @After
    fun clearAuthStateAfterTest() {
        clearAuthState()
    }

    @Test
    fun opensOnUnauthenticatedLaunch() {
        launchUnauthenticatedActivity().use {
            assertLoginBottomSheetDisplayed()
        }
    }

    @Test
    fun closesWhenCloseButtonClicked() {
        launchUnauthenticatedActivity().use {
            assertLoginBottomSheetDisplayed()

            // Use the explicit close control so the test does not depend on device Back/swipe behavior.
            onView(withId(R.id.close_login_bottom_sheet_button)).perform(click())

            assertLoginBottomSheetDismissed()
        }
    }

    @Test
    fun continueAsGuestClosesModal() {
        launchUnauthenticatedActivity().use {
            onView(withId(R.id.continue_as_guest_button)).perform(click())

            assertLoginBottomSheetDismissed()
        }
    }

    @Test
    fun dismissedModalDoesNotReopenAfterBackgroundAndForeground() {
        launchUnauthenticatedActivity().use { scenario ->
            onView(withId(R.id.continue_as_guest_button)).perform(click())
            assertLoginBottomSheetDismissed()

            // Background/foreground should not recreate a dismissed launch-only prompt.
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)

            assertLoginBottomSheetDismissed()
        }
    }

    private fun launchUnauthenticatedActivity(): ActivityScenario<MainActivity> {
        // The bottom sheet is only shown for unauthenticated users on a fresh activity launch.
        clearAuthState()
        return ActivityScenario.launch(MainActivity::class.java)
    }

    private fun assertLoginBottomSheetDisplayed() {
        onView(withId(R.id.login_bottom_sheet_title)).check(matches(isDisplayed()))
        onView(withId(R.id.sign_in_with_google_button)).check(matches(isDisplayed()))
        onView(withId(R.id.continue_as_guest_button)).check(matches(isDisplayed()))
    }

    private fun assertLoginBottomSheetDismissed() {
        onView(withId(R.id.login_bottom_sheet_title)).check(doesNotExist())
    }

    private fun clearAuthState() {
        AuthRepository(ApplicationProvider.getApplicationContext()).clear()
    }
}
