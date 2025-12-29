package com.android.contacts.dialogs

import androidx.appcompat.app.AlertDialog
import com.goodwy.commons.dialogs.FilePickerDialog
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.ContactsHelper
import com.goodwy.commons.helpers.ensureBackgroundThread
import com.goodwy.commons.models.contacts.Contact
import com.goodwy.commons.models.contacts.ContactSource
import com.android.contacts.R
import com.android.contacts.activities.SimpleActivity
import com.android.contacts.adapters.FilterContactSourcesAdapter
import com.android.contacts.databinding.DialogExportContactsBinding
import com.android.contacts.extensions.config
import eightbitlab.com.blurview.BlurTarget
import eightbitlab.com.blurview.BlurView
import java.io.File

class ExportContactsDialog(
    val activity: SimpleActivity, val path: String, val hidePath: Boolean, blurTarget: BlurTarget,
    private val callback: (file: File, ignoredContactSources: HashSet<String>) -> Unit
) {
    private var dialog: AlertDialog? = null
    private var ignoreClicks = false
    private var realPath = if (path.isEmpty()) activity.internalStoragePath else path
    private var contactSources = ArrayList<ContactSource>()
    private var contacts = ArrayList<Contact>()
    private var isContactSourcesReady = false
    private var isContactsReady = false

    init {
        val binding = DialogExportContactsBinding.inflate(activity.layoutInflater).apply {
            exportContactsFolder.setText(activity.humanizePath(realPath))
            exportContactsFilename.setText("contacts_${getCurrentFormattedDateTime()}")

            if (hidePath) {
                exportContactsFolderHint.beGone()
            } else {
                exportContactsFolder.setOnClickListener {
                    activity.hideKeyboard(exportContactsFilename)
                    val blurTarget = activity.findViewById<eightbitlab.com.blurview.BlurTarget>(com.goodwy.commons.R.id.mainBlurTarget)
                        ?: throw IllegalStateException("mainBlurTarget not found")
                    FilePickerDialog(activity, realPath, false, showFAB = true, blurTarget = blurTarget) {
                        exportContactsFolder.setText(activity.humanizePath(it))
                        realPath = it
                    }
                }
            }

            ContactsHelper(activity).getContactSources { contactSources ->
                contactSources.mapTo(this@ExportContactsDialog.contactSources) { it.copy() }
                isContactSourcesReady = true
                processDataIfReady(this)
            }

            ContactsHelper(activity).getContacts(getAll = true) { contacts ->
                contacts.mapTo(this@ExportContactsDialog.contacts) { it.copy() }
                isContactsReady = true
                processDataIfReady(this)
            }
        }

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
            setText(R.string.export_contacts)
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
                if (binding.exportContactsList.adapter == null || ignoreClicks) {
                    return@setOnClickListener
                }

                val filename = binding.exportContactsFilename.value
                when {
                    filename.isEmpty() -> activity.toast(com.goodwy.commons.R.string.empty_name)
                    filename.isAValidFilename() -> {
                        val file = File(realPath, "$filename.vcf")
                        if (!hidePath && file.exists()) {
                            activity.toast(com.goodwy.commons.R.string.name_taken)
                            return@setOnClickListener
                        }

                        ignoreClicks = true
                        ensureBackgroundThread {
                            activity.config.lastExportPath = file.absolutePath.getParentPath()
                            val selectedSources = (binding.exportContactsList.adapter as FilterContactSourcesAdapter).getSelectedContactSources()
                            val ignoredSources = contactSources
                                .filter { !selectedSources.contains(it) }
                                .map { it.getFullIdentifier() }
                                .toHashSet()
                            callback(file, ignoredSources)
                            activity.runOnUiThread {
                                dialog?.dismiss()
                            }
                        }
                    }

                    else -> activity.toast(com.goodwy.commons.R.string.invalid_name)
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
                }
            }
    }

    private fun processDataIfReady(binding: DialogExportContactsBinding) {
        if (!isContactSourcesReady || !isContactsReady) {
            return
        }

        val contactSourcesWithCount = ArrayList<ContactSource>()
        for (source in contactSources) {
            val count = contacts.filter { it.source == source.name }.count()
            contactSourcesWithCount.add(source.copy(count = count))
        }

        contactSources.clear()
        contactSources.addAll(contactSourcesWithCount)

        activity.runOnUiThread {
            binding.exportContactsList.adapter = FilterContactSourcesAdapter(activity, contactSourcesWithCount, activity.getVisibleContactSources())
        }
    }
}
