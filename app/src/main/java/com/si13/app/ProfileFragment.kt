package com.si13.app

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import coil.load
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class ProfileFragment : Fragment(R.layout.fragment_profile) {
    private val users = MutableStateFlow<AuthUser?>(null)
    private lateinit var authRepository: AuthRepository
    private lateinit var viewModel: ProfileViewModel
    private lateinit var profilePicture: ImageView
    private lateinit var avatarInitial: TextView
    private lateinit var displayName: TextView
    private lateinit var email: TextView
    private lateinit var completedValue: TextView
    private lateinit var activeValue: TextView
    private lateinit var rateValue: TextView
    private lateinit var completedValueVertical: TextView
    private lateinit var activeValueVertical: TextView
    private lateinit var rateValueVertical: TextView
    private lateinit var progress: LinearProgressIndicator
    private lateinit var signInButton: View
    private lateinit var signOutButton: MaterialButton
    private lateinit var guestContainer: View
    private lateinit var accountCard: View
    private lateinit var metricsHorizontal: View
    private lateinit var metricsVertical: View
    private lateinit var errorText: TextView

    private val authStateListener = FirebaseAuth.AuthStateListener { refreshUser() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        authRepository = AuthRepository(requireContext())
        users.value = authRepository.getCurrentUser()
        viewModel = ViewModelProvider(this, ProfileViewModel.Factory(
            users = users,
            connectivity = AndroidConnectivityObserver(requireContext()).observeOnline(),
            taskRepositoryFactory = { TaskRepository.create(requireContext()) },
            signOutAction = {
                FirebaseAuth.getInstance().signOut()
                authRepository.clear()
                users.value = null
            }
        ))[ProfileViewModel::class.java]

        bindViews(view)
        signInButton.setOnClickListener { signIn() }
        signOutButton.setOnClickListener { confirmSignOut() }
        view.findViewById<View>(R.id.profile_content).addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val vertical = resources.configuration.screenWidthDp < 360
            metricsVertical.isVisible = vertical
            metricsHorizontal.isVisible = !vertical
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        FirebaseAuth.getInstance().addAuthStateListener(authStateListener)
        refreshUser()
    }

    override fun onStop() {
        FirebaseAuth.getInstance().removeAuthStateListener(authStateListener)
        super.onStop()
    }

    private fun bindViews(view: View) {
        profilePicture = view.findViewById(R.id.profile_picture_image)
        avatarInitial = view.findViewById(R.id.profile_avatar_initial)
        displayName = view.findViewById(R.id.profile_status_text)
        email = view.findViewById(R.id.profile_email_text)
        completedValue = view.findViewById(R.id.profile_completed_value)
        activeValue = view.findViewById(R.id.profile_active_value)
        rateValue = view.findViewById(R.id.profile_rate_value)
        completedValueVertical = view.findViewById(R.id.profile_completed_value_vertical)
        activeValueVertical = view.findViewById(R.id.profile_active_value_vertical)
        rateValueVertical = view.findViewById(R.id.profile_rate_value_vertical)
        progress = view.findViewById(R.id.profile_progress_indicator)
        signInButton = view.findViewById(R.id.profile_sign_in_button)
        signOutButton = view.findViewById(R.id.profile_sign_out_button)
        guestContainer = view.findViewById(R.id.profile_guest_container)
        accountCard = view.findViewById(R.id.profile_account_card)
        metricsHorizontal = view.findViewById(R.id.profile_metrics_horizontal)
        metricsVertical = view.findViewById(R.id.profile_metrics_vertical)
        errorText = view.findViewById(R.id.profile_error_text)
    }

    private fun render(state: ProfileUiState) {
        accountCard.isVisible = state.showProfileCard
        signOutButton.isVisible = state.showSignOut
        guestContainer.isVisible = state.user == null
        setProgress(state)
        state.user?.let(::renderUser)
    }

    private fun renderUser(user: AuthUser) {
        val name = user.displayName?.takeIf { it.isNotBlank() } ?: getString(R.string.your_account)
        displayName.text = name
        email.text = user.email?.takeIf { it.isNotBlank() } ?: getString(R.string.unknown_email)
        val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString().orEmpty()
        avatarInitial.text = initial
        val hasPhoto = !user.photoUrl.isNullOrBlank()
        profilePicture.isVisible = hasPhoto
        avatarInitial.isVisible = !hasPhoto
        if (hasPhoto) profilePicture.load(user.photoUrl)
    }

    private fun setProgress(state: ProfileUiState) {
        val rate = getString(R.string.percentage_format, state.completionRate)
        completedValue.text = state.completedTaskCount.toString()
        activeValue.text = state.activeTaskCount.toString()
        rateValue.text = rate
        completedValueVertical.text = state.completedTaskCount.toString()
        activeValueVertical.text = state.activeTaskCount.toString()
        rateValueVertical.text = rate
        progress.progress = state.completionRate
    }

    private fun signIn() {
        viewLifecycleOwner.lifecycleScope.launch {
            GoogleSignInHandler(requireContext(), requireActivity()).signIn(
                signInButton = signInButton,
                errorText = errorText,
                onSuccess = {
                    TaskImportDialogFragment.showIfLocalTasks(requireContext(), parentFragmentManager)
                    refreshUser()
                }
            )
        }
    }

    private fun confirmSignOut() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.sign_out_confirmation_title)
            .setMessage(R.string.sign_out_confirmation_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.sign_out) { _, _ -> viewModel.signOut() }
            .show()
    }

    private fun refreshUser() {
        if (::authRepository.isInitialized) users.value = authRepository.getCurrentUser()
    }
}
