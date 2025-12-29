package com.android.contacts.dialogs

import com.goodwy.commons.activities.BaseSimpleActivity
import com.goodwy.commons.extensions.getAlertDialogBuilder
import com.goodwy.commons.extensions.getProperPrimaryColor
import com.goodwy.commons.extensions.setupDialogStuff
import com.android.contacts.R
import com.goodwy.commons.extensions.getProperBlurOverlayColor
import eightbitlab.com.blurview.BlurTarget
import eightbitlab.com.blurview.BlurView

class DateTimePatternInfoDialog(activity: BaseSimpleActivity, blurTarget: BlurTarget) {
    private var dialog: androidx.appcompat.app.AlertDialog? = null

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

        // Setup custom button inside BlurView
        val primaryColor = activity.getProperPrimaryColor()
        val positiveButton = view.findViewById<com.google.android.material.button.MaterialButton>(com.goodwy.commons.R.id.positive_button)
        val buttonsContainer = view.findViewById<android.widget.LinearLayout>(com.goodwy.commons.R.id.buttons_container)

        buttonsContainer?.visibility = android.view.View.VISIBLE
        positiveButton?.apply {
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
}
