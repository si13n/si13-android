package com.si13.forgetty

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var navController: NavController
    private lateinit var navHostFragment: NavHostFragment
    fun setBottomNavigationVisible(visible: Boolean) {
        findViewById<View>(R.id.bottom_navigation)?.visibility = if (visible) View.VISIBLE else View.GONE
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        setContentView(R.layout.activity_main)

        navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController
        val bottomNavigationView = findViewById<View>(R.id.bottom_navigation)
        val homeButton = findViewById<MaterialButton>(R.id.homeFragment)
        val profileButton = findViewById<MaterialButton>(R.id.profileFragment)
        val statsButton = findViewById<MaterialButton>(R.id.statsFragment)
        val addTaskButton = findViewById<MaterialButton>(R.id.add_task_fab)

        homeButton.setOnClickListener { navigateTo(navController, R.id.homeFragment) }
        profileButton.setOnClickListener { navigateTo(navController, R.id.profileFragment) }
        statsButton.setOnClickListener { navigateTo(navController, R.id.statsFragment) }
        addTaskButton.setOnClickListener {
            navigateTo(navController, R.id.homeFragment)
            addTaskButton.post {
                (navHostFragment.childFragmentManager.primaryNavigationFragment as? HomeFragment)
                    ?.showAddTaskSheet()
            }
        }
        navController.addOnDestinationChangedListener { _, destination, _ ->
            updateNavigationButton(homeButton, destination.id == R.id.homeFragment)
            updateNavigationButton(profileButton, destination.id == R.id.profileFragment)
            updateNavigationButton(statsButton, destination.id == R.id.statsFragment)
        }

        if (savedInstanceState == null) {
            if (AuthRepository(this).isAuthenticated()) {
                lifecycleScope.launch {
                    TaskImportDialogFragment.showIfLocalTasks(
                        this@MainActivity,
                        supportFragmentManager
                    )
                }
            } else {
                LoginBottomSheet().show(supportFragmentManager, LoginBottomSheet.TAG)
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            bottomNavigationView.setPadding(
                bottomNavigationView.paddingLeft,
                bottomNavigationView.paddingTop,
                bottomNavigationView.paddingRight,
                systemBars.bottom
            )
            insets
        }
        handleExtensionIntent(intent)
        seedDebugTasksIfNeeded()
    }

    private fun seedDebugTasksIfNeeded() {
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        val preferences = getPreferences(MODE_PRIVATE)
        if (preferences.getBoolean(DEMO_SEED_KEY, false)) return
        lifecycleScope.launch {
            DemoTaskSeeder.seedIfEmpty(TaskRepository.create(applicationContext))
            preferences.edit().putBoolean(DEMO_SEED_KEY, true).apply()
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleExtensionIntent(intent)
    }

    private fun handleExtensionIntent(intent: android.content.Intent?) {
        val action = intent?.action
        val taskId = intent?.getStringExtra(EXTRA_TASK_ID)
        if (action !in setOf(ACTION_ADD_TASK, ACTION_TODAY_TASKS, ACTION_VOICE_TASK, ACTION_SEARCH_TASKS) && taskId == null) return
        navigateTo(navController, R.id.homeFragment)
        findViewById<View>(R.id.nav_host_fragment).post {
            val home = navHostFragment.childFragmentManager.primaryNavigationFragment as? HomeFragment
            when (action) {
                ACTION_ADD_TASK -> home?.showAddTaskSheet()
                ACTION_VOICE_TASK -> home?.showAddTaskSheet(startVoice = true)
                ACTION_TODAY_TASKS -> home?.showTodayFromExtension()
                ACTION_SEARCH_TASKS -> home?.openSearchFromExtension()
            }
            taskId?.let { home?.openTaskFromExtension(it) }
            intent?.removeExtra(EXTRA_TASK_ID)
            intent?.action = null
        }
    }

    private fun navigateTo(navController: NavController, destinationId: Int) {
        if (navController.currentDestination?.id == destinationId) return
        val options = NavOptions.Builder()
            .setLaunchSingleTop(true)
            .setRestoreState(true)
            .setPopUpTo(navController.graph.startDestinationId, false, true)
            .build()
        navController.navigate(destinationId, null, options)
    }

    private fun updateNavigationButton(
        button: MaterialButton,
        selected: Boolean
    ) {
        button.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
        val contentColor = MaterialColors.getColor(
            button,
            if (selected) {
                androidx.appcompat.R.attr.colorPrimary
            } else {
                com.google.android.material.R.attr.colorOnSurfaceVariant
            }
        )
        button.iconTint = ColorStateList.valueOf(
            contentColor
        )
        button.setTextColor(contentColor)
    }

    companion object {
        const val ACTION_ADD_TASK = "com.si13.forgetty.ADD_TASK"
        const val ACTION_TODAY_TASKS = "com.si13.forgetty.TODAY_TASKS"
        const val ACTION_VOICE_TASK = "com.si13.forgetty.VOICE_TASK"
        const val ACTION_SEARCH_TASKS = "com.si13.forgetty.SEARCH_TASKS"
        const val DEMO_SEED_KEY = "demo_seed_v1"
    }
}
