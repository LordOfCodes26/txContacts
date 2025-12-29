package com.android.contacts.dialogs

import androidx.appcompat.app.AlertDialog
import com.goodwy.commons.activities.BaseSimpleActivity
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.getDateFormats
import com.goodwy.commons.helpers.isSPlus
import com.android.contacts.R
import com.android.contacts.databinding.DialogDatePickerBinding
import eightbitlab.com.blurview.BlurTarget
import eightbitlab.com.blurview.BlurView
import android.text.format.DateFormat
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

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

        // Setup title inside BlurView (if needed)
        val titleTextView = binding.root.findViewById<com.goodwy.commons.views.MyTextView>(R.id.dialog_title)
        // Title is hidden by default in this dialog

        // Setup custom buttons inside BlurView
        val primaryColor = activity.getProperPrimaryColor()
        val buttonsContainer = binding.root.findViewById<android.widget.LinearLayout>(R.id.buttons_container)
        val positiveButton = binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.positive_button)
        val negativeButton = binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.negative_button)

        if (buttonsContainer != null) {
            buttonsContainer.visibility = android.view.View.VISIBLE
            
            positiveButton?.apply {
                visibility = android.view.View.VISIBLE
                text = activity.resources.getString(com.goodwy.commons.R.string.ok)
                setTextColor(primaryColor)
                setOnClickListener { dialogConfirmed() }
            }
            negativeButton?.apply {
                visibility = android.view.View.VISIBLE
                text = activity.resources.getString(com.goodwy.commons.R.string.cancel)
                setTextColor(primaryColor)
                setOnClickListener { dialog?.dismiss() }
            }
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
                        var parsed = false
                        
                        // First, try to parse using user's preferred format
                        val userDateFormat = activity.baseConfig.dateFormat
                        try {
                            val formatter = DateTimeFormat.forPattern(userDateFormat)
                            val date = DateTime.parse(defaultDate, formatter)
                            year = date.year
                            month = date.monthOfYear - 1
                            day = date.dayOfMonth
                            parsed = true
                        } catch (e: Exception) {
                            // Try without year if format includes year
                            if (userDateFormat.contains("y")) {
                                try {
                                    var formatWithoutYear = userDateFormat
                                        .replace("yyyy", "")
                                        .replace("yy", "")
                                        .replace("y", "")
                                        .replace("  ", " ")
                                        .trim()
                                        .trim('-')
                                        .trim('.')
                                        .trim('/')
                                        .trim()
                                    
                                    if (formatWithoutYear.isNotEmpty()) {
                                        val formatter = DateTimeFormat.forPattern(formatWithoutYear)
                                        val date = DateTime.parse(defaultDate, formatter)
                                        month = date.monthOfYear - 1
                                        day = date.dayOfMonth
                                        binding.hideYear.isChecked = true
                                        parsed = true
                                    }
                                } catch (e2: Exception) {
                                    // Continue to fallback formats
                                }
                            }
                        }
                        
                        // Fallback: Try old formats for backward compatibility
                        if (!parsed) {
                            val ignoreYear = defaultDate.startsWith("-")
                            binding.hideYear.isChecked = ignoreYear

                            try {
                                if (ignoreYear) {
                                    // Old format: --MM-dd
                                    if (defaultDate.length >= 7) {
                                        month = defaultDate.substring(2, 4).toInt() - 1
                                        day = defaultDate.substring(5, 7).toInt()
                                        parsed = true
                                    }
                                } else {
                                    // Old format: yyyy-MM-dd
                                    if (defaultDate.length >= 10) {
                                        year = defaultDate.substring(0, 4).toInt()
                                        month = defaultDate.substring(5, 7).toInt() - 1
                                        day = defaultDate.substring(8, 10).toInt()
                                        parsed = true
                                    }
                                }
                            } catch (e: Exception) {
                                // If parsing fails, use today's date (already set above)
                            }
                        }
                        
                        // Try common date formats as last resort
                        if (!parsed) {
                            // Also try all date format constants
                            val allFormats = getDateFormats() + listOf(
                                "dd.MM.yyyy", "dd/MM/yyyy", "MM/dd/yyyy", 
                                "MM-dd-yyyy", "dd-MM-yyyy", "dd.MM.yy", 
                                "dd/MM/yy", "MM/dd/yy", "dd-MM", "dd.MM"
                            )
                            
                            for (format in allFormats) {
                                try {
                                    val formatter = DateTimeFormat.forPattern(format)
                                    val date = DateTime.parse(defaultDate, formatter)
                                    year = date.year
                                    month = date.monthOfYear - 1
                                    day = date.dayOfMonth
                                    
                                    // Check if format has year
                                    if (!format.contains("y")) {
                                        binding.hideYear.isChecked = true
                                    }
                                    parsed = true
                                    break
                                } catch (e: Exception) {
                                    // Try next format
                                }
                            }
                        }
                    }

                    // Get proper text color for the theme
                    val textColor = activity.getProperTextColor()
                    
                    // Make DatePicker background transparent for blur effect
                    binding.datePicker.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    
                    // Make DatePicker child views transparent and set text colors
                    try {
                        fun setViewTransparentAndTextColor(view: android.view.View) {
                            if (view is android.view.ViewGroup) {
                                view.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                
                                // Handle NumberPicker specifically
                                if (view is android.widget.NumberPicker) {
                                    try {
                                        // Set text colors for NumberPicker using reflection
                                        val selectorWheelPaintField = android.widget.NumberPicker::class.java.getDeclaredField("mSelectorWheelPaint")
                                        selectorWheelPaintField.isAccessible = true
                                        val paint = selectorWheelPaintField.get(view) as android.graphics.Paint
                                        paint.color = textColor
                                        
                                        // Update selected text color
                                        val selectedTextColorField = android.widget.NumberPicker::class.java.getDeclaredField("mSelectedTextColor")
                                        selectedTextColorField.isAccessible = true
                                        selectedTextColorField.set(view, textColor)
                                    } catch (e: Exception) {
                                        // Reflection might fail, try direct child access
                                    }
                                    
                                    // Set text color on NumberPicker's EditText child
                                    for (i in 0 until view.childCount) {
                                        val child = view.getChildAt(i)
                                        if (child is android.widget.EditText) {
                                            child.setTextColor(textColor)
                                        }
                                        setViewTransparentAndTextColor(child)
                                    }
                                } else {
                                    // Recursively process other ViewGroups
                                    for (i in 0 until view.childCount) {
                                        setViewTransparentAndTextColor(view.getChildAt(i))
                                    }
                                }
                            } else {
                                view.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                if (view is android.widget.TextView) {
                                    view.setTextColor(textColor)
                                }
                            }
                        }
                        
                        val datePickerChildren = (binding.datePicker as android.view.ViewGroup).childCount
                        for (i in 0 until datePickerChildren) {
                            setViewTransparentAndTextColor((binding.datePicker as android.view.ViewGroup).getChildAt(i))
                        }
                    } catch (e: Exception) {
                        // If we can't make it transparent, continue anyway
                    }

                    if (activity.isDynamicTheme() && isSPlus()) {
                        // Keep dialog holder transparent for blur effect
                        binding.dialogHolder.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    }

                    binding.datePicker.updateDate(year, month, day)
                }
            }
    }

    private fun dialogConfirmed() {
        val year = binding.datePicker.year
        val month = binding.datePicker.month
        val day = binding.datePicker.dayOfMonth
        
        // Create a Calendar instance with the selected date
        val cal = Calendar.getInstance(Locale.ENGLISH)
        cal.set(year, month, day)

        // Get user's preferred date format
        var dateFormat = activity.baseConfig.dateFormat
        
        // If year is hidden, remove year part from format
        if (binding.hideYear.isChecked) {
            // Remove year patterns (yyyy, yy, y) and clean up separators
            dateFormat = dateFormat
                .replace("yyyy", "")
                .replace("yy", "")
                .replace("y", "")
                .replace("  ", " ") // Replace double spaces
                .trim()
            
            // Clean up leading/trailing separators
            dateFormat = dateFormat.trimStart('-', '.', '/', ' ')
                .trimEnd('-', '.', '/', ' ')
            
            // Clean up consecutive separators (e.g., ".." or "--")
            dateFormat = dateFormat.replace(Regex("[-./]{2,}"), "-")
            
            // If format becomes empty, use default MM-dd
            if (dateFormat.isEmpty()) {
                dateFormat = "MM-dd"
            }
        }

        // Format the date using Android's DateFormat (SimpleDateFormat compatible)
        val tag = try {
            DateFormat.format(dateFormat, cal).toString()
        } catch (e: Exception) {
            // Fallback to default format if user's format is invalid
            try {
                if (binding.hideYear.isChecked) {
                    DateFormat.format("MM-dd", cal).toString()
                } else {
                    DateFormat.format("yyyy-MM-dd", cal).toString()
                }
            } catch (e2: Exception) {
                // Last resort: use SimpleDateFormat directly
                try {
                    val sdf = SimpleDateFormat(dateFormat, Locale.ENGLISH)
                    sdf.format(cal.time)
                } catch (e3: Exception) {
                    // Ultimate fallback
                    if (binding.hideYear.isChecked) {
                        "--${String.format(Locale.ENGLISH, "%02d", month + 1)}-${String.format(Locale.ENGLISH, "%02d", day)}"
                    } else {
                        "${year}-${String.format(Locale.ENGLISH, "%02d", month + 1)}-${String.format(Locale.ENGLISH, "%02d", day)}"
                    }
                }
            }
        }

        callback(tag)
        dialog?.dismiss()
    }
}
