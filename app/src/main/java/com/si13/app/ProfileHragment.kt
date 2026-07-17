package com.si13.app

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import coil.load
import com.google.firebase.auth.FirebaseAuth

class ProfileFragment : Fragment(R.layout.fragment_profile_hragment) {
    private lateinit var authRepository: AuthRepository
    private lateinit var profilePictureImage: ImageView
    private lateinit var profileStatusText: TextView
    private lateinit var profileMessageText: TextView
    private lateinit var profileEmailText: TextView
    private lateinit var signInButton: Button
    private lateinit var signOutButton: Button

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        authRepository = AuthRepository(requireContext())
        profilePictureImage = view.findViewById(R.id.profile_picture_image)
        profileStatusText = view.findViewById(R.id.profile_status_text)
        profileMessageText = view.findViewById(R.id.profile_message_text)
        profileEmailText = view.findViewById(R.id.profile_email_text)
        signInButton = view.findViewById(R.id.profile_sign_in_button)
        signOutButton = view.findViewById(R.id.profile_sign_out_button)

        parentFragmentManager.setFragmentResultListener(
            LoginBottomSheet.LOGIN_RESULT_KEY,
            viewLifecycleOwner
        ) { _, _ ->
            renderProfile()
        }

        signInButton.setOnClickListener {
            LoginBottomSheet().show(parentFragmentManager, LoginBottomSheet.TAG)
        }

        signOutButton.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            authRepository.clear()
            renderGuest()
        }

        renderProfile()
    }
    private fun renderProfile() {
        val user = authRepository.getCurrentUser()
        if (user == null) {
            renderGuest()
        } else {
            renderAuthenticated(user)
        }
    }

    private fun renderGuest() {
        profilePictureImage.isVisible = false
        profileStatusText.text = getString(R.string.guest_status)
        profileMessageText.text = getString(R.string.guest_profile_message)
        profileMessageText.isVisible = true
        profileEmailText.isVisible = false
        signInButton.isVisible = true
        signOutButton.isVisible = false
    }

    private fun renderAuthenticated(user: AuthUser) {
        val displayName = user.displayName ?: getString(R.string.unknown_name)
        val email = user.email ?: getString(R.string.unknown_email)

        profileStatusText.text = displayName
        profileMessageText.isVisible = false
        profileEmailText.text = email
        profileEmailText.isVisible = true
        signInButton.isVisible = false
        signOutButton.isVisible = true

        if (user.photoUrl.isNullOrBlank()) {
            profilePictureImage.isVisible = false
        } else {
            profilePictureImage.isVisible = true
            profilePictureImage.load(user.photoUrl)
        }
    }
}
