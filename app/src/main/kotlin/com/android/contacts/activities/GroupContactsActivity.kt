package com.android.contacts.activities

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.goodwy.commons.dialogs.ConfirmationDialog
import com.goodwy.commons.extensions.*
import eightbitlab.com.blurview.BlurTarget
import com.goodwy.commons.helpers.ContactsHelper
import com.goodwy.commons.helpers.NavigationIcon
import com.goodwy.commons.helpers.TAB_GROUPS
import com.goodwy.commons.helpers.ensureBackgroundThread
import com.goodwy.commons.models.contacts.Contact
import com.goodwy.commons.models.contacts.Group
import com.android.contacts.R
import com.android.contacts.adapters.ContactsAdapter
import com.android.contacts.databinding.ActivityGroupContactsBinding
import com.android.contacts.dialogs.RenameGroupDialog
import com.android.contacts.activities.SelectContactsActivity
import com.android.contacts.extensions.getSelectedContactsResult
import com.android.contacts.extensions.startSelectContactsActivity
import com.android.contacts.helpers.REQUEST_CODE_SELECT_CONTACTS
import com.android.contacts.extensions.handleGenericContactClick
import com.android.contacts.helpers.GROUP
import com.android.contacts.helpers.LOCATION_GROUP_CONTACTS
import com.android.contacts.interfaces.RefreshContactsListener
import com.android.contacts.interfaces.RemoveFromGroupListener

class GroupContactsActivity : SimpleActivity(), RemoveFromGroupListener, RefreshContactsListener {
    private var allContacts = ArrayList<Contact>()
    private var groupContacts = ArrayList<Contact>()
    private var wasInit = false
    private val binding by viewBinding(ActivityGroupContactsBinding::inflate)
    lateinit var group: Group

    protected val INTENT_SELECT_RINGTONE = 600
    private val REQUEST_CODE_SELECT_CONTACTS_FOR_GROUP = 601

    protected var contact: Contact? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        updateTextColors(binding.groupContactsCoordinator)
        setupOptionsMenu()
        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.groupContactsCoordinator))

        val useSurfaceColor = isDynamicTheme() && !isSystemInDarkMode()
        // setupMaterialScrollListener expects MyAppBarLayout, but we have MaterialToolbar - using setupToolbar instead
        // setupMaterialScrollListener(binding.groupContactsList, binding.groupContactsToolbar, useSurfaceColor)

        val backgroundColor = if (useSurfaceColor) getSurfaceColor() else getProperBackgroundColor()
        binding.groupContactsCoordinator.setBackgroundColor(backgroundColor)
        binding.groupContactsToolbar

        group = intent.extras?.getSerializable(GROUP) as Group
        binding.groupContactsToolbar.title = group.title

        binding.groupContactsFab.setOnClickListener {
            if (wasInit) {
                fabClicked()
            }
        }

        binding.groupContactsPlaceholder2.setOnClickListener {
            fabClicked()
        }

        val properPrimaryColor = getProperPrimaryColor()
        binding.groupContactsFastscroller.updateColors(properPrimaryColor)
        binding.groupContactsPlaceholder2.underlineText()
        binding.groupContactsPlaceholder2.setTextColor(properPrimaryColor)
    }

    override fun onResume() {
        super.onResume()
        refreshContacts()

        val properBackgroundColor = if (isDynamicTheme() && !isSystemInDarkMode()) getSurfaceColor() else getProperBackgroundColor()
        setupTopAppBar(binding.groupContactsAppbar, NavigationIcon.Arrow, topBarColor = properBackgroundColor)
        val navigationBarHeight = ViewCompat.getRootWindowInsets(window.decorView)?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
        (binding.groupContactsFab.layoutParams as CoordinatorLayout.LayoutParams).bottomMargin =
            navigationBarHeight + resources.getDimension(com.goodwy.commons.R.dimen.activity_margin).toInt()
    }

    private fun setupOptionsMenu() {
        binding.groupContactsToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.send_sms_to_group -> sendSMSToGroup()
                R.id.send_email_to_group -> sendEmailToGroup()
                R.id.assign_ringtone_to_group -> assignRingtoneToGroup()
                R.id.rename_group -> renameGroup()
                R.id.delete_group -> askConfirmDelete()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (requestCode == INTENT_SELECT_RINGTONE && resultCode == Activity.RESULT_OK && resultData != null) {
            val extras = resultData.extras
            if (extras?.containsKey(RingtoneManager.EXTRA_RINGTONE_PICKED_URI) == true) {
                val uri = extras.getParcelable<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI) ?: return
                try {
                    setRingtoneOnSelected(uri)
                } catch (e: Exception) {
                    showErrorToast(e)
                }
            }
        } else if (requestCode == REQUEST_CODE_SELECT_CONTACTS_FOR_GROUP && resultCode == Activity.RESULT_OK && resultData != null) {
            val (addedContacts, removedContacts) = resultData.getSelectedContactsResult(this)
            ensureBackgroundThread {
                addContactsToGroup(addedContacts, group.id!!)
                removeContactsFromGroup(removedContacts, group.id!!)
                refreshContacts()
            }
        }
    }

    private fun fabClicked() {
        Log.d("CHero-fabClicked", groupContacts.size.toString())
        startSelectContactsActivity(
            allowSelectMultiple = true,
            showOnlyContactsWithNumber = false,
            selectedContacts = groupContacts,
            requestCode = REQUEST_CODE_SELECT_CONTACTS_FOR_GROUP
        )
    }

    private fun refreshContacts() {
        ContactsHelper(this).getContacts {
            wasInit = true
            allContacts = it

            groupContacts = it.filter { it.groups.map { it.id }.contains(group.id) } as ArrayList<Contact>
            binding.groupContactsPlaceholder2.beVisibleIf(groupContacts.isEmpty())
            binding.groupContactsPlaceholder.beVisibleIf(groupContacts.isEmpty())
            binding.groupContactsFastscroller.beVisibleIf(groupContacts.isNotEmpty())
            updateContacts(groupContacts)
        }
    }

    private fun sendSMSToGroup() {
        if (groupContacts.isEmpty()) {
            toast(com.goodwy.commons.R.string.no_contacts_found)
        } else {
            sendSMSToContacts(groupContacts)
        }
    }

    private fun sendEmailToGroup() {
        if (groupContacts.isEmpty()) {
            toast(com.goodwy.commons.R.string.no_contacts_found)
        } else {
            sendEmailToContacts(groupContacts)
        }
    }

    private fun assignRingtoneToGroup() {
        val ringtonePickerIntent = getRingtonePickerIntent()
        try {
            startActivityForResult(ringtonePickerIntent, INTENT_SELECT_RINGTONE)
        } catch (e: Exception) {
            toast(e.toString())
        }
    }

    private fun renameGroup() {
        val blurTarget = findViewById<BlurTarget>(com.goodwy.commons.R.id.mainBlurTarget)
            ?: throw IllegalStateException("mainBlurTarget not found")
        RenameGroupDialog(this, group, blurTarget) {
            binding.groupContactsToolbar.title = group.title
        }
    }

    private fun askConfirmDelete() {
        val item = "\"${group.title}\""
        val baseString = com.goodwy.commons.R.string.deletion_confirmation
        val question = String.format(resources.getString(baseString), item)

        val blurTarget = findViewById<BlurTarget>(com.goodwy.commons.R.id.mainBlurTarget)
            ?: throw IllegalStateException("mainBlurTarget not found")
        ConfirmationDialog(this, question, blurTarget = blurTarget) {
            ensureBackgroundThread {
                deleteGroup()
            }
        }
    }

    private fun deleteGroup() {
        if (group.isPrivateSecretGroup()) {
            groupsDB.deleteGroupId(group.id!!)
        } else {
            ContactsHelper(this).deleteGroup(group.id!!)
        }
        runOnUiThread {
            onBackPressedDispatcher.onBackPressed()
//            onBackPressed()
        }
    }

    private fun updateContacts(contacts: ArrayList<Contact>) {
        val currAdapter = binding.groupContactsList.adapter
        if (currAdapter == null) {
            ContactsAdapter(
                this,
                contactItems = contacts,
                recyclerView = binding.groupContactsList,
                location = LOCATION_GROUP_CONTACTS,
                removeListener = this,
                refreshListener = this
            ) {
                contactClicked(it as Contact)
            }.apply {
                binding.groupContactsList.adapter = this
            }

            if (areSystemAnimationsEnabled) {
                binding.groupContactsList.scheduleLayoutAnimation()
            }
        } else {
            (currAdapter as ContactsAdapter).updateItems(contacts)
        }
    }

    override fun refreshContacts(refreshTabsMask: Int) {
        refreshContacts()
    }

    override fun contactClicked(contact: Contact) {
        handleGenericContactClick(contact)
    }

    override fun removeFromGroup(contacts: ArrayList<Contact>) {
        ensureBackgroundThread {
            removeContactsFromGroup(contacts, group.id!!)
            if (groupContacts.size == contacts.size) {
                refreshContacts()
            }
        }
    }

    private fun getDefaultRingtoneUri(): Uri {
        return try {
            RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        } catch (_: SecurityException) {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        }
    }

    private fun getRingtonePickerIntent(): Intent {
        val defaultRingtoneUri = getDefaultRingtoneUri()

        return Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE)
            putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, defaultRingtoneUri)
            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, defaultRingtoneUri)
        }
    }

    private fun setRingtoneOnSelected(uri: Uri) {
        groupContacts.forEach {
            ContactsHelper(this).updateRingtone(it.contactId.toString(), uri.toString())
        }
    }

}
