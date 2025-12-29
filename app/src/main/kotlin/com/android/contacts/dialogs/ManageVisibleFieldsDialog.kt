package com.android.contacts.dialogs

import com.goodwy.commons.activities.BaseSimpleActivity
import com.goodwy.commons.extensions.getAlertDialogBuilder
import com.goodwy.commons.extensions.getProperPrimaryColor
import com.goodwy.commons.extensions.setupDialogStuff
import com.goodwy.commons.helpers.*
import com.goodwy.commons.views.MyAppCompatCheckbox
import com.android.contacts.R
import com.android.contacts.extensions.config
import com.goodwy.commons.extensions.getProperBlurOverlayColor
import eightbitlab.com.blurview.BlurTarget
import eightbitlab.com.blurview.BlurView

class ManageVisibleFieldsDialog(val activity: BaseSimpleActivity, blurTarget: BlurTarget, val callback: (hasSomethingChanged: Boolean) -> Unit) {
    private var dialog: androidx.appcompat.app.AlertDialog? = null
    private var view = activity.layoutInflater.inflate(R.layout.dialog_manage_visible_fields, null)
    private val fields = LinkedHashMap<Int, Int>()

    init {
        fields.apply {
            put(SHOW_PREFIX_FIELD, R.id.manage_visible_fields_prefix)
            put(SHOW_FIRST_NAME_FIELD, R.id.manage_visible_fields_first_name)
            put(SHOW_MIDDLE_NAME_FIELD, R.id.manage_visible_fields_middle_name)
            put(SHOW_SURNAME_FIELD, R.id.manage_visible_fields_surname)
            put(SHOW_SUFFIX_FIELD, R.id.manage_visible_fields_suffix)
            put(SHOW_NICKNAME_FIELD, R.id.manage_visible_fields_nickname)
            put(SHOW_PHONE_NUMBERS_FIELD, R.id.manage_visible_fields_phone_numbers)
            put(SHOW_MESSENGERS_ACTIONS_FIELD, R.id.manage_visible_fields_messengers_actions)
            put(SHOW_EMAILS_FIELD, R.id.manage_visible_fields_emails)
            put(SHOW_ADDRESSES_FIELD, R.id.manage_visible_fields_addresses)
            put(SHOW_IMS_FIELD, R.id.manage_visible_fields_ims)
            put(SHOW_EVENTS_FIELD, R.id.manage_visible_fields_events)
            put(SHOW_NOTES_FIELD, R.id.manage_visible_fields_notes)
            put(SHOW_ORGANIZATION_FIELD, R.id.manage_visible_fields_organization)
            put(SHOW_WEBSITES_FIELD, R.id.manage_visible_fields_websites)
            put(SHOW_RELATIONS_FIELD, R.id.manage_visible_fields_relations)
            put(SHOW_GROUPS_FIELD, R.id.manage_visible_fields_groups)
            put(SHOW_CONTACT_SOURCE_FIELD, R.id.manage_visible_fields_contact_source)
            put(SHOW_RINGTONE_FIELD, R.id.manage_ringtone)
        }

        val showContactFields = activity.config.showContactFields
        for ((key, value) in fields) {
            view.findViewById<MyAppCompatCheckbox>(value).isChecked = showContactFields and key != 0
        }

        // Setup BlurView with the provided BlurTarget
        val blurView = view.findViewById<BlurView>(R.id.blurView)
        val decorView = activity.window.decorView
        val windowBackground = decorView.background
        
        blurView.setOverlayColor(activity.getProperBlurOverlayColor())
        blurView.setupWith(blurTarget)
            .setFrameClearDrawable(windowBackground)
            .setBlurRadius(8f)
            .setBlurAutoUpdate(true)

        // Setup title inside BlurView
        val titleTextView = view.findViewById<com.goodwy.commons.views.MyTextView>(com.goodwy.commons.R.id.dialog_title)
        titleTextView?.apply {
            visibility = android.view.View.VISIBLE
            setText(R.string.manage_shown_contact_fields)
        }

        // Setup custom buttons inside BlurView
        val primaryColor = activity.getProperPrimaryColor()
        val positiveButton = view.findViewById<com.google.android.material.button.MaterialButton>(com.goodwy.commons.R.id.positive_button)
        val negativeButton = view.findViewById<com.google.android.material.button.MaterialButton>(com.goodwy.commons.R.id.negative_button)
        val buttonsContainer = view.findViewById<android.widget.LinearLayout>(com.goodwy.commons.R.id.buttons_container)

        buttonsContainer?.visibility = android.view.View.VISIBLE
        positiveButton?.apply {
            visibility = android.view.View.VISIBLE
            setTextColor(primaryColor)
            setOnClickListener { dialogConfirmed() }
        }
        negativeButton?.apply {
            visibility = android.view.View.VISIBLE
            setTextColor(primaryColor)
            setOnClickListener { dialog?.dismiss() }
        }

        activity.getAlertDialogBuilder()
            .apply {
                activity.setupDialogStuff(view, this, titleText = "") { alertDialog ->
                    dialog = alertDialog
                }
            }
    }

    private fun dialogConfirmed() {
        var result = 0
        for ((key, value) in fields) {
            if (view.findViewById<MyAppCompatCheckbox>(value).isChecked) {
                result += key
            }
        }

        val hasSomethingChanged = activity.config.showContactFields != result
        activity.config.showContactFields = result

        if (hasSomethingChanged) {
            callback(true)
        }
        dialog?.dismiss()
    }
}
