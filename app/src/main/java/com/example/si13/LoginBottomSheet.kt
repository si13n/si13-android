package com.example.si13

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

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

        view.findViewById<Button>(R.id.sign_in_with_google_button).setOnClickListener {
            Toast.makeText(
                requireContext(),
                R.string.google_sign_in_not_available,
                Toast.LENGTH_SHORT
            ).show()
        }

        view.findViewById<Button>(R.id.continue_as_guest_button).setOnClickListener {
            dismiss()
        }
    }

    companion object {
        const val TAG = "LoginBottomSheet"
    }
}
