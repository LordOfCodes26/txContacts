package com.android.contacts.dialogs

import com.goodwy.commons.activities.BaseSimpleActivity
import com.goodwy.commons.extensions.beGoneIf
import com.goodwy.commons.extensions.getAlertDialogBuilder
import com.goodwy.commons.extensions.setupDialogStuff
import com.goodwy.commons.helpers.*
import com.android.contacts.R
import com.android.contacts.databinding.DialogChangeSortingBinding
import com.android.contacts.extensions.config
import com.goodwy.commons.extensions.getProperBlurOverlayColor
import com.goodwy.commons.extensions.getProperPrimaryColor
import eightbitlab.com.blurview.BlurTarget
import eightbitlab.com.blurview.BlurView

class ChangeSortingDialog(val activity: BaseSimpleActivity, private val showCustomSorting: Boolean = false, blurTarget: BlurTarget, private val callback: () -> Unit) {
    private var dialog: androidx.appcompat.app.AlertDialog? = null
    private var currSorting = 0
    private var config = activity.config
    private val binding = DialogChangeSortingBinding.inflate(activity.layoutInflater)

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

        // Setup title inside BlurView
        val titleTextView = binding.root.findViewById<com.goodwy.commons.views.MyTextView>(com.goodwy.commons.R.id.dialog_title)
        titleTextView?.apply {
            visibility = android.view.View.VISIBLE
            setText(com.goodwy.commons.R.string.sort_by)
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
            setOnClickListener { dialogConfirmed() }
        }
        negativeButton?.apply {
            visibility = android.view.View.VISIBLE
            setTextColor(primaryColor)
            setOnClickListener { dialog?.dismiss() }
        }

        activity.getAlertDialogBuilder()
            .apply {
                // Pass empty titleText to prevent setupDialogStuff from adding title outside BlurView
                activity.setupDialogStuff(binding.root, this, titleText = "") { alertDialog ->
                    dialog = alertDialog
                }
            }

        currSorting = if (showCustomSorting && config.isCustomOrderSelected) {
            SORT_BY_CUSTOM
        } else {
            config.sorting
        }

        setupSortRadio()
        setupOrderRadio()
        setupSymbolsFirst()
    }

    private fun setupSortRadio() {
        val sortingRadio = binding.sortingDialogRadioSorting

        sortingRadio.setOnCheckedChangeListener { group, checkedId ->
            val isCustomSorting = checkedId == binding.sortingDialogRadioCustom.id
            binding.sortingDialogRadioOrder.beGoneIf(isCustomSorting)
            binding.divider.root.beGoneIf(isCustomSorting)
        }

        val sortBtn = when {
            currSorting and SORT_BY_FIRST_NAME != 0 -> binding.sortingDialogRadioFirstName
            currSorting and SORT_BY_MIDDLE_NAME != 0 -> binding.sortingDialogRadioMiddleName
            currSorting and SORT_BY_SURNAME != 0 -> binding.sortingDialogRadioSurname
            currSorting and SORT_BY_FULL_NAME != 0 -> binding.sortingDialogRadioFullName
            currSorting and SORT_BY_CUSTOM != 0 -> binding.sortingDialogRadioCustom
            else -> binding.sortingDialogRadioDateCreated
        }
        sortBtn.isChecked = true

        if (showCustomSorting) {
            binding.sortingDialogRadioCustom.isChecked = config.isCustomOrderSelected
        }
        binding.sortingDialogRadioCustom.beGoneIf(!showCustomSorting)
    }

    private fun setupOrderRadio() {
        var orderBtn = binding.sortingDialogRadioAscending

        if (currSorting and SORT_DESCENDING != 0) {
            orderBtn = binding.sortingDialogRadioDescending
        }
        orderBtn.isChecked = true
    }

    private fun setupSymbolsFirst() {
        binding.sortingDialogSymbolsFirstCheckbox.isChecked = config.sortingSymbolsFirst
    }

    private fun dialogConfirmed() {
        val sortingRadio = binding.sortingDialogRadioSorting
        var sorting = when (sortingRadio.checkedRadioButtonId) {
            R.id.sorting_dialog_radio_first_name -> SORT_BY_FIRST_NAME
            R.id.sorting_dialog_radio_middle_name -> SORT_BY_MIDDLE_NAME
            R.id.sorting_dialog_radio_surname -> SORT_BY_SURNAME
            R.id.sorting_dialog_radio_full_name -> SORT_BY_FULL_NAME
            R.id.sorting_dialog_radio_custom -> SORT_BY_CUSTOM
            else -> SORT_BY_DATE_CREATED
        }

        if (sorting != SORT_BY_CUSTOM && binding.sortingDialogRadioOrder.checkedRadioButtonId == R.id.sorting_dialog_radio_descending) {
            sorting = sorting or SORT_DESCENDING
        }

        if (showCustomSorting) {
            if (sorting == SORT_BY_CUSTOM) {
                config.isCustomOrderSelected = true
            } else {
                config.isCustomOrderSelected = false
                config.sorting = sorting
            }
        } else {
            config.sorting = sorting
        }

        config.sortingSymbolsFirst = binding.sortingDialogSymbolsFirstCheckbox.isChecked

        callback()
        dialog?.dismiss()
    }
}
