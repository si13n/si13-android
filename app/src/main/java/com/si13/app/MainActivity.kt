package com.si13.app

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNavigationView.setupWithNavController(navController)

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
}
