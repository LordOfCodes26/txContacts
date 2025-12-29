package com.android.contacts.dialogs

import com.goodwy.commons.activities.BaseSimpleActivity
import com.goodwy.commons.extensions.getAlertDialogBuilder
import com.goodwy.commons.extensions.getProperPrimaryColor
import com.goodwy.commons.extensions.setupDialogStuff
import com.goodwy.commons.helpers.TAB_CONTACTS
import com.goodwy.commons.helpers.TAB_FAVORITES
import com.goodwy.commons.helpers.TAB_GROUPS
import com.goodwy.commons.views.MyAppCompatCheckbox
import com.android.contacts.R
import com.android.contacts.extensions.config
import com.android.contacts.helpers.ALL_TABS_MASK
import com.goodwy.commons.extensions.getProperBlurOverlayColor
import eightbitlab.com.blurview.BlurTarget
import eightbitlab.com.blurview.BlurView

class ManageVisibleTabsDialog(val activity: BaseSimpleActivity, blurTarget: BlurTarget) {
    private var dialog: androidx.appcompat.app.AlertDialog? = null
    private var view = activity.layoutInflater.inflate(R.layout.dialog_manage_visible_tabs, null)
    private val tabs = LinkedHashMap<Int, Int>()

    init {
        tabs.apply {
            put(TAB_FAVORITES, R.id.manage_visible_tabs_favorites)
            put(TAB_CONTACTS, R.id.manage_visible_tabs_contacts)
            put(TAB_GROUPS, R.id.manage_visible_tabs_groups)
        }

        val showTabs = activity.config.showTabs
        for ((key, value) in tabs) {
            view.findViewById<MyAppCompatCheckbox>(value).isChecked = showTabs and key != 0
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
            setText(com.goodwy.commons.R.string.manage_shown_tabs)
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
        for ((key, value) in tabs) {
            if (view.findViewById<MyAppCompatCheckbox>(value).isChecked) {
                result += key
            }
        }

        if (result == 0) {
            result = ALL_TABS_MASK
        }

        activity.config.showTabs = result
        dialog?.dismiss()
    }
}
