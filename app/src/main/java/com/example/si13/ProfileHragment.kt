package com.example.si13

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.fragment.app.Fragment

class ProfileFragment : Fragment(R.layout.fragment_profile_hragment) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<Button>(R.id.profile_sign_in_button).setOnClickListener {
            LoginBottomSheet().show(parentFragmentManager, LoginBottomSheet.TAG)
        }
    }
}
