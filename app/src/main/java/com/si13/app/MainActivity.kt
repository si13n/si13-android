package com.si13.app

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContentView(R.layout.activity_main)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        val bottomNavigationView = findViewById<View>(R.id.bottom_navigation)
        val homeButton = findViewById<MaterialButton>(R.id.homeFragment)
        val profileButton = findViewById<MaterialButton>(R.id.profileFragment)

        homeButton.setOnClickListener { navigateTo(navController, R.id.homeFragment) }
        profileButton.setOnClickListener { navigateTo(navController, R.id.profileFragment) }
        navController.addOnDestinationChangedListener { _, destination, _ ->
            updateNavigationButton(homeButton, destination.id == R.id.homeFragment, R.string.home)
            updateNavigationButton(
                profileButton,
                destination.id == R.id.profileFragment,
                R.string.profile
            )
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
        selected: Boolean,
        labelRes: Int
    ) {
        button.text = if (selected) getString(labelRes) else ""
        button.contentDescription = getString(labelRes)
        button.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(
                this,
                if (selected) R.color.home_accent else android.R.color.transparent
            )
        )
        button.iconTint = ColorStateList.valueOf(
            ContextCompat.getColor(
                this,
                if (selected) R.color.white else R.color.home_text_secondary
            )
        )
        button.setTextColor(
            ContextCompat.getColor(
                this,
                if (selected) R.color.white else R.color.home_text_secondary
            )
        )
    }
}
