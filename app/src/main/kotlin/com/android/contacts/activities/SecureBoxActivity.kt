package com.android.contacts.activities

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import com.reddit.indicatorfastscroll.FastScrollItemIndicator
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.ContactsHelper
import com.goodwy.commons.helpers.CONTACT_ID
import com.goodwy.commons.helpers.NavigationIcon
import com.goodwy.commons.helpers.ensureBackgroundThread
import com.goodwy.commons.models.contacts.Contact
import com.goodwy.commons.securebox.BiometricUnlockHelper
import com.goodwy.commons.securebox.SecureBoxHelper
import com.android.contacts.R
import com.android.contacts.adapters.ContactsAdapter
import com.android.contacts.databinding.ActivitySelectContactsBinding
import com.android.contacts.helpers.*
import java.util.Locale

class SecureBoxActivity : SimpleActivity() {
    private val binding by viewBinding(ActivitySelectContactsBinding::inflate)
    
    private var allContacts = ArrayList<Contact>()
    private var currentAdapter: ContactsAdapter? = null
    private var mSearchView: androidx.appcompat.widget.SearchView? = null
    private var mSearchMenuItem: MenuItem? = null
    
    private lateinit var secureBoxHelper: SecureBoxHelper
    private lateinit var biometricUnlockHelper: BiometricUnlockHelper
    private var cipherNumber: Int = 1

    companion object {
        const val EXTRA_CIPHER_NUMBER = "cipher_number"
        private const val DEFAULT_CIPHER_NUMBER = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Read cipher number from intent, default to 1 if not provided
        cipherNumber = intent?.getIntExtra(EXTRA_CIPHER_NUMBER, DEFAULT_CIPHER_NUMBER) ?: DEFAULT_CIPHER_NUMBER
        
        setContentView(binding.root)
        updateTextColors(binding.selectContactsCoordinator)
        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.selectContactsCoordinator))

        secureBoxHelper = SecureBoxHelper(this)
        biometricUnlockHelper = BiometricUnlockHelper(this)

        val useSurfaceColor = isDynamicTheme() && !isSystemInDarkMode()
        val backgroundColor = if (useSurfaceColor) getSurfaceColor() else getProperBackgroundColor()
        binding.selectContactsCoordinator.setBackgroundColor(backgroundColor)

        setupOptionsMenu()
        binding.selectContactsMenu.apply {
            updateTitle(getString(R.string.secure_box))
            searchBeVisibleIf(false)
        }

        // Unlock secure box and load contacts
        unlockAndLoadContacts()
    }

    private fun unlockAndLoadContacts() {
        // Check if biometric is available
        if (biometricUnlockHelper.isBiometricAvailable()) {
            biometricUnlockHelper.onUnlockSuccess = {
                loadContacts()
            }
            biometricUnlockHelper.onUnlockError = { errorCode, errString ->
                toast("Authentication error: $errString")
                finish()
            }
            biometricUnlockHelper.onUnlockFailed = {
                toast("Authentication failed")
            }
            biometricUnlockHelper.showBiometricPrompt(
                title = "Unlock Secure Box",
                subtitle = "Use your fingerprint or face to unlock"
            )
        } else {
            // If biometric is not available, unlock directly (for testing/development)
            SecureBoxHelper.unlockSecureBox()
            loadContacts()
        }
    }

    override fun onResume() {
        super.onResume()
        val useSurfaceColor = isDynamicTheme() && !isSystemInDarkMode()
        val backgroundColor = if (useSurfaceColor) getSurfaceColor() else getProperBackgroundColor()
        setupTopAppBar(binding.selectContactsMenu, NavigationIcon.Arrow, topBarColor = backgroundColor)

        // Lock secure box when leaving the activity
        if (!isChangingConfigurations) {
            SecureBoxHelper.resetUnlockState()
        }
    }

    override fun onPause() {
        super.onPause()
        // Lock secure box when activity is paused
        if (!isChangingConfigurations) {
            SecureBoxHelper.lockSecureBox()
        }
    }

    private fun loadContacts() {
        ensureBackgroundThread {
            // Get secure box contacts with cipher number 1
            val secureBoxContacts = secureBoxHelper.getSecureBoxContactsByCipherNumber(cipherNumber)
            
            if (secureBoxContacts.isEmpty()) {
                runOnUiThread {
                    setupContactsList()
                    setupFastscroller()
                }
                return@ensureBackgroundThread
            }

            // Get all contacts
            ContactsHelper(this).getContacts { contacts ->
                // Filter to only show contacts that are in secure box with cipher number 1
                val secureBoxContactIds = secureBoxContacts.map { it.contactId }.toSet()
                allContacts = contacts.filter { secureBoxContactIds.contains(it.id) } as ArrayList<Contact>

                val contactSources = getVisibleContactSources()
                allContacts = allContacts.filter { contactSources.contains(it.source) } as ArrayList<Contact>

                runOnUiThread {
                    setupContactsList()
                    setupFastscroller()
                }
            }
        }
    }

    private fun setupContactsList() {
        binding.apply {
            currentAdapter = ContactsAdapter(
                activity = this@SecureBoxActivity,
                contactItems = allContacts.toMutableList(),
                refreshListener = null,
                location = LOCATION_CONTACTS_TAB,
                removeListener = null,
                recyclerView = selectContactList,
                enableDrag = false,
            ) { contact ->
                // Open contact view
                Intent(this@SecureBoxActivity, ViewContactActivity::class.java).apply {
                    putExtra(CONTACT_ID, (contact as Contact).id)
                    startActivity(this)
                }
            }
            selectContactList.adapter = currentAdapter

            if (areSystemAnimationsEnabled) {
                selectContactList.scheduleLayoutAnimation()
            }

            selectContactList.beVisibleIf(allContacts.isNotEmpty())
            selectContactPlaceholder.beVisibleIf(allContacts.isEmpty())
        }
    }

    private fun setupFastscroller() {
        val adjustedPrimaryColor = getProperAccentColor()
        binding.apply {
            letterFastscroller.textColor = getProperTextColor().getColorStateList()
            letterFastscroller.pressedTextColor = adjustedPrimaryColor
            letterFastscrollerThumb.fontSize = getTextSize()
            letterFastscrollerThumb.textColor = adjustedPrimaryColor.getContrastColor()
            letterFastscrollerThumb.thumbColor = adjustedPrimaryColor.getColorStateList()
            letterFastscrollerThumb.setupWithFastScroller(letterFastscroller)
        }

        binding.letterFastscroller.setupWithRecyclerView(binding.selectContactList, { position ->
            try {
                val name = allContacts[position].getNameToDisplay()
                val emoji = name.take(2)
                val character = if (emoji.isEmoji()) emoji else if (name.isNotEmpty()) name.substring(0, 1) else ""
                FastScrollItemIndicator.Text(character.normalizeString().uppercase(Locale.getDefault()))
            } catch (_: Exception) {
                FastScrollItemIndicator.Text("")
            }
        })

        try {
            val allNotEmpty = allContacts.filter { it.getNameToDisplay().isNotEmpty() }
            val all = allNotEmpty.map { it.getNameToDisplay().substring(0, 1) }
            val unique: Set<String> = HashSet(all)
            val sizeUnique = unique.size

            if (isHighScreenSize()) {
                if (sizeUnique > 39) binding.letterFastscroller.textAppearanceRes = R.style.LetterFastscrollerStyleTooTiny
                else if (sizeUnique > 32) binding.letterFastscroller.textAppearanceRes = R.style.LetterFastscrollerStyleTiny
                else binding.letterFastscroller.textAppearanceRes = R.style.LetterFastscrollerStyleSmall
            } else {
                if (sizeUnique > 49) binding.letterFastscroller.textAppearanceRes = R.style.LetterFastscrollerStyleTooTiny
                else if (sizeUnique > 37) binding.letterFastscroller.textAppearanceRes = R.style.LetterFastscrollerStyleTiny
                else binding.letterFastscroller.textAppearanceRes = R.style.LetterFastscrollerStyleSmall
            }
        } catch (_: Exception) { }
    }

    private fun isHighScreenSize(): Boolean {
        return when (resources.configuration.screenLayout
            and Configuration.SCREENLAYOUT_LONG_MASK) {
            Configuration.SCREENLAYOUT_LONG_NO -> false
            else -> true
        }
    }

    private fun setupOptionsMenu() {
        binding.selectContactsMenu.apply {
            requireCustomToolbar().inflateMenu(R.menu.menu_select_contacts)
            requireCustomToolbar().menu.findItem(R.id.done)?.isVisible = false
            requireCustomToolbar().menu.findItem(R.id.select_all)?.isVisible = false
            requireCustomToolbar().menu.findItem(R.id.deselect_all)?.isVisible = false
            setupSearch(requireCustomToolbar().menu)
        }
    }

    private fun setupSearch(menu: Menu) {
        updateMenuItemColors(menu)
        val searchManager = getSystemService(android.content.Context.SEARCH_SERVICE) as android.app.SearchManager
        mSearchMenuItem = menu.findItem(R.id.search)
        var actionView = mSearchMenuItem!!.actionView
        if (actionView == null) {
            actionView = androidx.core.view.MenuItemCompat.getActionView(mSearchMenuItem!!)
        }
        if (actionView == null) {
            // If actionView is still null, create it manually
            val searchView = androidx.appcompat.widget.SearchView(this)
            androidx.core.view.MenuItemCompat.setActionView(mSearchMenuItem!!, searchView)
            actionView = searchView
        }
        val searchView = (actionView as? androidx.appcompat.widget.SearchView) ?: throw IllegalStateException("SearchView actionView could not be created")
        mSearchView = searchView
        searchView.apply {
            val textColor = getProperTextColor()
            val smallPadding = resources.getDimensionPixelSize(com.goodwy.commons.R.dimen.small_margin)
            
            post {
                findViewById<android.widget.TextView>(androidx.appcompat.R.id.search_src_text)?.apply {
                    setTextColor(textColor)
                    setHintTextColor(textColor)
                    setPadding(smallPadding, paddingTop, paddingRight, paddingBottom)
                }
                findViewById<android.widget.ImageView>(androidx.appcompat.R.id.search_close_btn)?.apply {
                    setImageResource(com.goodwy.commons.R.drawable.ic_clear_round)
                    setColorFilter(textColor)
                }
                findViewById<android.view.View>(androidx.appcompat.R.id.search_plate)?.apply {
                    background?.setColorFilter(android.graphics.Color.TRANSPARENT, android.graphics.PorterDuff.Mode.MULTIPLY)
                    setPadding(smallPadding, paddingTop, paddingRight, paddingBottom)
                }
                findViewById<android.widget.ImageView>(androidx.appcompat.R.id.search_mag_icon)?.apply {
                    setColorFilter(textColor)
                }
            }

            setIconifiedByDefault(false)
            setSearchableInfo(searchManager.getSearchableInfo(componentName))
            isSubmitButtonEnabled = false
            queryHint = getString(R.string.search_contacts)
            setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String) = false

                override fun onQueryTextChange(newText: String): Boolean {
                    filterContactListBySearchQuery(newText)
                    return true
                }
            })
        }

        androidx.core.view.MenuItemCompat.setOnActionExpandListener(mSearchMenuItem, object : androidx.core.view.MenuItemCompat.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem?): Boolean {
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem?): Boolean {
                filterContactListBySearchQuery("")
                return true
            }
        })
    }

    private fun filterContactListBySearchQuery(query: String = "") {
        var contactsToShow = allContacts
        if (query.isNotEmpty()) {
            contactsToShow = contactsToShow.filter { it.name.contains(query, true) }.toMutableList() as ArrayList<Contact>
        }
        checkPlaceholderVisibility(contactsToShow)

        currentAdapter?.updateItems(contactsToShow)
    }

    private fun checkPlaceholderVisibility(contacts: ArrayList<Contact>) = with(binding) {
        selectContactPlaceholder.beVisibleIf(allContacts.isEmpty())

        if (mSearchView?.isIconified == false && mSearchView?.query?.isNotEmpty() == true) {
            selectContactPlaceholder.text = getString(com.goodwy.commons.R.string.no_items_found)
        }

        letterFastscroller.beVisibleIf(selectContactPlaceholder.isGone())
        letterFastscrollerThumb.beVisibleIf(selectContactPlaceholder.isGone())
    }
}

