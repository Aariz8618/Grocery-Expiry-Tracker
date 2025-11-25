package com.aariz.expirytracker

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth

class SettingsFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var textUserName: TextView
    private lateinit var textUserEmail: TextView
    private lateinit var buttonViewProfile: MaterialButton
    private lateinit var rowNotifications: LinearLayout
    private lateinit var rowPermissions: LinearLayout
    private lateinit var rowAbout: LinearLayout
    private lateinit var rowPrivacy: LinearLayout
    private lateinit var rowSupport: LinearLayout
    private lateinit var iconPermissionsChevron: ImageView
    private lateinit var layoutPermissionsExpanded: LinearLayout
    private var isPermissionsExpanded = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()

        view.findViewById<View>(R.id.header_section)?.applyHeaderInsets()

        initViews(view)
        setupClickListeners()
        loadUserInfo()
    }

    private fun initViews(view: View) {
        textUserName = view.findViewById(R.id.text_user_name)
        textUserEmail = view.findViewById(R.id.text_user_email)
        buttonViewProfile = view.findViewById(R.id.button_view_profile)
        rowNotifications = view.findViewById(R.id.row_notifications)
        rowPermissions = view.findViewById(R.id.row_permissions)
        rowAbout = view.findViewById(R.id.row_about)
        rowPrivacy = view.findViewById(R.id.row_privacy)
        rowSupport = view.findViewById(R.id.row_support)
        iconPermissionsChevron = view.findViewById(R.id.icon_permissions_chevron)
        layoutPermissionsExpanded = view.findViewById(R.id.layout_permissions_expanded)
    }

    private fun setupClickListeners() {
        buttonViewProfile.setOnClickListener {
            if (!isAdded) return@setOnClickListener
            startActivity(Intent(requireContext(), ProfileActivity::class.java))
        }

        rowNotifications.setOnClickListener {
            if (!isAdded) return@setOnClickListener
            startActivity(Intent(requireContext(), NotificationSettingsActivity::class.java))
        }

        rowPermissions.setOnClickListener {
            togglePermissionsExpanded()
        }

        rowAbout.setOnClickListener {
            showAboutDialog()
        }

        rowPrivacy.setOnClickListener {
            if (!isAdded) return@setOnClickListener
            startActivity(Intent(requireContext(), PrivacyPolicyActivity::class.java))
        }

        rowSupport.setOnClickListener {
            if (!isAdded) return@setOnClickListener
            startActivity(Intent(requireContext(), FeedbackActivity::class.java))
        }
    }

    private fun loadUserInfo() {
        if (!isAdded) return

        val currentUser = auth.currentUser
        if (currentUser != null) {
            textUserName.text = currentUser.displayName ?: "User"
            textUserEmail.text = currentUser.email ?: "No email"
        } else {
            textUserName.text = "Guest"
            textUserEmail.text = "Not logged in"
        }
    }

    private fun togglePermissionsExpanded() {
        if (!isAdded) return

        isPermissionsExpanded = !isPermissionsExpanded

        if (isPermissionsExpanded) {
            layoutPermissionsExpanded.visibility = View.VISIBLE
            iconPermissionsChevron.rotation = 180f
        } else {
            layoutPermissionsExpanded.visibility = View.GONE
            iconPermissionsChevron.rotation = 0f
        }
    }

    private fun showAboutDialog() {
        if (!isAdded) return

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("About Expiry Tracker")
            .setMessage(
                "Version: 1.0.2\n" +
                        "Developer: TechFlow Solutions\n\n" +
                        "Expiry Tracker helps you reduce food waste by tracking expiration dates " +
                        "and sending timely reminders.\n\n" +
                        "© 2025 TechFlow Solutions"
            )
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        loadUserInfo()
    }
}