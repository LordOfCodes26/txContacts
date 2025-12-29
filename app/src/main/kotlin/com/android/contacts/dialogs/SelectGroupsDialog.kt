package com.android.contacts.dialogs

import android.graphics.Typeface
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.ContactsHelper
import com.goodwy.commons.models.contacts.Group
import com.goodwy.commons.views.MyAppCompatCheckbox
import com.android.contacts.R
import com.android.contacts.activities.SimpleActivity
import com.android.contacts.databinding.DialogSelectGroupsBinding
import com.android.contacts.databinding.ItemCheckboxBinding
import com.android.contacts.databinding.ItemTextviewBinding
import com.goodwy.commons.extensions.getProperBlurOverlayColor
import eightbitlab.com.blurview.BlurTarget
import eightbitlab.com.blurview.BlurView

class SelectGroupsDialog(val activity: SimpleActivity, val selectedGroups: ArrayList<Group>, private val blurTarget: BlurTarget, val callback: (newGroups: ArrayList<Group>) -> Unit) {
    private val binding = DialogSelectGroupsBinding.inflate(activity.layoutInflater)
    private val checkboxes = ArrayList<MyAppCompatCheckbox>()
    private var groups = ArrayList<Group>()
    private var dialog: AlertDialog? = null

    init {
        ContactsHelper(activity).getStoredGroups {
            groups = it
            activity.runOnUiThread {
                initDialog()
            }
        }
    }

    private fun initDialog() {
        groups.sortedBy { it.title }.forEach {
            addGroupCheckbox(it)
        }

        if (groups.isEmpty()) addNoGroupText()
        addCreateNewGroupButton()

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
            setText(com.goodwy.strings.R.string.add_group)
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
                activity.setupDialogStuff(binding.root, this, titleText = "") { alertDialog ->
                    dialog = alertDialog
                }
            }
    }

    private fun addGroupCheckbox(group: Group) {
        ItemCheckboxBinding.inflate(activity.layoutInflater, null, false).apply {
            checkboxes.add(itemCheckbox)
            itemCheckboxHolder.setOnClickListener {
                itemCheckbox.toggle()
            }

            itemCheckbox.apply {
                isChecked = selectedGroups.contains(group)
                text = group.title
                tag = group.id
                setColors(activity.getProperTextColor(), activity.getProperPrimaryColor(), activity.getProperBackgroundColor())
            }
            binding.dialogGroupsHolder.addView(this.root)
        }
    }

    private fun addNoGroupText() {
        val newGroup = Group(0, activity.getString(com.goodwy.commons.R.string.no_items_found))
        ItemTextviewBinding.inflate(activity.layoutInflater, null, false).itemTextview.apply {
            text = newGroup.title
            setTypeface(null, Typeface.ITALIC)
            tag = newGroup.id
            setTextColor(activity.getProperTextColor())
            binding.dialogGroupsHolder.addView(this)
        }
    }

    private fun addCreateNewGroupButton() {
        val newGroup = Group(0, activity.getString(R.string.create_new_group))
        ItemTextviewBinding.inflate(activity.layoutInflater, null, false).itemTextview.apply {
            text = newGroup.title
            tag = newGroup.id
            setTextColor(activity.getProperPrimaryColor())
            binding.dialogGroupsHolder.addView(this)
            setOnClickListener {
                val blurTarget = activity.findViewById<eightbitlab.com.blurview.BlurTarget>(com.goodwy.commons.R.id.mainBlurTarget)
                    ?: throw IllegalStateException("mainBlurTarget not found")
                CreateNewGroupDialog(activity, blurTarget) {
                    selectedGroups.add(it)
                    groups.add(it)
                    binding.dialogGroupsHolder.removeViewAt(binding.dialogGroupsHolder.childCount - 1)
                    addGroupCheckbox(it)
                    addCreateNewGroupButton()
                }
            }
        }
    }

    private fun dialogConfirmed() {
        val selectedGroups = ArrayList<Group>()
        checkboxes.filter { it.isChecked }.forEach {
            val groupId = it.tag as Long
            groups.firstOrNull { it.id == groupId }?.apply {
                selectedGroups.add(this)
            }
        }

        callback(selectedGroups)
        dialog?.dismiss()
    }
}
