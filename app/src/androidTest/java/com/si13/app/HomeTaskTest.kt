package com.si13.app

import android.content.Context
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.pressImeActionButton
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import io.qameta.allure.android.runners.AllureAndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matcher
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AllureAndroidJUnit4::class)
class HomeTaskTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clearStateBeforeTest() {
        clearState()
    }

    @After
    fun clearStateAfterTest() {
        clearState()
    }

    @Test
    fun addsGuestTask() {
        ActivityScenario.launch(MainActivity::class.java).use {
            continueAsGuest()

            onView(withId(R.id.task_input)).perform(replaceText("Buy milk"))
            onView(withId(R.id.add_task_button)).perform(click())
            onView(isRoot()).perform(waitFor(500))

            onView(withText("Buy milk")).check(matches(isDisplayed()))
            onView(withText("0 / 200")).check(matches(isDisplayed()))
        }
    }

    @Test
    fun addsGuestTaskFromKeyboardEnter() {
        ActivityScenario.launch(MainActivity::class.java).use {
            continueAsGuest()

            onView(withId(R.id.task_input)).perform(replaceText("Submit with enter"))
            onView(withId(R.id.task_input)).perform(pressImeActionButton())
            onView(isRoot()).perform(waitFor(500))

            onView(withText("Submit with enter")).check(matches(isDisplayed()))
            onView(withText("0 / 200")).check(matches(isDisplayed()))
        }
    }

    @Test
    fun completingTaskHidesItUntilCompletedTasksAreShown() {
        ActivityScenario.launch(MainActivity::class.java).use {
            continueAsGuest()

            onView(withId(R.id.task_input)).perform(replaceText("Finish checklist"))
            onView(withId(R.id.add_task_button)).perform(click())
            onView(isRoot()).perform(waitFor(500))

            onView(withText("Finish checklist")).perform(click())
            onView(isRoot()).perform(waitFor(500))
            onView(withText("Finish checklist")).check(doesNotExist())

            onView(withId(R.id.show_completed_button)).perform(click())
            onView(withText("Finish checklist")).check(matches(isDisplayed()))
        }
    }

    @Test
    fun showsLocalTaskImportDialogForAuthenticatedUserWithGuestTasks() {
        // Seed both sides of the condition: a saved authenticated user and pending guest tasks.
        runBlocking {
            TaskDatabase.getInstance(context).taskDao().upsert(
                TaskEntity(
                    id = "guest-task-1",
                    text = "Guest task",
                    completed = false,
                    createdAt = 1L,
                    updatedAt = 1L
                )
            )
        }
        AuthRepository(context).saveAuthenticatedUser(
            uid = "test-user",
            displayName = "Test User",
            email = "test@example.com",
            photoUrl = null
        )

        ActivityScenario.launch(MainActivity::class.java).use {
            waitForDialogStartup()

            onView(withText(R.string.import_local_tasks_title))
                .inRoot(isDialog())
                .check(matches(isDisplayed()))
            onView(withText(R.string.import_local_tasks_add))
                .inRoot(isDialog())
                .check(matches(isDisplayed()))
            onView(withText(R.string.import_local_tasks_discard))
                .inRoot(isDialog())
                .check(matches(isDisplayed()))
        }
    }

    private fun continueAsGuest() {
        // Most Home tests run as a guest, so dismiss the launch sign-in prompt first.
        onView(withId(R.id.continue_as_guest_button)).perform(click())
    }

    private fun clearState() {
        // Keep every test independent from previous auth snapshots and Room rows.
        AuthRepository(context).clear()
        runBlocking {
            TaskDatabase.getInstance(context).taskDao().deleteAll()
        }
    }

    private fun waitForDialogStartup() {
        // The import check runs from an Activity coroutine; avoid touching the base root while dialog focus changes.
        Thread.sleep(500)
    }

    private fun waitFor(milliseconds: Long): ViewAction {
        return object : ViewAction {
            override fun getConstraints(): Matcher<View> {
                return isRoot()
            }

            override fun getDescription(): String {
                return "Wait for async Room/UI work to settle for $milliseconds milliseconds."
            }

            override fun perform(uiController: UiController, view: View) {
                uiController.loopMainThreadForAtLeast(milliseconds)
            }
        }
    }
}
