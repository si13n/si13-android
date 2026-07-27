package com.si13.app

import android.content.Context
import android.graphics.Rect
import android.view.View
import android.widget.CheckBox
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.ViewAssertion
import androidx.test.espresso.action.CoordinatesProvider
import androidx.test.espresso.action.GeneralSwipeAction
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Swipe
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.pressImeActionButton
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.action.ViewActions.swipeLeft
import androidx.test.espresso.action.ViewActions.swipeUp
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.Visibility.GONE
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.google.firebase.auth.FirebaseAuth
import io.qameta.allure.android.runners.AllureAndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
            onView(withId(R.id.task_character_counter)).check(matches(withEffectiveVisibility(GONE)))
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
            onView(withId(R.id.task_character_counter)).check(matches(withEffectiveVisibility(GONE)))
        }
    }

    @Test
    fun characterCounterAppearsAtOneHundredSeventyCharacters() {
        ActivityScenario.launch(MainActivity::class.java).use {
            continueAsGuest()

            onView(withId(R.id.task_input)).perform(replaceText("x".repeat(169)))
            onView(withId(R.id.task_character_counter)).check(matches(withEffectiveVisibility(GONE)))

            onView(withId(R.id.task_input)).perform(replaceText("x".repeat(170)))
            onView(withText("170 / 200")).check(matches(isDisplayed()))
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
            onView(withId(R.id.task_list)).check(taskIsCompletelyVisible("Guest task 1"))
        }
    }

    @Test
    fun bottomNavigationShowsLabelOnlyForSelectedDestination() {
        ActivityScenario.launch(MainActivity::class.java).use {
            continueAsGuest()

            onView(withId(R.id.homeFragment)).check(matches(withText(R.string.home)))
            onView(withId(R.id.profileFragment)).check(matches(withText("")))

            onView(withId(R.id.profileFragment)).perform(click())
            onView(withId(R.id.profileFragment)).check(matches(withText(R.string.profile)))
            onView(withId(R.id.homeFragment)).check(matches(withText("")))
        }
    }

    @Test
    fun sortMenuContainsTheDefaultPrioritySort() {
        ActivityScenario.launch(MainActivity::class.java).use {
            continueAsGuest()

            onView(withId(R.id.task_sort_button)).perform(click())

            onView(withText(R.string.sort_priority_first)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun headerAndProgressUseLiveTaskCounts() {
        seedGuestTasks(3)

        ActivityScenario.launch(MainActivity::class.java).use {
            continueAsGuest()

            onView(withText(R.string.today)).check(matches(isDisplayed()))
            onView(withId(R.id.home_date_text)).check(matches(isDisplayed()))
            onView(withText("3 active · 0 done")).check(matches(isDisplayed()))
            onView(withText("0 of 3 completed")).check(matches(isDisplayed()))

            onView(withText("Guest task 3")).perform(click())
            onView(isRoot()).perform(waitFor(500))

            onView(withText("2 active · 1 done")).check(matches(isDisplayed()))
            onView(withText("1 of 3 completed")).check(matches(isDisplayed()))
        }
    }

    @Test
    fun alphabeticalSortChangesTheActiveTaskOrder() {
        seedGuestTasks(3)

        ActivityScenario.launch(MainActivity::class.java).use {
            continueAsGuest()
            onView(withId(R.id.task_list)).check(firstTaskTextIs("Guest task 3"))

            onView(withId(R.id.task_sort_button)).perform(click())
            onView(withText(R.string.sort_alphabetical)).perform(click())

            onView(withId(R.id.task_list)).check(firstTaskTextIs("Guest task 1"))
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
    fun settingHighPriorityMovesTaskAboveNewerDefaultPriorityTasks() {
        seedGuestTasks(3)

        ActivityScenario.launch(MainActivity::class.java).use {
            continueAsGuest()
            onView(isRoot()).perform(waitFor(500))
            onView(withId(R.id.task_list)).check(firstTaskTextIs("Guest task 3"))

            onView(withId(R.id.task_list)).perform(clickPriorityForTask("Guest task 1"))
            onView(isRoot()).perform(waitFor(500))

            onView(withId(R.id.task_list)).check(firstTaskTextIs("Guest task 1"))
            onView(withId(R.id.task_list)).check(priorityDotIsEndAligned("Guest task 1"))

            onView(withId(R.id.task_list)).perform(clickPriorityForTask("Guest task 1"))
            onView(isRoot()).perform(waitFor(500))

            onView(withId(R.id.task_list)).check(firstTaskTextIs("Guest task 3"))

            val updatedTask = runBlocking {
                TaskDatabase.getInstance(context).taskDao().getTasks()
                    .single { task -> task.id == "guest-task-1" }
            }
            assertEquals(null, updatedTask.priority)
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
            onView(withText("Finish checklist")).check(matches(isDisplayed()))
            onView(withId(R.id.task_section_title)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun revealedDeleteActionDeletesOnFirstTap() {
        seedGuestTasks(1)

        ActivityScenario.launch(MainActivity::class.java).use {
            continueAsGuest()
            onView(isRoot()).perform(waitFor(500))

            onView(withText("Guest task 1")).perform(swipeLeft())
            onView(withText("Guest task 1")).check(matches(isDisplayed()))
            onView(allOf(withContentDescription(R.string.delete_task), isDisplayed())).perform(click())
            onView(isRoot()).perform(waitFor(500))

            onView(withText("Guest task 1")).check(doesNotExist())
            assertTrue(runBlocking { TaskDatabase.getInstance(context).taskDao().getTasks() }.isEmpty())
        }
    }

    @Test
    fun undoRestoresDeletedTask() {
        seedGuestTasks(1)

        ActivityScenario.launch(MainActivity::class.java).use {
            continueAsGuest()
            onView(isRoot()).perform(waitFor(500))

            onView(withText("Guest task 1")).perform(swipeLeft())
            onView(allOf(withContentDescription(R.string.delete_task), isDisplayed())).perform(click())
            onView(withText(R.string.undo)).perform(click())
            onView(isRoot()).perform(waitFor(500))

            onView(withText("Guest task 1")).check(matches(isDisplayed()))
            assertEquals(1, runBlocking { TaskDatabase.getInstance(context).taskDao().getTasks() }.size)
        }
    }

    @Test
    fun outsideTapClosesRevealWithoutDeletingTask() {
        seedGuestTasks(1)

        ActivityScenario.launch(MainActivity::class.java).use {
            continueAsGuest()
            onView(isRoot()).perform(waitFor(500))

            onView(withText("Guest task 1")).perform(swipeLeft())
            onView(withId(R.id.task_list)).check(taskRevealState("Guest task 1", true))

            onView(withId(R.id.home_title_text)).perform(click())
            onView(isRoot()).perform(waitFor(250))

            onView(withId(R.id.task_list)).check(taskRevealState("Guest task 1", false))
            onView(withText("Guest task 1")).check(matches(isDisplayed()))
        }
    }

    @Test
    fun rightSwipeClosesRevealWithoutMovingRowOrDeletingTask() {
        seedGuestTasks(1)

        ActivityScenario.launch(MainActivity::class.java).use {
            continueAsGuest()
            onView(isRoot()).perform(waitFor(500))

            onView(withText("Guest task 1")).perform(swipeLeft())
            onView(withId(R.id.task_list)).check(taskRevealState("Guest task 1", true))

            onView(withId(R.id.task_list)).perform(swipeTaskRight("Guest task 1"))
            onView(isRoot()).perform(waitFor(250))

            onView(withId(R.id.task_list)).check(taskRevealState("Guest task 1", false))
            onView(withText("Guest task 1")).check(matches(isDisplayed()))
        }
    }

    @Test
    fun openingSecondTaskClosesFirstReveal() {
        seedGuestTasks(2)

        ActivityScenario.launch(MainActivity::class.java).use {
            continueAsGuest()
            onView(isRoot()).perform(waitFor(500))

            onView(withText("Guest task 2")).perform(swipeLeft())
            onView(withText("Guest task 1")).perform(swipeLeft())
            onView(isRoot()).perform(waitFor(250))

            onView(withId(R.id.task_list)).check(taskRevealState("Guest task 2", false))
            onView(withId(R.id.task_list)).check(taskRevealState("Guest task 1", true))
        }
    }

    @Test
    fun tappingAnotherTaskClosesRevealAndPreservesTheTap() {
        seedGuestTasks(2)

        ActivityScenario.launch(MainActivity::class.java).use {
            continueAsGuest()
            onView(isRoot()).perform(waitFor(500))

            onView(withText("Guest task 2")).perform(swipeLeft())
            onView(withText("Guest task 1")).perform(click())
            onView(isRoot()).perform(waitFor(500))

            onView(withId(R.id.task_list)).check(taskRevealState("Guest task 2", false))
            onView(withText("Guest task 1")).check(doesNotExist())
        }
    }

    @Test
    fun scrollingClosesRevealWithoutDeletingTask() {
        seedGuestTasks(30)

        ActivityScenario.launch(MainActivity::class.java).use {
            continueAsGuest()
            onView(isRoot()).perform(waitFor(500))

            onView(withText("Guest task 30")).perform(swipeLeft())
            onView(withId(R.id.task_list)).perform(swipeUp())
            onView(withId(R.id.task_list)).perform(scrollRecyclerToPosition(0))
            onView(isRoot()).perform(waitFor(250))

            onView(withId(R.id.task_list)).check(taskRevealState("Guest task 30", false))
            assertEquals(30, runBlocking { TaskDatabase.getInstance(context).taskDao().getTasks() }.size)
        }
    }

    @Test
    fun deleteAllTasksRequiresConfirmation() {
        seedGuestTasks(3)

        ActivityScenario.launch(MainActivity::class.java).use {
            continueAsGuest()
            onView(isRoot()).perform(waitFor(500))

            onView(withId(R.id.profileFragment)).perform(click())
            onView(withId(R.id.profile_delete_all_tasks_row)).perform(scrollTo(), click())
            onView(withText(R.string.delete_all_tasks_title))
                .inRoot(isDialog())
                .check(matches(isDisplayed()))

            onView(withText(R.string.cancel))
                .inRoot(isDialog())
                .perform(click())
            assertEquals(3, runBlocking { TaskDatabase.getInstance(context).taskDao().getTasks() }.size)

            onView(withId(R.id.profile_delete_all_tasks_row)).perform(scrollTo(), click())
            onView(withText(R.string.delete))
                .inRoot(isDialog())
                .perform(click())
            onView(isRoot()).perform(waitFor(500))

            onView(withId(R.id.homeFragment)).perform(click())
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
        FirebaseAuth.getInstance().signOut()
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

    private fun swipeTaskRight(taskText: String): ViewAction {
        fun taskCoordinates(horizontalFraction: Float) = CoordinatesProvider { view ->
            val recyclerView = view as RecyclerView
            val child = (0 until recyclerView.childCount)
                .map(recyclerView::getChildAt)
                .firstOrNull { item ->
                    item.findViewById<CheckBox>(R.id.task_checkbox)?.text == taskText
                }
                ?: throw AssertionError("Could not find visible task '$taskText'.")
            val location = IntArray(2)
            child.getLocationOnScreen(location)
            floatArrayOf(
                location[0] + child.width * horizontalFraction,
                location[1] + child.height / 2f
            )
        }

        return GeneralSwipeAction(
            Swipe.FAST,
            taskCoordinates(0.25f),
            taskCoordinates(0.85f),
            Press.FINGER
        )
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
                    if (checkbox?.text == taskText) {
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

    private fun taskIsCompletelyVisible(taskText: String): ViewAssertion {
        return ViewAssertion { view, _ ->
            val recyclerView = view as RecyclerView
            for (index in 0 until recyclerView.childCount) {
                val child = recyclerView.getChildAt(index)
                val checkbox = child.findViewById<CheckBox>(R.id.task_checkbox)
                if (checkbox?.text == taskText) {
                    assertTrue(
                        "Task '$taskText' is clipped within the RecyclerView.",
                        child.top >= 0 && child.bottom <= recyclerView.height
                    )
                    return@ViewAssertion
                }
            }

            throw AssertionError("Could not find visible task '$taskText'.")
        }
    }

    private fun priorityDotIsEndAligned(taskText: String): ViewAssertion {
        return ViewAssertion { view, _ ->
            val recyclerView = view as RecyclerView
            for (index in 0 until recyclerView.childCount) {
                val child = recyclerView.getChildAt(index)
                val checkbox = child.findViewById<CheckBox>(R.id.task_checkbox)
                if (checkbox?.text == taskText) {
                    val dot = child.findViewById<View>(R.id.task_priority_dot)
                    val rowBounds = Rect()
                    val dotBounds = Rect()
                    assertTrue(dot.isShown)
                    assertTrue(child.getGlobalVisibleRect(rowBounds))
                    assertTrue(dot.getGlobalVisibleRect(dotBounds))
                    val maximumEndOffset = 28f * child.resources.displayMetrics.density
                    assertTrue(rowBounds.right - dotBounds.exactCenterX() <= maximumEndOffset)
                    return@ViewAssertion
                }
            }

            throw AssertionError("Could not find visible task '$taskText'.")
        }
    }

    private fun taskRevealState(taskText: String, expectedRevealed: Boolean): ViewAssertion {
        return ViewAssertion { view, _ ->
            val recyclerView = view as RecyclerView
            for (index in 0 until recyclerView.childCount) {
                val child = recyclerView.getChildAt(index)
                val checkbox = child.findViewById<CheckBox>(R.id.task_checkbox)
                if (checkbox?.text == taskText) {
                    val foreground = child.findViewById<View>(R.id.task_foreground_container)
                    val deleteAction = child.findViewById<View>(R.id.task_delete_action)
                    if (expectedRevealed) {
                        assertEquals(
                            child.resources.getDimensionPixelSize(R.dimen.task_delete_action_width),
                            deleteAction.width
                        )
                        assertTrue(
                            "The task foreground must remain mostly visible.",
                            deleteAction.width < foreground.width / 2
                        )
                        assertTrue(
                            "The delete action must draw above the task foreground.",
                            deleteAction.z > foreground.z
                        )
                        assertEquals(0f, deleteAction.translationX, 1f)
                    }
                    assertEquals(0f, child.translationX, 0f)
                    assertEquals(0f, foreground.translationX, 0f)
                    return@ViewAssertion
                }
            }

            throw AssertionError("Could not find visible task '$taskText'.")
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
