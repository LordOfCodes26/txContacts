package com.android.contacts.dialogs

import com.goodwy.commons.activities.BaseSimpleActivity
import com.goodwy.commons.extensions.getAlertDialogBuilder
import com.goodwy.commons.extensions.setupDialogStuff
import com.android.contacts.R
import com.goodwy.commons.extensions.getProperBlurOverlayColor
import eightbitlab.com.blurview.BlurTarget
import eightbitlab.com.blurview.BlurView

class DateTimePatternInfoDialog(activity: BaseSimpleActivity, blurTarget: BlurTarget) {

    init {
        val view = activity.layoutInflater.inflate(R.layout.datetime_pattern_info_layout, null)
        
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
            .setPositiveButton(com.goodwy.commons.R.string.ok) { _, _ -> { } }
            .apply {
                activity.setupDialogStuff(view, this)
            }
    }
}
