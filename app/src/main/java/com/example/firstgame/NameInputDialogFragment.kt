package com.example.firstgame

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import android.view.WindowManager // Import this for setting layout attributes

class NameInputDialogFragment : DialogFragment() {
    interface NameInputListener {
        fun onNameSubmitted(playerName: String)
    }
    private var listener: NameInputListener? = null

    override fun onAttach(context: android.content.Context) {
        super.onAttach(context)
        try {
            // Ensure the hosting activity implements the listener interface
            listener = context as NameInputListener
        } catch (e: ClassCastException) {
            throw ClassCastException("$context must implement NameInputListener")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the custom layout for the dialog
        return inflater.inflate(R.layout.dialog_enter_name, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Get references to the UI elements within the dialog's layout
        val usernameEditText = view.findViewById<EditText>(R.id.dialogUsernameEditText)
        val submitButton = view.findViewById<Button>(R.id.dialogSubmitNameButton)

        // Prevent dismissing the dialog by touching outside or pressing back
        isCancelable = false

        submitButton.setOnClickListener {
            val playerName = usernameEditText.text.toString().trim()
            if (playerName.isEmpty()) {
                usernameEditText.error = "Please enter your name!"
            } else {
                // Pass the submitted name back to the hosting activity
                listener?.onNameSubmitted(playerName)
                dismiss() // Close the dialog after submission
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            // Get screen width
            val displayMetrics = resources.displayMetrics
            val width = displayMetrics.widthPixels
            // Calculate desired width (e.g., 85% of screen width)
            val desiredWidth = (width * 0.85).toInt() // Adjust 0.85 (85%) as needed for more/less space
            // Set dialog width, and wrap content for height
            setLayout(desiredWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
            // Center the dialog (it usually is by default for dialogs, but explicitly setting gravity ensures it)
            setGravity(android.view.Gravity.CENTER)
            setBackgroundDrawableResource(android.R.color.transparent)
        }
    }
    override fun onDetach() {
        super.onDetach()
        listener = null // Clear the listener to prevent memory leaks
    }
}