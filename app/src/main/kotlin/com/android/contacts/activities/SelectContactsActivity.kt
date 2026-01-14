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
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.view.MenuItemCompat
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
    private var mSearchView: androidx.appcompat.widget.SearchView? = null
    private var mSearchMenuItem: MenuItem? = null
    
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
        setupTopAppBar(binding.selectContactsMenu, NavigationIcon.Arrow, topBarColor = backgroundColor)

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
            requireCustomToolbar().inflateMenu(R.menu.menu_select_contacts)
            val menu = requireCustomToolbar().menu
            
            // Hide all menu items from the overflow menu since we're using action buttons
            menu.findItem(R.id.done)?.isVisible = false
            menu.findItem(R.id.select_all)?.isVisible = false
            menu.findItem(R.id.deselect_all)?.isVisible = false
            
            setupSearch(menu)
            
            // Hide the menu button (overflow menu) by setting overflowIcon to null
            requireCustomToolbar().overflowIcon = null
            
            // Setup action buttons directly in toolbar
            setupActionButtons()
            
            // Update action buttons visibility
            updateMenuItems()
            
            requireCustomToolbar().setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.done -> {
                        confirmSelection()
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
                    else -> false
                }
            }
        }
    }
    
    private var doneButton: ImageButton? = null
    private var selectAllButton: ImageButton? = null
    
    private fun setupActionButtons() {
        val toolbar = binding.selectContactsMenu.requireCustomToolbar()
        val toolbarRoot = toolbar.getChildAt(0) as? ViewGroup ?: return
        val relativeLayout = toolbarRoot.getChildAt(0) as? RelativeLayout ?: return
        val activityContext = this@SelectContactsActivity
        
        // Find the search icon button to position our buttons next to it
        val searchIconButton = toolbarRoot.findViewById<ImageView>(com.goodwy.commons.R.id.searchIconButton)
        val iconSize = resources.getDimensionPixelSize(com.goodwy.commons.R.dimen.medium_icon_size)
        val smallerMargin = resources.getDimensionPixelSize(com.goodwy.commons.R.dimen.smaller_margin)
        
        // Create select all button (positioned to the left of search icon)
        selectAllButton = ImageButton(activityContext).apply {
            id = View.generateViewId()
            layoutParams = RelativeLayout.LayoutParams(iconSize, iconSize).apply {
                if (searchIconButton != null) {
                    addRule(RelativeLayout.LEFT_OF, searchIconButton.id)
                } else {
                    addRule(RelativeLayout.ALIGN_PARENT_END)
                }
                addRule(RelativeLayout.CENTER_VERTICAL)
                marginEnd = smallerMargin
            }
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            adjustViewBounds = true
            val typedValue = TypedValue()
            activityContext.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, typedValue, true)
            background = ContextCompat.getDrawable(activityContext, typedValue.resourceId)
            isClickable = true
            isFocusable = true
            
            val selectAllIcon = ContextCompat.getDrawable(activityContext, com.goodwy.commons.R.drawable.ic_select_all_vector)
            selectAllIcon?.let {
                val textColor = activityContext.getProperTextColor()
                it.applyColorFilter(textColor)
                setImageDrawable(it)
            }
            
            contentDescription = activityContext.getString(com.goodwy.commons.R.string.select_all)
            
            setOnClickListener {
                activityContext.selectAll()
            }
        }
        
        // Create done/confirm button (positioned to the left of select all button)
        doneButton = ImageButton(activityContext).apply {
            id = View.generateViewId()
            layoutParams = RelativeLayout.LayoutParams(iconSize, iconSize).apply {
                addRule(RelativeLayout.LEFT_OF, selectAllButton!!.id)
                addRule(RelativeLayout.CENTER_VERTICAL)
                marginEnd = smallerMargin
            }
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            adjustViewBounds = true
            val typedValue = TypedValue()
            activityContext.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, typedValue, true)
            background = ContextCompat.getDrawable(activityContext, typedValue.resourceId)
            isClickable = true
            isFocusable = true
            
            val doneIcon = ContextCompat.getDrawable(activityContext, com.goodwy.commons.R.drawable.ic_check_vector)
            doneIcon?.let {
                val textColor = activityContext.getProperTextColor()
                it.applyColorFilter(textColor)
                setImageDrawable(it)
            }
            
            contentDescription = activityContext.getString(com.goodwy.commons.R.string.ok)
            visibility = if (allowSelectMultiple) View.VISIBLE else View.GONE
            
            setOnClickListener {
                activityContext.confirmSelection()
            }
        }
        
        // Add buttons to toolbar (done button first, then select all)
        relativeLayout.addView(doneButton)
        relativeLayout.addView(selectAllButton)
    }

    private fun setupSearch(menu: Menu) {
        updateMenuItemColors(menu)
        val searchManager = getSystemService(android.content.Context.SEARCH_SERVICE) as android.app.SearchManager
        mSearchMenuItem = menu.findItem(R.id.search)
        var actionView = mSearchMenuItem!!.actionView
        if (actionView == null) {
            actionView = MenuItemCompat.getActionView(mSearchMenuItem!!)
        }
        if (actionView == null) {
            // If actionView is still null, create it manually
            val searchView = SearchView(this)
            MenuItemCompat.setActionView(mSearchMenuItem!!, searchView)
            actionView = searchView
        }
        val searchView = (actionView as? SearchView) ?: throw IllegalStateException("SearchView actionView could not be created")
        mSearchView = searchView
        searchView.apply {
            val textColor = getProperTextColor()
            val smallPadding = resources.getDimensionPixelSize(com.goodwy.commons.R.dimen.small_margin)
            
            // Post to ensure views are inflated
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
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String) = false

                override fun onQueryTextChange(newText: String): Boolean {
                    filterContactListBySearchQuery(newText)
                    return true
                }
            })
        }

        MenuItemCompat.setOnActionExpandListener(mSearchMenuItem, object : MenuItemCompat.OnActionExpandListener {
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

        currentAdapter?.updateContacts(contactsToShow)
    }

    private fun checkPlaceholderVisibility(contacts: ArrayList<Contact>) = with(binding) {
        selectContactPlaceholder.beVisibleIf(allContacts.isEmpty())

        if (mSearchView?.isIconified == false && mSearchView?.query?.isNotEmpty() == true) {
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
        val allSelected = selectedContacts.size == allContacts.size && allContacts.isNotEmpty()
        val noneSelected = selectedContacts.isEmpty()
        
        // Update select all button visibility and behavior
        selectAllButton?.visibility = if (allowSelectMultiple && allContacts.isNotEmpty()) {
            View.VISIBLE
        } else {
            View.GONE
        }
        
        // Toggle select all button behavior based on selection state
        if (allSelected && selectAllButton != null) {
            // When all are selected, button acts as "deselect all"
            val deselectIcon = ContextCompat.getDrawable(this, com.goodwy.commons.R.drawable.ic_select_all_vector)
            deselectIcon?.let {
                val textColor = getProperTextColor()
                it.applyColorFilter(textColor)
                selectAllButton?.setImageDrawable(it)
            }
            selectAllButton?.setOnClickListener {
                deselectAll()
            }
            selectAllButton?.contentDescription = getString(R.string.deselect_all)
        } else if (selectAllButton != null) {
            // When not all are selected, button acts as "select all"
            val selectIcon = ContextCompat.getDrawable(this, com.goodwy.commons.R.drawable.ic_select_all_vector)
            selectIcon?.let {
                val textColor = getProperTextColor()
                it.applyColorFilter(textColor)
                selectAllButton?.setImageDrawable(it)
            }
            selectAllButton?.setOnClickListener {
                selectAll()
            }
            selectAllButton?.contentDescription = getString(com.goodwy.commons.R.string.select_all)
        }
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

