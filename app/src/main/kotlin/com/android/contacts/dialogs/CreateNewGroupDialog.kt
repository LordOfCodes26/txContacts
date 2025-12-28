package com.android.contacts.dialogs

import android.view.View
import androidx.appcompat.app.AlertDialog
import com.goodwy.commons.activities.BaseSimpleActivity
import com.goodwy.commons.dialogs.RadioGroupDialog
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.ContactsHelper
import com.goodwy.commons.helpers.ensureBackgroundThread
import com.goodwy.commons.models.RadioItem
import com.goodwy.commons.models.contacts.ContactSource
import com.goodwy.commons.models.contacts.Group
import com.android.contacts.R
import com.android.contacts.databinding.DialogCreateNewGroupBinding
import eightbitlab.com.blurview.BlurTarget
import eightbitlab.com.blurview.BlurView

class CreateNewGroupDialog(val activity: BaseSimpleActivity, blurTarget: BlurTarget, val callback: (newGroup: Group) -> Unit) {
    private var dialog: AlertDialog? = null

    init {
        val binding = DialogCreateNewGroupBinding.inflate(activity.layoutInflater)

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
            setText(R.string.create_new_group)
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
                val name = binding.groupName.value
                if (name.isEmpty()) {
                    activity.toast(com.goodwy.commons.R.string.empty_name)
                    return@setOnClickListener
                }

                val contactSources = ArrayList<ContactSource>()
                ContactsHelper(activity).getContactSources {
                    it.mapTo(contactSources) { ContactSource(it.name, it.type, it.publicName) }

                    val items = ArrayList<RadioItem>()
                    contactSources.forEachIndexed { index, contactSource ->
                        items.add(RadioItem(index, contactSource.publicName))
                    }

                    activity.runOnUiThread {
                        if (items.size == 1) {
                            createGroupUnder(name, contactSources.first())
                        } else {
                            val blurTarget = activity.findViewById<eightbitlab.com.blurview.BlurTarget>(com.goodwy.commons.R.id.mainBlurTarget)
                                ?: throw IllegalStateException("mainBlurTarget not found")
                            RadioGroupDialog(activity, items, titleId = R.string.create_group_under_account, blurTarget = blurTarget) {
                                val contactSource = contactSources[it as Int]
                                createGroupUnder(name, contactSource)
                            }
                        }
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
                    alertDialog.showKeyboard(binding.groupName)
                }
            }
    }

    private fun createGroupUnder(name: String, contactSource: ContactSource) {
        ensureBackgroundThread {
            val newGroup = ContactsHelper(activity).createNewGroup(name, contactSource.name, contactSource.type)
            activity.runOnUiThread {
                if (newGroup != null) {
                    callback(newGroup)
                }
                dialog?.dismiss()
            }
        }
    }
}


