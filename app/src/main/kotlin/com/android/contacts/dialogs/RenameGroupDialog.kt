package com.android.contacts.dialogs

import androidx.appcompat.app.AlertDialog
import com.goodwy.commons.activities.BaseSimpleActivity
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.ContactsHelper
import com.goodwy.commons.helpers.ensureBackgroundThread
import com.goodwy.commons.models.contacts.Group
import com.android.contacts.R
import com.android.contacts.databinding.DialogRenameGroupBinding
import eightbitlab.com.blurview.BlurTarget
import eightbitlab.com.blurview.BlurView

class RenameGroupDialog(val activity: BaseSimpleActivity, val group: Group, blurTarget: BlurTarget, val callback: () -> Unit) {
    private var dialog: AlertDialog? = null

    init {
        val binding = DialogRenameGroupBinding.inflate(activity.layoutInflater).apply {
            renameGroupTitle.setText(group.title)
        }

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
            setText(com.goodwy.commons.R.string.rename)
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
                val newTitle = binding.renameGroupTitle.value
                if (newTitle.isEmpty()) {
                    activity.toast(com.goodwy.commons.R.string.empty_name)
                    return@setOnClickListener
                }

                if (!newTitle.isAValidFilename()) {
                    activity.toast(com.goodwy.commons.R.string.invalid_name)
                    return@setOnClickListener
                }

                group.title = newTitle
                group.contactsCount = 0
                ensureBackgroundThread {
                    if (group.isPrivateSecretGroup()) {
                        activity.groupsDB.insertOrUpdate(group)
                    } else {
                        ContactsHelper(activity).renameGroup(group)
                    }
                    activity.runOnUiThread {
                        callback()
                        dialog?.dismiss()
                    }
                }
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
                    alertDialog.showKeyboard(binding.renameGroupTitle)
                }
            }
    }
}
