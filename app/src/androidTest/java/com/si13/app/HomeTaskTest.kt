package com.si13.app

import android.content.Context
import android.view.View
import android.widget.CheckBox
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.ViewAssertion
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
import org.junit.Assert.assertEquals
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
    fun rapidAddCallbacksCreateOneGuestTask() {
        ActivityScenario.launch(MainActivity::class.java).use {
            continueAsGuest()

            onView(withId(R.id.task_input)).perform(replaceText("Add once"))
            onView(withId(R.id.add_task_button)).perform(callClickTwice())
            onView(isRoot()).perform(waitFor(500))

            val matchingTasks = runBlocking {
                TaskDatabase.getInstance(context).taskDao().getTasks()
                    .filter { task -> task.text == "Add once" }
            }
            assertEquals(1, matchingTasks.size)
            onView(withText("Add once")).check(matches(isDisplayed()))
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
    fun scrollsThroughLongGuestTaskList() {
        seedGuestTasks(30)

        ActivityScenario.launch(MainActivity::class.java).use {
            continueAsGuest()
            onView(isRoot()).perform(waitFor(500))

            onView(withId(R.id.task_list)).perform(scrollRecyclerToPosition(29))
            onView(withText("Guest task 1")).check(matches(isDisplayed()))
        }
    }

    @Test
    fun addingGuestTaskFromScrolledListReturnsToTop() {
        seedGuestTasks(30)

        ActivityScenario.launch(MainActivity::class.java).use {
            continueAsGuest()
            onView(isRoot()).perform(waitFor(500))
            onView(withId(R.id.task_list)).perform(scrollRecyclerToPosition(29))

            onView(withId(R.id.task_input)).perform(replaceText("Newest task"))
            onView(withId(R.id.add_task_button)).perform(click())
            onView(isRoot()).perform(waitFor(500))

            onView(withText("Newest task")).check(matches(isDisplayed()))
        }
    }

    @Test
    fun settingHighPriorityMovesTaskToTop() {
        seedGuestTasks(3)

        ActivityScenario.launch(MainActivity::class.java).use {
            continueAsGuest()
            onView(isRoot()).perform(waitFor(500))
            onView(withId(R.id.task_list)).check(firstTaskTextIs("Guest task 3"))

            onView(withId(R.id.task_list)).perform(clickPriorityForTask("Guest task 1"))
            onView(isRoot()).perform(waitFor(500))

            onView(withId(R.id.task_list)).check(firstTaskTextIs("Guest task 1"))
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

            onView(withId(R.id.task_settings_button)).perform(click())
            onView(withText(R.string.show_completed))
                .inRoot(isDialog())
                .perform(click())
            onView(withText("Finish checklist")).check(matches(isDisplayed()))
        }
    }

    @Test
    fun deleteAllTasksRequiresConfirmation() {
        seedGuestTasks(3)

        ActivityScenario.launch(MainActivity::class.java).use {
            continueAsGuest()
            onView(isRoot()).perform(waitFor(500))

            onView(withId(R.id.task_settings_button)).perform(click())
            onView(withText(R.string.delete_all_tasks))
                .inRoot(isDialog())
                .perform(click())
            onView(withText(R.string.delete_all_tasks_title))
                .inRoot(isDialog())
                .check(matches(isDisplayed()))

            onView(withText(R.string.cancel))
                .inRoot(isDialog())
                .perform(click())
            onView(withText("Guest task 3")).check(matches(isDisplayed()))

            onView(withId(R.id.task_settings_button)).perform(click())
            onView(withText(R.string.delete_all_tasks))
                .inRoot(isDialog())
                .perform(click())
            onView(withText(R.string.delete))
                .inRoot(isDialog())
                .perform(click())
            onView(isRoot()).perform(waitFor(500))

            onView(withText(R.string.tasks_empty)).check(matches(isDisplayed()))
            val remainingTasks = runBlocking {
                TaskDatabase.getInstance(context).taskDao().getTasks()
            }
            assertEquals(0, remainingTasks.size)
        }
    }

    @Test
    fun showsLocalTaskImportDialogForAuthenticatedUserWithGuestTasks() {
        // Seed both sides of the condition: a saved authenticated user and pending guest tasks.
        seedAuthenticatedUserWithGuestTasks()

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

    @Test
    fun discardLocalTaskImportClosesDialog() {
        seedAuthenticatedUserWithGuestTasks()

        ActivityScenario.launch(MainActivity::class.java).use {
            waitForDialogStartup()

            onView(withText(R.string.import_local_tasks_discard))
                .inRoot(isDialog())
                .perform(click())

            onView(withText(R.string.import_local_tasks_title)).check(doesNotExist())
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

    private fun seedAuthenticatedUserWithGuestTasks() {
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
    }

    private fun seedGuestTasks(count: Int) {
        runBlocking {
            TaskDatabase.getInstance(context).taskDao().upsertAll(
                (1..count).map { index ->
                    TaskEntity(
                        id = "guest-task-$index",
                        text = "Guest task $index",
                        completed = false,
                        createdAt = index.toLong(),
                        updatedAt = index.toLong()
                    )
                }
            )
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

    private fun scrollRecyclerToPosition(position: Int): ViewAction {
        return object : ViewAction {
            override fun getConstraints(): Matcher<View> {
                return isDisplayed()
            }

            override fun getDescription(): String {
                return "Scroll RecyclerView to adapter position $position."
            }

            override fun perform(uiController: UiController, view: View) {
                (view as RecyclerView).scrollToPosition(position)
                uiController.loopMainThreadUntilIdle()
            }
        }
    }

    private fun clickPriorityForTask(taskText: String): ViewAction {
        return object : ViewAction {
            override fun getConstraints(): Matcher<View> {
                return isDisplayed()
            }

            override fun getDescription(): String {
                return "Click priority button for task '$taskText'."
            }

            override fun perform(uiController: UiController, view: View) {
                val recyclerView = view as RecyclerView
                for (index in 0 until recyclerView.childCount) {
                    val child = recyclerView.getChildAt(index)
                    val checkbox = child.findViewById<CheckBox>(R.id.task_checkbox)
                    if (checkbox.text == taskText) {
                        child.findViewById<View>(R.id.task_priority_button).performClick()
                        uiController.loopMainThreadUntilIdle()
                        return
                    }
                }

                throw AssertionError("Could not find visible task '$taskText'.")
            }
        }
    }

    private fun firstTaskTextIs(expectedText: String): ViewAssertion {
        return ViewAssertion { view, _ ->
            val recyclerView = view as RecyclerView
            val firstTaskText = recyclerView.getChildAt(0)
                ?.findViewById<CheckBox>(R.id.task_checkbox)
                ?.text
                ?.toString()
            assertEquals(expectedText, firstTaskText)
        }
    }

    private fun callClickTwice(): ViewAction {
        return object : ViewAction {
            override fun getConstraints(): Matcher<View> {
                return isDisplayed()
            }

            override fun getDescription(): String {
                return "Call click listener twice in the same UI loop."
            }

            override fun perform(uiController: UiController, view: View) {
                view.callOnClick()
                view.callOnClick()
                uiController.loopMainThreadUntilIdle()
            }
        }
    }
}
