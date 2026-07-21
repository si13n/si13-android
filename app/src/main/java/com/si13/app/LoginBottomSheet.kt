package com.si13.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch

class LoginBottomSheet : BottomSheetDialogFragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.bottom_sheet_login, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val signInButton = view.findViewById<View>(R.id.sign_in_with_google_button)
        val guestButton = view.findViewById<Button>(R.id.continue_as_guest_button)
        val closeButton = view.findViewById<ImageButton>(R.id.close_login_bottom_sheet_button)
        val errorText = view.findViewById<TextView>(R.id.login_error_text)

        signInButton.setOnClickListener {
            lifecycleScope.launch {
                GoogleSignInHandler(requireContext(), requireActivity()).signIn(
                    signInButton = signInButton,
                    errorText = errorText,
                    extraDisabledView = guestButton,
                    onSuccess = {
                        TaskImportDialogFragment.showIfLocalTasks(
                            requireContext(),
                            parentFragmentManager
                        )
                        parentFragmentManager.setFragmentResult(LOGIN_RESULT_KEY, Bundle.EMPTY)
                        dismiss()
                    }
                )
            }
        }

        guestButton.setOnClickListener {
            dismiss()
        }

        closeButton.setOnClickListener {
            dismiss()
        }
    }

    companion object {
        const val TAG = "LoginBottomSheet"
        const val LOGIN_RESULT_KEY = "login_result"
    }
}
