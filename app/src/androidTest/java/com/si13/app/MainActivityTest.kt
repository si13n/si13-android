package com.si13.app

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import io.qameta.allure.android.runners.AllureAndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AllureAndroidJUnit4::class)
class MainActivityTest {

    @Test
    fun launchesAfterRestart() {
        // First launch
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }

        // Second Launch
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }
}
