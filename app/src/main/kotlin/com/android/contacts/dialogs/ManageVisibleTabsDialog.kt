package com.android.contacts.dialogs

import com.goodwy.commons.activities.BaseSimpleActivity
import com.goodwy.commons.extensions.getAlertDialogBuilder
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

        activity.getAlertDialogBuilder()
            .setPositiveButton(com.goodwy.commons.R.string.ok) { dialog, which -> dialogConfirmed() }
            .setNegativeButton(com.goodwy.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(view, this, com.goodwy.commons.R.string.manage_shown_tabs)
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
    }
}
