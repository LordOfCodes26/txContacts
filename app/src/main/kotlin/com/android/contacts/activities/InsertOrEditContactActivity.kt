package com.android.contacts.activities

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.speech.RecognizerIntent
import androidx.viewpager.widget.ViewPager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.goodwy.commons.extensions.*
import com.goodwy.commons.views.MyLiquidNavigationView
import com.goodwy.commons.helpers.*
import com.goodwy.commons.models.contacts.Contact
import eightbitlab.com.blurview.BlurTarget
import com.android.contacts.R
import com.android.contacts.adapters.ViewPagerAdapter
import com.android.contacts.databinding.ActivityInsertEditContactBinding
import com.android.contacts.dialogs.ChangeSortingDialog
import com.android.contacts.dialogs.FilterContactSourcesDialog
import com.android.contacts.extensions.config
import com.android.contacts.fragments.MyViewPagerFragment
import com.android.contacts.helpers.ADD_NEW_CONTACT_NUMBER
import com.android.contacts.helpers.KEY_EMAIL
import com.android.contacts.helpers.KEY_NAME
import com.android.contacts.helpers.LOCATION_CONTACTS_TAB
import com.android.contacts.helpers.LOCATION_FAVORITES_TAB
import com.android.contacts.interfaces.RefreshContactsListener
import androidx.core.graphics.drawable.toDrawable
import java.util.Objects

class InsertOrEditContactActivity : SimpleActivity(), RefreshContactsListener {
    companion object {
        private const val START_INSERT_ACTIVITY = 1
        private const val START_EDIT_ACTIVITY = 2
    }

    private var isSelectContactIntent = false
    private var specialMimeType: String? = null
    private var isSpeechToTextAvailable = false
    private val binding by viewBinding(ActivityInsertEditContactBinding::inflate)

    private val contactsFavoritesList = arrayListOf(
        TAB_FAVORITES,
        TAB_CONTACTS
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupOptionsMenu()
        isSelectContactIntent = intent.action == Intent.ACTION_PICK
        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.insertEditContactHolder))

        if (isSelectContactIntent) {
            specialMimeType = when (intent.data) {
                Email.CONTENT_URI -> Email.CONTENT_ITEM_TYPE
                Phone.CONTENT_URI -> Phone.CONTENT_ITEM_TYPE
                else -> null
            }
        }

        binding.newContactHolder.beGoneIf(isSelectContactIntent)
        //binding.selectContactLabel.beGoneIf(isSelectContactIntent)

        if (checkAppSideloading()) {
            return
        }

        setupTabs()

        //val phoneNumber = getPhoneNumberFromIntent(intent) ?: ""
        binding.insertEditMenu.updateTitle(getString(com.goodwy.strings.R.string.add_number))

        // we do not really care about the permission request result. Even if it was denied, load private contacts
        handlePermission(PERMISSION_READ_CONTACTS) {
            if (it) {
                handlePermission(PERMISSION_WRITE_CONTACTS) {
                    handlePermission(PERMISSION_GET_ACCOUNTS) {
                        initFragments()
                    }
                }
            } else {
                initFragments()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateMenuColors()
//        setupTabColors()
    }

    private fun setupOptionsMenu() {
        binding.insertEditMenu.requireCustomToolbar().inflateMenu(R.menu.menu_insert_or_edit)
        binding.insertEditMenu.toggleHideOnScroll(false)


        binding.insertEditMenu.setupMenu()

        binding.insertEditMenu.onSearchTextChangedListener = { text ->
            getCurrentFragment()?.onSearchQueryChanged(text)
            binding.insertEditMenu.clearSearch()
        }

        binding.insertEditMenu.requireCustomToolbar().setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.sort -> showSortingDialog()
                R.id.filter -> showFilterDialog()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
    }

    private fun updateMenuColors() {
        val useSurfaceColor = isDynamicTheme() && !isSystemInDarkMode()
        val backgroundColor = if (useSurfaceColor) getSurfaceColor() else getProperBackgroundColor()
        binding.insertEditContactHolder.setBackgroundColor(backgroundColor)
        window.setSystemBarsAppearance(backgroundColor)
        binding.insertEditMenu.updateColors(background = backgroundColor)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (requestCode == REQUEST_CODE_SPEECH_INPUT && resultCode == RESULT_OK) {
            if (resultData != null) {
                val res: java.util.ArrayList<String> =
                    resultData.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS) as java.util.ArrayList<String>

                val speechToText =  Objects.requireNonNull(res)[0]
                if (speechToText.isNotEmpty()) {
                    binding.insertEditMenu.setText(speechToText)
                }
            }
        } else if (resultCode == RESULT_OK) {
            hideKeyboard()
            finish()
        }
    }

    override fun onBackPressed() {
        if (binding.insertEditMenu.isSearchOpen) {
            binding.insertEditMenu.closeSearch()
        } else {
            super.onBackPressed()
        }
    }

    private fun initFragments() {
        binding.viewPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrollStateChanged(state: Int) {}

            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}

            override fun onPageSelected(position: Int) {
                binding.insertEditTabsHolder.getTabAt(position)?.select()
                getAllFragments().forEach {
                    it?.finishActMode()
                }
            }
        })

        binding.viewPager.onGlobalLayout {
            refreshContacts(getTabsMask())
        }

        binding.selectContactLabel.setTextColor(getProperPrimaryColor())
//        binding.newContactTmb.setImageDrawable(
//            resources.getColoredDrawableWithColor(
//                com.goodwy.commons.R.drawable.ic_add_person_vector,
//                getProperTextColor()
//            )
//        )
        val placeholderImage = SimpleContactsHelper(this).getContactLetterIcon("+").toDrawable(resources)
        binding.newContactTmb.setImageDrawable(placeholderImage)
        binding.newContactName.setTextColor(getProperTextColor())
        binding.newContactHolder.setOnClickListener {
            createNewContact()
        }
    }

    private fun setupTabs() {
        binding.insertEditTabsHolder.removeAllTabs()
        contactsFavoritesList.forEachIndexed { index, value ->
            if (config.showTabs and value != 0) {
                val tab = binding.insertEditTabsHolder.newTab()
                tab.setIcon(getTabIconResId(index))
                tab.setText(getTabLabel(index))
                binding.insertEditTabsHolder.addTab(tab)
            }
        }

        binding.insertEditTabsHolder.onTabSelectionChanged(
            tabUnselectedAction = {
                // MyLiquidNavigationView handles colors internally
            },
            tabSelectedAction = {
                binding.insertEditMenu.closeSearch()
                binding.viewPager.currentItem = it.position
            }
        )

        binding.insertEditTabsHolder.beGoneIf(binding.insertEditTabsHolder.tabCount == 1)
    }

    private fun getCurrentFragment(): MyViewPagerFragment<*>? {
        return if (binding.viewPager.currentItem == 0) {
            findViewById(R.id.favorites_fragment)
        } else {
            findViewById(R.id.contacts_fragment)
        }
    }

    private fun getAllFragments(): ArrayList<MyViewPagerFragment<*>?> {
        return arrayListOf<MyViewPagerFragment<*>?>(
            findViewById(R.id.favorites_fragment),
            findViewById(R.id.contacts_fragment)
        )
    }

    private fun setupTabColors() {
        // MyLiquidNavigationView handles colors internally through Compose
        // No need to manually update tab item colors
        
        val bottomBarColor =
            if (isDynamicTheme() && !isSystemInDarkMode()) getColoredMaterialStatusBarColor()
            else getSurfaceColor()
        binding.insertEditTabsHolder.setBackgroundColor(bottomBarColor)
        window.setSystemBarsAppearance(bottomBarColor)
        if (binding.insertEditTabsHolder.tabCount == 1) {
            // Handle transparent navigation bar with window insets
            ViewCompat.setOnApplyWindowInsetsListener(binding.insertEditCoordinator) { view, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                view.setPadding(systemBars.left, 0, systemBars.right, 0)
                insets
            }
        }
    }

    private fun getInactiveTabIndexes(activeIndex: Int) = (0 until binding.insertEditTabsHolder.tabCount).filter { it != activeIndex }

    private fun getSelectedTabDrawableIds() = arrayOf(
        R.drawable.ic_star_vector_scaled,
        R.drawable.ic_person_rounded_scaled
    )

    private fun getDeselectedTabDrawableIds() = arrayOf(
        com.goodwy.commons.R.drawable.ic_star_vector,
        com.goodwy.commons.R.drawable.ic_person_rounded
    )

    private fun getTabIconResId(position: Int): Int {
        return when (position) {
            LOCATION_FAVORITES_TAB -> com.goodwy.commons.R.drawable.ic_star_vector
            LOCATION_CONTACTS_TAB -> com.goodwy.commons.R.drawable.ic_person_rounded
            else -> com.goodwy.commons.R.drawable.ic_person_rounded
        }
    }

    override fun refreshContacts(refreshTabsMask: Int) {
        if (isDestroyed || isFinishing) {
            return
        }

        if (binding.viewPager.adapter == null) {
            binding.viewPager.adapter = ViewPagerAdapter(this, contactsFavoritesList, getTabsMask())
        }

        ContactsHelper(this).getContacts {
            if (isDestroyed || isFinishing) {
                return@getContacts
            }

            val contacts = it.filter {
                if (specialMimeType != null) {
                    val hasRequiredValues = when (specialMimeType) {
                        Email.CONTENT_ITEM_TYPE -> it.emails.isNotEmpty()
                        Phone.CONTENT_ITEM_TYPE -> it.phoneNumbers.isNotEmpty()
                        else -> true
                    }
                    !it.isPrivate() && hasRequiredValues
                } else {
                    true
                }
            } as ArrayList<Contact>

            val placeholderText = when (specialMimeType) {
                Email.CONTENT_ITEM_TYPE -> getString(R.string.no_contacts_with_emails)
                Phone.CONTENT_ITEM_TYPE -> getString(R.string.no_contacts_with_phone_numbers)
                else -> null
            }

            if (refreshTabsMask and TAB_CONTACTS != 0) {
                findViewById<MyViewPagerFragment<*>>(R.id.contacts_fragment)?.apply {
                    skipHashComparing = true
                    refreshContacts(contacts, placeholderText)
                }
            }

            if (refreshTabsMask and TAB_FAVORITES != 0) {
                findViewById<MyViewPagerFragment<*>>(R.id.favorites_fragment)?.apply {
                    skipHashComparing = true
                    refreshContacts(contacts, placeholderText)
                }
            }
        }
    }

    override fun contactClicked(contact: Contact) {
        hideKeyboard()
        if (isSelectContactIntent) {
            Intent().apply {
                data = getResultUri(contact)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                setResult(RESULT_OK, this)
            }
            finish()
        } else {
            val phoneNumber = getPhoneNumberFromIntent(intent) ?: ""
            val email = getEmailFromIntent(intent) ?: ""
            Intent(applicationContext, EditContactActivity::class.java).apply {
                data = getContactPublicUri(contact)
                action = ADD_NEW_CONTACT_NUMBER

                if (phoneNumber.isNotEmpty()) {
                    putExtra(KEY_PHONE, phoneNumber)
                }

                if (email.isNotEmpty()) {
                    putExtra(KEY_EMAIL, email)
                }

                putExtra(IS_PRIVATE, contact.isPrivate())
                startActivityForResult(this, START_EDIT_ACTIVITY)
            }
        }
    }

    private fun getResultUri(contact: Contact): Uri {
        return when {
            specialMimeType != null -> {
                val contactId = ContactsHelper(this).getContactMimeTypeId(contact.id.toString(), specialMimeType!!)
                Uri.withAppendedPath(ContactsContract.Data.CONTENT_URI, contactId)
            }

            else -> getContactPublicUri(contact)
        }
    }

    private fun createNewContact() {
        val name = intent.getStringExtra(KEY_NAME) ?: ""
        val phoneNumber = getPhoneNumberFromIntent(intent) ?: ""
        val email = getEmailFromIntent(intent) ?: ""

        Intent().apply {
            action = Intent.ACTION_INSERT
            data = ContactsContract.Contacts.CONTENT_URI

            if (phoneNumber.isNotEmpty()) {
                putExtra(KEY_PHONE, phoneNumber)
            }

            if (name.isNotEmpty()) {
                putExtra(KEY_NAME, name)
            }

            if (email.isNotEmpty()) {
                putExtra(KEY_EMAIL, email)
            }

            try {
                startActivityForResult(this, START_INSERT_ACTIVITY)
            } catch (e: ActivityNotFoundException) {
                toast(com.goodwy.commons.R.string.no_app_found)
            } catch (e: Exception) {
                showErrorToast(e)
            }
        }
    }

    private fun showSortingDialog() {
        val blurTarget = findViewById<BlurTarget>(com.goodwy.commons.R.id.mainBlurTarget)
            ?: throw IllegalStateException("mainBlurTarget not found")
        ChangeSortingDialog(this, blurTarget = blurTarget) {
            refreshContacts(getTabsMask())
        }
    }

    fun showFilterDialog() {
        val blurTarget = findViewById<BlurTarget>(com.goodwy.commons.R.id.mainBlurTarget)
            ?: throw IllegalStateException("mainBlurTarget not found")
        FilterContactSourcesDialog(this, blurTarget) {
            findViewById<MyViewPagerFragment<*>>(R.id.contacts_fragment)?.forceListRedraw = true
            refreshContacts(getTabsMask())
        }
    }

    private fun getTabsMask(): Int {
        var mask = TAB_CONTACTS
        if (config.showTabs and TAB_FAVORITES != 0) {
            mask += TAB_FAVORITES
        }
        return mask
    }
}
