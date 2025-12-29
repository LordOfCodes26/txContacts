package com.android.contacts.dialogs

import androidx.appcompat.app.AlertDialog
import com.goodwy.commons.activities.BaseSimpleActivity
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.isSPlus
import com.android.contacts.databinding.DialogDatePickerBinding
import eightbitlab.com.blurview.BlurTarget
import eightbitlab.com.blurview.BlurView
import org.joda.time.DateTime
import java.util.Calendar

class MyDatePickerDialog(val activity: BaseSimpleActivity, val defaultDate: String, blurTarget: BlurTarget, val callback: (dateTag: String) -> Unit) {
    private val binding = DialogDatePickerBinding.inflate(activity.layoutInflater)
    private var dialog: AlertDialog? = null

    init {
        // Setup BlurView with the provided BlurTarget
        val blurView = binding.blurView
        val decorView = activity.window.decorView
        val windowBackground = decorView.background
        
        blurView.setOverlayColor(activity.getProperBlurOverlayColor())
        blurView.setupWith(blurTarget)
            .setFrameClearDrawable(windowBackground)
            .setBlurRadius(8f)
            .setBlurAutoUpdate(true)

        // Setup custom buttons inside BlurView
        val primaryColor = activity.getProperPrimaryColor()
        val positiveButton = binding.root.findViewById<com.google.android.material.button.MaterialButton>(com.goodwy.commons.R.id.positive_button)
        val negativeButton = binding.root.findViewById<com.google.android.material.button.MaterialButton>(com.goodwy.commons.R.id.negative_button)
        val buttonsContainer = binding.root.findViewById<android.widget.LinearLayout>(com.goodwy.commons.R.id.buttons_container)

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

        AlertDialog.Builder(activity, activity.getDatePickerDialogTheme())
            .apply {
                activity.setupDialogStuff(binding.root, this, titleText = "") { alertDialog ->
                    dialog = alertDialog
                    val today = Calendar.getInstance()
                    var year = today.get(Calendar.YEAR)
                    var month = today.get(Calendar.MONTH)
                    var day = today.get(Calendar.DAY_OF_MONTH)

                    if (defaultDate.isNotEmpty()) {
                        val ignoreYear = defaultDate.startsWith("-")
                        binding.hideYear.isChecked = ignoreYear

                        if (ignoreYear) {
                            month = defaultDate.substring(2, 4).toInt() - 1
                            day = defaultDate.substring(5, 7).toInt()
                        } else {
                            year = defaultDate.substring(0, 4).toInt()
                            month = defaultDate.substring(5, 7).toInt() - 1
                            day = defaultDate.substring(8, 10).toInt()
                        }
                    }

                    if (activity.isDynamicTheme() && isSPlus()) {
                        val dialogBackgroundColor = activity.getColor(com.goodwy.commons.R.color.you_dialog_background_color)
                        binding.dialogHolder.setBackgroundColor(dialogBackgroundColor)
                        binding.datePicker.setBackgroundColor(dialogBackgroundColor)
                    }

                    binding.datePicker.updateDate(year, month, day)
                }
            }
    }

    private fun dialogConfirmed() {
        val year = binding.datePicker.year
        val month = binding.datePicker.month + 1
        val day = binding.datePicker.dayOfMonth
        val date = DateTime().withDate(year, month, day).withTimeAtStartOfDay()

        val tag = if (binding.hideYear.isChecked) {
            date.toString("--MM-dd")
        } else {
            date.toString("yyyy-MM-dd")
        }

        callback(tag)
        dialog?.dismiss()
    }
}
