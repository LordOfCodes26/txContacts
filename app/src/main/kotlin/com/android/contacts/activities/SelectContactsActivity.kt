package com.android.contacts.activities

import android.content.Intent
import android.content.res.Configuration
import android.content.res.TypedArray
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.RelativeLayout
import com.reddit.indicatorfastscroll.FastScrollItemIndicator
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.ContactsHelper
import com.goodwy.commons.helpers.NavigationIcon
import com.goodwy.commons.helpers.ensureBackgroundThread
import com.goodwy.commons.models.contacts.Contact
import com.goodwy.commons.views.MySearchMenu
import com.android.contacts.R
import com.android.contacts.adapters.SelectContactsAdapter
import com.android.contacts.databinding.ActivitySelectContactsBinding
import com.android.contacts.helpers.*
import eightbitlab.com.blurview.BlurTarget
import java.util.Locale

class SelectContactsActivity : SimpleActivity() {
    private val binding by viewBinding(ActivitySelectContactsBinding::inflate)
    
    private var allContacts = ArrayList<Contact>()
    private var initiallySelectedContacts = ArrayList<Contact>()
    private val selectedContacts = HashSet<Contact>()
    private var currentAdapter: SelectContactsAdapter? = null
    private var searchQuery = ""
    
    private var allowSelectMultiple = true
    private var showOnlyContactsWithNumber = false
    private var contactClickCallback: ((Contact) -> Unit)? = null
    private var selectedContactIds = emptyList<Long>()

    companion object {
        const val EXTRA_ALLOW_SELECT_MULTIPLE = "allow_select_multiple"
        const val EXTRA_SHOW_ONLY_CONTACTS_WITH_NUMBER = "show_only_contacts_with_number"
        const val EXTRA_SELECTED_CONTACT_IDS = "selected_contact_ids"
        const val RESULT_ADDED_CONTACT_IDS = "added_contact_ids"
        const val RESULT_REMOVED_CONTACT_IDS = "removed_contact_ids"
    }
    
    private var wasSelectedContactIdsProvided = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        updateTextColors(binding.selectContactsCoordinator)
        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.selectContactsCoordinator))

        allowSelectMultiple = intent.getBooleanExtra(EXTRA_ALLOW_SELECT_MULTIPLE, true)
        showOnlyContactsWithNumber = intent.getBooleanExtra(EXTRA_SHOW_ONLY_CONTACTS_WITH_NUMBER, false)
        wasSelectedContactIdsProvided = intent.hasExtra(EXTRA_SELECTED_CONTACT_IDS)
        selectedContactIds = intent.getLongArrayExtra(EXTRA_SELECTED_CONTACT_IDS)?.toList() ?: emptyList()

        val useSurfaceColor = isDynamicTheme() && !isSystemInDarkMode()
        val backgroundColor = if (useSurfaceColor) getSurfaceColor() else getProperBackgroundColor()
        binding.selectContactsCoordinator.setBackgroundColor(backgroundColor)

        setupOptionsMenu()
        binding.selectContactsMenu.apply {
            updateTitle(getString(R.string.select_contact))
            searchBeVisibleIf(false)
        }
    }

    override fun onResume() {
        super.onResume()
        val useSurfaceColor = isDynamicTheme() && !isSystemInDarkMode()
        val backgroundColor = if (useSurfaceColor) getSurfaceColor() else getProperBackgroundColor()
        setupTopAppBar(binding.selectContactsMenu, NavigationIcon.None, topBarColor = backgroundColor)
        
        // Explicitly remove navigation icon
        val customToolbar = binding.selectContactsMenu.requireCustomToolbar()
        customToolbar.navigationIcon = null
        
        // Ensure toolbar is full width by removing margins
        customToolbar.onGlobalLayout {
            val params = customToolbar.layoutParams
            if (params is RelativeLayout.LayoutParams) {
                params.width = ViewGroup.LayoutParams.MATCH_PARENT
                params.marginEnd = 0
                params.removeRule(RelativeLayout.ALIGN_PARENT_END)
                customToolbar.layoutParams = params
            } else if (params != null) {
                params.width = ViewGroup.LayoutParams.MATCH_PARENT
                customToolbar.layoutParams = params
            }
        }

        // if selecting multiple contacts is disabled, react on first contact click and finish the activity
        contactClickCallback = if (allowSelectMultiple) {
            null
        } else { contact ->
            val resultIntent = Intent().apply {
                putExtra(RESULT_ADDED_CONTACT_IDS, longArrayOf(contact.id.toLong()))
                putExtra(RESULT_REMOVED_CONTACT_IDS, longArrayOf())
            }
            setResult(RESULT_OK, resultIntent)
            finish()
        }

        loadContacts()
    }
    
    override fun onBackPressed() {
        val customToolbar = binding.selectContactsMenu.requireCustomToolbar()
        if (customToolbar.isSearchExpanded) {
            customToolbar.collapseSearch()
            binding.selectContactsMenu.isSearchOpen = false
            searchQuery = ""
            filterContactListBySearchQuery("")
        } else {
            super.onBackPressed()
        }
    }


    private fun loadContacts() {
        ensureBackgroundThread {
            ContactsHelper(this).getContacts { contacts ->
                allContacts = contacts
                
                val contactSources = getVisibleContactSources()
                allContacts = allContacts.filter { contactSources.contains(it.source) } as ArrayList<Contact>

                if (showOnlyContactsWithNumber) {
                    allContacts = allContacts.filter { it.phoneNumbers.isNotEmpty() }.toMutableList() as ArrayList<Contact>
                }

                // Load initially selected contacts from IDs if provided
                // Use contacts from allContacts list to ensure same instances are used
                if (wasSelectedContactIdsProvided) {
                    // Extra was explicitly provided (even if empty), use it
                    if (selectedContactIds.isNotEmpty()) {
                        val selectedIdsSet = selectedContactIds.toSet()
                        initiallySelectedContacts = allContacts.filter { contact ->
                            selectedIdsSet.contains(contact.id.toLong())
                        } as ArrayList<Contact>
                    }
                    // If empty, initiallySelectedContacts stays empty (no contacts pre-selected)
                } else if (initiallySelectedContacts.isEmpty()) {
                    // If no IDs provided at all, use starred contacts as default
                    initiallySelectedContacts = allContacts.filter { it.starred == 1 } as ArrayList<Contact>
                }
                
                selectedContacts.clear()
                selectedContacts.addAll(initiallySelectedContacts)

                runOnUiThread {
                    setupContactsList()
                    setupFastscroller()
                    updateMenuItems()
                }
            }
        }
    }

    private fun setupContactsList() {
        binding.apply {
            currentAdapter = SelectContactsAdapter(
                this@SelectContactsActivity,
                allContacts,
                selectedContacts,
                allowSelectMultiple,
                selectContactList,
                ::onSelectionChanged,
                contactClickCallback
            )
            selectContactList.adapter = currentAdapter

            if (areSystemAnimationsEnabled) {
                selectContactList.scheduleLayoutAnimation()
            }

            selectContactList.beVisibleIf(allContacts.isNotEmpty())
            selectContactPlaceholder.beVisibleIf(allContacts.isEmpty())
        }
        updateMenuItems()
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
            val customToolbar = requireCustomToolbar()
            customToolbar.inflateMenu(R.menu.menu_select_contacts)
            val menu = customToolbar.menu
            
            updateMenuItemColors(menu)
            
            // Update menu item visibility based on current state
            updateMenuItems()
            
            // Setup search text changed listener for CustomToolbar
            customToolbar.setOnSearchTextChangedListener { text ->
                searchQuery = text
                filterContactListBySearchQuery(text)
            }
            
            // Setup search expand/collapse listeners
            customToolbar.setOnSearchExpandListener {
                isSearchOpen = true
                // Restore previous search query if it exists
                if (searchQuery.isNotEmpty()) {
                    customToolbar.setSearchText(searchQuery)
                    filterContactListBySearchQuery(searchQuery)
                }
            }
            
            // Setup search back click listener
            customToolbar.setOnSearchBackClickListener {
                // CustomToolbar already calls collapseSearch() internally
                isSearchOpen = false
                searchQuery = ""
                filterContactListBySearchQuery("")
            }
            
            // Setup menu item click listener
            customToolbar.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.search -> {
                        toggleCustomSearchBar()
                        true
                    }
                    R.id.select_all -> {
                        selectAll()
                        true
                    }
                    R.id.deselect_all -> {
                        deselectAll()
                        true
                    }
                    R.id.done -> {
                        confirmSelection()
                        true
                    }
                    else -> false
                }
            }
            
            // Hide the menu button (overflow menu) by setting overflowIcon to null
            customToolbar.overflowIcon = null
        }
    }
    
    private fun toggleCustomSearchBar() {
        val customToolbar = binding.selectContactsMenu.requireCustomToolbar()
        if (customToolbar.isSearchExpanded) {
            customToolbar.collapseSearch()
            binding.selectContactsMenu.isSearchOpen = false
            searchQuery = ""
            filterContactListBySearchQuery("")
        } else {
            customToolbar.expandSearch()
            binding.selectContactsMenu.isSearchOpen = true
        }
    }

    private fun filterContactListBySearchQuery(query: String = "") {
        var contactsToShow = allContacts
        if (query.isNotEmpty()) {
            contactsToShow = contactsToShow.filter { it.name.contains(query, true) }.toMutableList() as ArrayList<Contact>
        }
        checkPlaceholderVisibility(contactsToShow)

        currentAdapter?.updateContacts(contactsToShow)
    }

    private fun checkPlaceholderVisibility(contacts: ArrayList<Contact>) = with(binding) {
        selectContactPlaceholder.beVisibleIf(allContacts.isEmpty())

        val customToolbar = selectContactsMenu.requireCustomToolbar()
        if (customToolbar.isSearchExpanded && searchQuery.isNotEmpty()) {
            selectContactPlaceholder.text = getString(com.goodwy.commons.R.string.no_items_found)
        }

        letterFastscroller.beVisibleIf(selectContactPlaceholder.isGone())
        letterFastscrollerThumb.beVisibleIf(selectContactPlaceholder.isGone())
    }

    private fun onSelectionChanged(contacts: HashSet<Contact>) {
        // selectedContacts is already updated in the adapter, nothing needs to be done
        updateMenuItems()
    }
    
    private fun updateMenuItems() {
        val customToolbar = binding.selectContactsMenu.requireCustomToolbar()
        val menu = customToolbar.menu
        
        val allSelected = selectedContacts.size == allContacts.size && allContacts.isNotEmpty()
        val noneSelected = selectedContacts.isEmpty()
        
        menu.findItem(R.id.select_all)?.isVisible = !allSelected && allContacts.isNotEmpty() && allowSelectMultiple
        menu.findItem(R.id.deselect_all)?.isVisible = !noneSelected && allowSelectMultiple
        menu.findItem(R.id.done)?.isVisible = allowSelectMultiple
        
        customToolbar.invalidateMenu()
    }
    
    private fun selectAll() {
        if (!allowSelectMultiple) return
        selectedContacts.clear()
        selectedContacts.addAll(allContacts)
        // Update adapter to reflect selection changes
        currentAdapter?.refreshSelection()
        onSelectionChanged(selectedContacts)
        updateMenuItems()
    }
    
    private fun deselectAll() {
        if (!allowSelectMultiple) return
        selectedContacts.clear()
        // Update adapter to reflect selection changes
        currentAdapter?.refreshSelection()
        onSelectionChanged(selectedContacts)
        updateMenuItems()
    }


    private fun confirmSelection() {
        ensureBackgroundThread {
            val selectedContactsList = ArrayList(selectedContacts)

            val newlySelectedContacts = selectedContactsList.filter { !initiallySelectedContacts.contains(it) }
            val unselectedContacts = initiallySelectedContacts.filter { !selectedContactsList.contains(it) }

            val resultIntent = Intent().apply {
                putExtra(RESULT_ADDED_CONTACT_IDS, newlySelectedContacts.map { it.id.toLong() }.toLongArray())
                putExtra(RESULT_REMOVED_CONTACT_IDS, unselectedContacts.map { it.id.toLong() }.toLongArray())
            }
            runOnUiThread {
                setResult(RESULT_OK, resultIntent)
                finish()
            }
        }
    }
}

