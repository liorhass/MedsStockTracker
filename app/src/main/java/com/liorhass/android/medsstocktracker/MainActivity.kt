//
// todo: fancy logging: https://medium.com/@.me./android-logging-on-steroids-clickable-logs-with-location-info-de1a5c16e86f
// todo: SharedPreferences and Delegated Properties in Kotlin: https://medium.com/@krzychukosobudzki/sharedpreferences-and-delegated-properties-in-kotlin-5437feeb254d
// todo: search for any leftover findViewById in all files. should be replaced w/ binding
// todo: Themes:
//    https://medium.com/monzo-bank/refactoring-android-themes-with-style-restructuring-themes-15230569e50
//    when all is working, move to new material-components (dark themes, etc): https://medium.com/androiddevelopers/migrating-to-material-components-for-android-ec6757795351
// todo: go over:
//     https://proandroiddev.com/5-common-mistakes-when-using-architecture-components-403e9899f4cb
//     https://www.raywenderlich.com/8279305-navigation-component-for-android-part-3-transition-and-navigation
//     animate transitions between fragments:  https://developer.android.com/guide/navigation/navigation-animate-transitions
//     https://www.raywenderlich.com/1560485-android-recyclerview-tutorial-with-kotlin
//
// todo: Play Store deployment article: https://www.raywenderlich.com/6569516-android-app-distribution-tutorial-from-zero-to-google-play-store
//
package com.liorhass.android.medsstocktracker

import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import androidx.preference.PreferenceManager
import com.liorhass.android.medsstocktracker.databinding.ActivityMainBinding
import com.liorhass.android.medsstocktracker.notifications.createNotificationChannels
import com.liorhass.android.medsstocktracker.notifications.scheduleOrCancelNotificationsWork
import com.liorhass.android.medsstocktracker.util.hideSoftKeyboard
import timber.log.Timber

class MainActivity : AppCompatActivity(), NavController.OnDestinationChangedListener  {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get our navController. This should have been as simple as:
        //      val navController = findNavController(R.id.nav_host_fragment)
        // instead, we use the following code to bypass a bug: https://stackoverflow.com/a/58859118/1071117
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        // Set the toolbar to act as the ActionBar for this Activity window
        setSupportActionBar(binding.mainAppScreen.toolbar)

        // For some reason, without this, the initial title is the app name instead of the displayed fragment's title
        supportActionBar?.title = getString(R.string.fragment_title_medicines)

        // Configuration options for NavigationUI methods that interact with our Toolbar
        // https://developer.android.com/guide/navigation/navigation-ui#create_a_toolbar
        val appBarConfiguration = AppBarConfiguration(
            setOf(R.id.medicineListFragment/*, R.id.event_list_fragment*/), binding.drawerLayout)
        binding.mainAppScreen.toolbar.setupWithNavController(navController, appBarConfiguration)
        binding.navView.setupWithNavController(navController)

        // Ask to be notified on navigation events. This is so we can close the keyboard when necessary.
        navController.addOnDestinationChangedListener(this)

        // Schedule our notification service (or cancel it) according to user's preferences in settings
        scheduleOrCancelNotificationsWork(applicationContext)

        doOneTimeInitializationStuff()

        Timber.v("onCreate() done")
    }

    // If the system back button is pressed while the nav-drawer is open, close it
    override fun onBackPressed() {
        val drawer = binding.drawerLayout
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestinationChanged(controller: NavController, destination: NavDestination,
                                      arguments: Bundle?) {
        // Sometimes the keyboard is left open when navigating back to this fragment (e.g. When the
        // user clicks on "Cancel" while keyboard is open). So we force-close it here.
        if(destination.id == R.id.medicineListFragment) {
            Timber.v("Hiding soft keyboard")
            hideSoftKeyboard(this)
        }
    }

    private fun doOneTimeInitializationStuff() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val sharedPreferences: SharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(this)
            if (! sharedPreferences.getBoolean(
                    Constants.NOTIFICATION_CHANNELS_INITIALIZED_FLAG, false)) {
                // Register our notification channels with the system. This is something
                // that is only done once after the app is first install.
                createNotificationChannels(this)
                sharedPreferences.edit().putBoolean(
                    Constants.NOTIFICATION_CHANNELS_INITIALIZED_FLAG, true).apply()
            }
        }
    }

    private object Constants {
        const val NOTIFICATION_CHANNELS_INITIALIZED_FLAG = "nci"
    }
}
