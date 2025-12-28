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

        activity.getAlertDialogBuilder()
            .setPositiveButton(com.goodwy.commons.R.string.ok, null)
            .setNegativeButton(com.goodwy.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this, com.goodwy.commons.R.string.rename) { alertDialog ->
                    alertDialog.showKeyboard(binding.renameGroupTitle)
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
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
                                alertDialog.dismiss()
                            }
                        }
                    }
                }
            }
    }
}
