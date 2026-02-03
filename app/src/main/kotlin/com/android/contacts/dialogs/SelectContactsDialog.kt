package com.android.contacts.dialogs

import android.content.res.Configuration
import android.graphics.Color
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AlertDialog
import com.reddit.indicatorfastscroll.FastScrollItemIndicator
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.ensureBackgroundThread
import com.goodwy.commons.models.contacts.Contact
import com.goodwy.commons.views.MySearchMenuTop
import com.android.contacts.R
import com.android.contacts.activities.SimpleActivity
import com.android.contacts.adapters.SelectContactsAdapter
import com.android.contacts.databinding.DialogSelectContactBinding
import eightbitlab.com.blurview.BlurTarget
import eightbitlab.com.blurview.BlurView
import java.util.Locale

class SelectContactsDialog(
    val activity: SimpleActivity, initialContacts: ArrayList<Contact>, val allowSelectMultiple: Boolean, val showOnlyContactsWithNumber: Boolean,
    selectContacts: ArrayList<Contact>? = null, blurTarget: BlurTarget, val callback: (addedContacts: ArrayList<Contact>, removedContacts: ArrayList<Contact>) -> Unit
) {
    private var dialog: AlertDialog? = null
    private val binding = DialogSelectContactBinding.inflate(activity.layoutInflater)
    private var initiallySelectedContacts = ArrayList<Contact>()
    private var allContacts = initialContacts
    private var contactClickCallback: ((Contact) -> Unit)? = null

    private var currentAdapter: SelectContactsAdapter? = null
    private val selectedContacts = HashSet<Contact>()
    private val searchView = binding.selectContactSearchView
    private val searchEditText = searchView.binding.topToolbarSearch
    // private val searchViewAppBarLayout = searchView.binding.topAppBarLayout // topAppBarLayout not available in MySearchMenuTop

    init {
        if (selectContacts == null) {
            val contactSources = activity.getVisibleContactSources()
            allContacts = allContacts.filter { contactSources.contains(it.source) } as ArrayList<Contact>

            if (showOnlyContactsWithNumber) {
                allContacts = allContacts.filter { it.phoneNumbers.isNotEmpty() }.toMutableList() as ArrayList<Contact>
            }

            initiallySelectedContacts = allContacts.filter { it.starred == 1 } as ArrayList<Contact>
        } else {
            initiallySelectedContacts = selectContacts
        }
        selectedContacts.addAll(initiallySelectedContacts)

        // if selecting multiple contacts is disabled, react on first contact click and dismiss the dialog
        contactClickCallback = if (allowSelectMultiple) {
            null
        } else { contact ->
            callback(arrayListOf(contact), arrayListOf())
            dialog!!.dismiss()
        }

        binding.apply {
            currentAdapter = SelectContactsAdapter(
                activity, allContacts, selectedContacts, allowSelectMultiple,
                selectContactList, ::onSelectionChanged, contactClickCallback
            )
            selectContactList.adapter = currentAdapter

            if (root.context.areSystemAnimationsEnabled) {
                selectContactList.scheduleLayoutAnimation()
            }

            selectContactList.beVisibleIf(allContacts.isNotEmpty())
            selectContactPlaceholder.beVisibleIf(allContacts.isEmpty())
        }

        configureSearchView()
        setupFastscroller(allContacts)

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

        if (allowSelectMultiple) {
            buttonsContainer?.visibility = android.view.View.VISIBLE
            positiveButton?.apply {
                visibility = android.view.View.VISIBLE
                setTextColor(primaryColor)
                setOnClickListener { dialogConfirmed() }
            }
        }
        negativeButton?.apply {
            visibility = android.view.View.VISIBLE
            setTextColor(primaryColor)
            setOnClickListener { dialog?.dismiss() }
        }

        val builder = activity.getAlertDialogBuilder()
        builder.apply {
            activity.setupDialogStuff(binding.root, this, titleText = "") { alertDialog ->
                dialog = alertDialog
            }
        }
    }

    // Callback when selection changes
    private fun onSelectionChanged(contacts: HashSet<Contact>) {
        // selectedContacts is already updated in the adapter, nothing needs to be done
    }

    private fun dialogConfirmed() {
        ensureBackgroundThread {
            val selectedContactsList = ArrayList(selectedContacts)

            val newlySelectedContacts = selectedContactsList.filter { !initiallySelectedContacts.contains(it) } as ArrayList<Contact>
            val unselectedContacts = initiallySelectedContacts.filter { !selectedContactsList.contains(it) } as ArrayList<Contact>
            callback(newlySelectedContacts, unselectedContacts)
        }
    }

    private fun setupFastscroller(allContacts: ArrayList<Contact>) {
        val adjustedPrimaryColor = activity.getProperAccentColor()
        binding.apply {
            letterFastscroller.textColor = root.context.getProperTextColor().getColorStateList()
            letterFastscroller.pressedTextColor = adjustedPrimaryColor
            letterFastscrollerThumb.fontSize = root.context.getTextSize()
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
            //Decrease the font size based on the number of letters in the letter scroller
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
        return when (activity.resources.configuration.screenLayout
            and Configuration.SCREENLAYOUT_LONG_MASK) {
            Configuration.SCREENLAYOUT_LONG_NO -> false
            else -> true
        }
    }

    private fun configureSearchView() = with(searchView) {
        updateHintText(context.getString(R.string.search_contacts))
        searchEditText.imeOptions = EditorInfo.IME_ACTION_DONE

        toggleHideOnScroll(false)
        setupMenu()
        setSearchViewListeners()
        updateSearchViewUi()
    }

    private fun MySearchMenuTop.updateSearchViewUi() {
        toolbar?.beInvisible()

        val backgroundColor = when {
            context.isDynamicTheme() -> resources.getColor(com.goodwy.commons.R.color.you_dialog_background_color, context.theme)
            context.isBlackTheme() -> context.getSurfaceColor()
            else -> context.baseConfig.backgroundColor
        }
        updateColors(background = backgroundColor)
        setBackgroundColor(Color.TRANSPARENT)
        // searchViewAppBarLayout not available in MySearchMenuTop - using setBackgroundColor on the view itself
    }

    private fun MySearchMenuTop.setSearchViewListeners() {
        onSearchOpenListener = {
            updateSearchViewLeftIcon(com.goodwy.commons.R.drawable.ic_chevron_left_vector)
        }

        onSearchClosedListener = {
            searchEditText.clearFocus()
            activity.hideKeyboard(searchEditText)
            updateSearchViewLeftIcon(com.android.common.R.drawable.ic_cmn_search)
        }

        onSearchTextChangedListener = { text ->
            filterContactListBySearchQuery(text)
            clearSearch()
        }
    }

    private fun updateSearchViewLeftIcon(iconResId: Int) = with(searchView.binding.topToolbarSearchIcon) {
        post {
            setImageResource(iconResId)
        }
    }

    private fun filterContactListBySearchQuery(query: String = "") {
        var contactsToShow = allContacts
        if (query.isNotEmpty()) {
            contactsToShow = contactsToShow.filter { it.name.contains(query, true) }.toMutableList() as ArrayList<Contact>
        }
        checkPlaceholderVisibility(contactsToShow)

        // We simply update the data in the existing adapter.
        // selectedContacts is saved automatically since it is a shared object
        currentAdapter?.updateContacts(contactsToShow)
    }

    private fun checkPlaceholderVisibility(dirs: ArrayList<Contact>) = with(binding) {
        selectContactPlaceholder.beVisibleIf(allContacts.isEmpty())

        if (selectContactSearchView.isSearchOpen) {
            selectContactPlaceholder.text = root.context.getString(com.goodwy.commons.R.string.no_items_found)
        }

        letterFastscroller.beVisibleIf(selectContactPlaceholder.isGone())
        letterFastscrollerThumb.beVisibleIf(selectContactPlaceholder.isGone())
    }
}
