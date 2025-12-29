package com.android.contacts.dialogs

import androidx.appcompat.app.AlertDialog
import com.goodwy.commons.activities.BaseSimpleActivity
import com.goodwy.commons.extensions.*
import com.android.contacts.databinding.DialogCustomLabelBinding
import eightbitlab.com.blurview.BlurTarget
import eightbitlab.com.blurview.BlurView

class CustomLabelDialog(val activity: BaseSimpleActivity, blurTarget: BlurTarget, val callback: (label: String) -> Unit) {
    private var dialog: AlertDialog? = null

    init {
        val binding = DialogCustomLabelBinding.inflate(activity.layoutInflater)

        // Setup BlurView with the provided BlurTarget
        val blurView = binding.blurView
        val decorView = activity.window.decorView
        val windowBackground = decorView.background
        
        blurView.setOverlayColor(activity.getProperBlurOverlayColor())
        blurView.setupWith(blurTarget)
            .setFrameClearDrawable(windowBackground)
            .setBlurRadius(8f)
            .setBlurAutoUpdate(true)

        // Setup title inside BlurView
        val titleTextView = binding.root.findViewById<com.goodwy.commons.views.MyTextView>(com.goodwy.commons.R.id.dialog_title)
        titleTextView?.apply {
            visibility = android.view.View.VISIBLE
            setText(com.goodwy.commons.R.string.label)
        }

        // Setup custom buttons inside BlurView
        val primaryColor = activity.getProperPrimaryColor()
        val positiveButton = binding.root.findViewById<com.google.android.material.button.MaterialButton>(com.goodwy.commons.R.id.positive_button)
        val negativeButton = binding.root.findViewById<com.google.android.material.button.MaterialButton>(com.goodwy.commons.R.id.negative_button)
        val buttonsContainer = binding.root.findViewById<android.widget.LinearLayout>(com.goodwy.commons.R.id.buttons_container)

        buttonsContainer?.visibility = android.view.View.VISIBLE
        positiveButton?.apply {
            visibility = android.view.View.VISIBLE
            setTextColor(primaryColor)
            setOnClickListener {
                val label = binding.customLabelEdittext.value
                if (label.isEmpty()) {
                    activity.toast(com.goodwy.commons.R.string.empty_name)
                    return@setOnClickListener
                }

                callback(label)
                dialog?.dismiss()
            }
        }
        negativeButton?.apply {
            visibility = android.view.View.VISIBLE
            setTextColor(primaryColor)
            setOnClickListener { dialog?.dismiss() }
        }

        activity.getAlertDialogBuilder()
            .apply {
                activity.setupDialogStuff(binding.root, this, titleText = "") { alertDialog ->
                    dialog = alertDialog
                    alertDialog.showKeyboard(binding.customLabelEdittext)
                }
            }
    }
}
