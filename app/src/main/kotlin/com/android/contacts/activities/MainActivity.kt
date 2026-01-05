package com.android.contacts.activities

import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.Icon
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuItemCompat
import androidx.core.view.ScrollingView
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.ViewPager
import com.behaviorule.arturdumchev.library.pixels
import com.goodwy.commons.databases.ContactsDatabase
import com.goodwy.commons.dialogs.NewAppDialog
import com.goodwy.commons.dialogs.RadioGroupDialog
import com.goodwy.commons.extensions.*
import eightbitlab.com.blurview.BlurTarget
import com.goodwy.commons.helpers.*
import com.goodwy.commons.models.RadioItem
import com.goodwy.commons.models.Release
import com.goodwy.commons.models.contacts.Contact
import com.goodwy.commons.views.MyLiquidNavigationView
import com.goodwy.commons.views.MySearchMenu
import com.goodwy.commons.views.TwoFingerSlideGestureDetector
import com.android.contacts.BuildConfig
import com.android.contacts.R
import com.android.contacts.adapters.ViewPagerAdapter
import com.android.contacts.adapters.ContactsAdapter
import com.android.contacts.databinding.ActivityMainBinding
import com.android.contacts.dialogs.ChangeSortingDialog
import com.android.contacts.dialogs.FilterContactSourcesDialog
import com.android.contacts.extensions.config
import com.android.contacts.extensions.getSelectedContactsResult
import com.android.contacts.extensions.handleGenericContactClick
import com.android.contacts.extensions.launchAbout
import com.android.contacts.helpers.REQUEST_CODE_SELECT_CONTACTS
import com.android.contacts.extensions.tryImportContactsFromFile
import com.android.contacts.fragments.ContactsFragment
import com.android.contacts.fragments.FavoritesFragment
import com.android.contacts.fragments.GroupsFragment
import com.android.contacts.fragments.MyViewPagerFragment
import com.android.contacts.helpers.ALL_TABS_MASK
import com.android.contacts.helpers.LOCATION_CONTACTS_TAB
import com.android.contacts.helpers.LOCATION_FAVORITES_TAB
import com.android.contacts.helpers.tabsList
import com.android.contacts.interfaces.RefreshContactsListener
import com.goodwy.commons.extensions.onTabSelectionChanged
import com.goodwy.commons.extensions.setBackgroundColor
import com.goodwy.commons.extensions.setText
import me.grantland.widget.AutofitHelper
import java.util.*
import kotlin.and
import kotlin.text.toFloat


class MainActivity : SimpleActivity(), RefreshContactsListener {
    private lateinit var mSearchView: SearchView
    private var isSearchOpen = false
    private var mSearchMenuItem: MenuItem? = null
    private var searchQuery = ""
    private var werePermissionsHandled = false
    private var isFirstResume = true
    private var isGettingContacts = false

    private var storedShowContactThumbnails = false
    private var storedShowPhoneNumbers = false
    private var storedStartNameWithSurname = false
    private var storedFontSize = 0
    private var storedShowTabs = 0
    private var storedBackgroundColor = 0
    private var currentOldScrollY = 0
    private val binding by viewBinding(ActivityMainBinding::inflate)
    
    // Cached fragment references to avoid repeated findViewById calls
    private var cachedContactsFragment: ContactsFragment? = null
    private var cachedFavoritesFragment: FavoritesFragment? = null
    private var cachedGroupsFragment: GroupsFragment? = null
    
    // Cached Handler to avoid creating new instances
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // Two-finger swipe gesture detector for secure box
    private lateinit var twoFingerGestureDetector: TwoFingerSlideGestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        appLaunched(BuildConfig.APPLICATION_ID)
        setupOptionsMenu()
        refreshMenuItems()
        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.mainTabsHolder))

        storeStateVariables()
        checkContactPermissions()
        setupTabs()
        checkWhatsNewDialog()
        binding.mainMenu.apply {
            updateTitle(getAppLauncherName())
            searchBeVisibleIf(false) //hide top search bar
        }
        
        // Setup two-finger swipe gesture for secure box
        setupTwoFingerSwipeGesture()
    }
    
    private fun setupTwoFingerSwipeGesture() {
        twoFingerGestureDetector = TwoFingerSlideGestureDetector(this,
            object : TwoFingerSlideGestureDetector.OnTwoFingerSlideGestureListener {
                override fun onTwoFingerSlide(
                    firstFingerX: Float,
                    firstFingerY: Float,
                    secondFingerX: Float,
                    secondFingerY: Float,
                    avgDeltaX: Float,
                    avgDeltaY: Float,
                    avgDistance: Float
                ) {
                    // Open secure box with cipher number 1
                    openSecureBox()
                }
            }
        )
        
        // Attach gesture detector to the root view
        binding.root.setOnTouchListener { _, event ->
            twoFingerGestureDetector.onTouchEvent(event) || false
        }
    }
    
    private fun openSecureBox() {
        Intent(this, SecureBoxActivity::class.java).apply {
            putExtra(SecureBoxActivity.EXTRA_CIPHER_NUMBER, 1)
            startActivity(this)
        }
    }

    private fun checkContactPermissions() {
        handlePermission(PERMISSION_READ_CONTACTS) {
            werePermissionsHandled = true
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
        refreshMenuItems()
        // Cache color calculations to avoid repeated calls
        val properTextColor = getProperTextColor()
        val properPrimaryColor = getProperPrimaryColor()
        val properBackgroundColor = getProperBackgroundColor()
        val properAccentColor = getProperAccentColor()
        val iconTintColor = properPrimaryColor.getContrastColor()
        if (storedShowTabs != config.showTabs ||storedShowPhoneNumbers != config.showPhoneNumbers) {
            System.exit(0)
            return
        }

        @SuppressLint("UnsafeIntentLaunch")
        if (config.needRestart || storedBackgroundColor != properBackgroundColor) {
            config.lastUsedViewPagerPage = 0
            finish()
            startActivity(intent)
            return
        }

        val configShowContactThumbnails = config.showContactThumbnails
        if (storedShowContactThumbnails != configShowContactThumbnails) {
            getAllFragments().forEach {
                it?.showContactThumbnailsChanged(configShowContactThumbnails)
            }
        }

        val dialpadIcon = resources.getColoredDrawableWithColor(com.goodwy.commons.R.drawable.ic_dialpad_vector, properPrimaryColor.getContrastColor())
        binding.mainDialpadButton.apply {
            setImageDrawable(dialpadIcon)
            beVisibleIf(config.showDialpadButton)
        }
        val addIcon = resources.getColoredDrawableWithColor(com.goodwy.commons.R.drawable.ic_plus_vector, properPrimaryColor.getContrastColor())
        binding.mainAddButton.setImageDrawable(addIcon)

        updateTextColors(binding.mainCoordinator)
        binding.mainMenu.updateColors(
            background = getStartRequiredStatusBarColor(),
            scrollOffset = scrollingView?.computeVerticalScrollOffset() ?: 0
        )

        val useSurfaceColor = isDynamicTheme() && !isSystemInDarkMode()
        val surfaceColor = if (useSurfaceColor) getSurfaceColor() else 0
        val backgroundColor = if (useSurfaceColor) surfaceColor else properBackgroundColor
        if (useSurfaceColor) binding.mainHolder.setBackgroundColor(surfaceColor)
        
        // Batch fragment color updates
        val allFragments = getAllFragments()
        allFragments.forEach { fragment ->
            fragment?.setupColors(properTextColor, properPrimaryColor, properAccentColor)
            if (storedFontSize != config.fontSize) {
                fragment?.fontSizeChanged()
            }
            fragment?.setBackgroundColor(backgroundColor)
        }

        val configStartNameWithSurname = config.startNameWithSurname
        if (storedStartNameWithSurname != configStartNameWithSurname) {
            getContactsFragment()?.startNameWithSurnameChanged(configStartNameWithSurname)
            getFavoritesFragment()?.startNameWithSurnameChanged(configStartNameWithSurname)
        }

        val configFontSize = config.fontSize
        if (storedFontSize != configFontSize) {
            storedFontSize = configFontSize
        }

        if (werePermissionsHandled && !isFirstResume) {
            if (binding.viewPager.adapter == null) {
                initFragments()
            } else {
                if (!binding.mainMenu.isSearchOpen) refreshContacts(ALL_TABS_MASK)
            }
        }

        isFirstResume = false
        checkShortcuts()
        invalidateOptionsMenu()

        //Screen slide animation
        val animation = when (config.screenSlideAnimation) {
            1 -> ZoomOutPageTransformer()
            2 -> DepthPageTransformer()
            else -> null
        }
        binding.viewPager.setPageTransformer(true, animation)
        binding.viewPager.setPagingEnabled(!config.useSwipeToAction)
    }

    override fun onPause() {
        super.onPause()
        storeStateVariables()
        config.lastUsedViewPagerPage = binding.viewPager.currentItem
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacksAndMessages(null)
        if (!isChangingConfigurations) {
            ContactsDatabase.destroyInstance()
        }
        config.needRestart = false
        clearFragmentCache()
    }

    @SuppressLint("GestureBackNavigation")
    override fun onBackPressed() {
        if (binding.mainMenu.isSearchOpen) {
            binding.mainMenu.closeSearch()
        } else if (isSearchOpen && mSearchMenuItem != null) {
            mSearchMenuItem!!.collapseActionView()
        } else {
            super.onBackPressed()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (requestCode == REQUEST_CODE_SPEECH_INPUT && resultCode == RESULT_OK) {
            if (resultData != null) {
                val res: ArrayList<String> =
                    resultData.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS) as ArrayList<String>

                val speechToText =  Objects.requireNonNull(res)[0]
                if (speechToText.isNotEmpty()) {
                    binding.mainMenu.setText(speechToText)
                }
            }
        } else if (requestCode == REQUEST_CODE_SELECT_CONTACTS && resultCode == RESULT_OK && resultData != null) {
            val (addedContacts, removedContacts) = resultData.getSelectedContactsResult(this)
            ContactsHelper(this).apply {
                addFavorites(addedContacts)
                removeFavorites(removedContacts)
            }
            refreshContacts(TAB_FAVORITES)
        } else {
            // Handle export contacts result
            ContactsAdapter.handleExportResult(this, requestCode, resultCode, resultData)
        }
    }

    private fun refreshMenuItems() {
        val currentFragment = getCurrentFragment()
        val groupsFragment = getGroupsFragment()
        val favoritesFragment = getFavoritesFragment()
        binding.mainMenu.requireToolbar().menu.apply {
            findItem(R.id.search).isVisible = /*!config.bottomNavigationBar*/ true
            findItem(R.id.sort).isVisible = currentFragment != groupsFragment
            findItem(R.id.filter).isVisible = currentFragment != groupsFragment
            findItem(R.id.select).isVisible = currentFragment != null
            //findItem(R.id.dialpad).isVisible = !config.showDialpadButton
            findItem(R.id.change_view_type).isVisible = currentFragment == favoritesFragment
            findItem(R.id.column_count).isVisible = currentFragment == favoritesFragment && config.viewType == VIEW_TYPE_GRID
        }
    }

    private fun setupOptionsMenu() {
        binding.mainMenu.apply {
            requireToolbar().inflateMenu(R.menu.menu)
            setupSearch(requireToolbar().menu)
            requireToolbar().setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.sort -> showSortingDialog(showCustomSorting = getCurrentFragment() is FavoritesFragment)
                    R.id.filter -> showFilterDialog()
                    R.id.select -> startActionMode()
                    R.id.dialpad -> launchDialpad()
                    R.id.change_view_type -> changeViewType()
                    R.id.column_count -> changeColumnCount()
                    R.id.scan_qr_code -> launchScanQrCode()
                    R.id.settings -> launchSettings()
//                    R.id.about -> launchAbout()
                    else -> return@setOnMenuItemClickListener false
                }
                return@setOnMenuItemClickListener true
            }
        }
    }

    private fun changeViewType() {
        config.viewType = if (config.viewType == VIEW_TYPE_LIST) VIEW_TYPE_GRID else VIEW_TYPE_LIST
        refreshMenuItems()
        getFavoritesFragment()?.updateFavouritesAdapter()
    }

    private fun changeColumnCount() {
        val items = (1..CONTACTS_GRID_MAX_COLUMNS_COUNT).map { i ->
            RadioItem(i, resources.getQuantityString(com.goodwy.commons.R.plurals.column_counts, i, i))
        }

        val currentColumnCount = config.contactsGridColumnCount
        val blurTarget = findViewById<BlurTarget>(com.goodwy.commons.R.id.mainBlurTarget)
            ?: throw IllegalStateException("mainBlurTarget not found")
        RadioGroupDialog(this, items as ArrayList<RadioItem>, currentColumnCount, blurTarget = blurTarget) {
            val newColumnCount = it as Int
            if (currentColumnCount != newColumnCount) {
                config.contactsGridColumnCount = newColumnCount
                getFavoritesFragment()?.columnCountChanged()
            }
        }
    }

    private fun storeStateVariables() {
        config.apply {
            storedShowContactThumbnails = showContactThumbnails
            storedShowPhoneNumbers = showPhoneNumbers
            storedStartNameWithSurname = startNameWithSurname
            storedShowTabs = showTabs
            storedFontSize = fontSize
            needRestart = false
        }
        storedBackgroundColor = getProperBackgroundColor()
    }

    private fun setupSearch(menu: Menu) {
        updateMenuItemColors(menu)
        val searchManager = getSystemService(SEARCH_SERVICE) as SearchManager
        mSearchMenuItem = menu.findItem(R.id.search)
        mSearchView = (mSearchMenuItem!!.actionView as SearchView).apply {
            val textColor = getProperTextColor()
            // Cache small padding calculation
            val smallPadding = resources.getDimensionPixelSize(com.goodwy.commons.R.dimen.small_margin)
            findViewById<TextView>(androidx.appcompat.R.id.search_src_text).apply {
                setTextColor(textColor)
                setHintTextColor(textColor)
                // Reduce left padding to a small value
                setPadding(smallPadding, paddingTop, paddingRight, paddingBottom)
            }
            findViewById<ImageView>(androidx.appcompat.R.id.search_close_btn).apply {
                setImageResource(com.goodwy.commons.R.drawable.ic_clear_round)
                setColorFilter(textColor)
            }
            findViewById<View>(androidx.appcompat.R.id.search_plate)?.apply { // search underline
                background.setColorFilter(Color.TRANSPARENT, PorterDuff.Mode.MULTIPLY)
                // Reduce left padding on the search plate to a small value
                setPadding(smallPadding, paddingTop, paddingRight, paddingBottom)
            }
            setIconifiedByDefault(false)
            findViewById<ImageView>(androidx.appcompat.R.id.search_mag_icon).apply {
                setColorFilter(textColor)
            }

            setSearchableInfo(searchManager.getSearchableInfo(componentName))
            isSubmitButtonEnabled = false
            queryHint = getString(R.string.search)
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String) = false

                override fun onQueryTextChange(newText: String): Boolean {
                    if (isSearchOpen) {
                        searchQuery = newText
                        getCurrentFragment()?.onSearchQueryChanged(newText)
                    }
                    return true
                }
            })
        }

        @Suppress("DEPRECATION")
        MenuItemCompat.setOnActionExpandListener(mSearchMenuItem, object : MenuItemCompat.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem?): Boolean {
                isSearchOpen = true
                // Keep dialpad button visible when search is opened

                // Animate search bar appearance with smooth translation (slide in from right)
                mSearchView?.let { searchView ->
                    searchView.post {
                        // Get the parent toolbar width for smooth slide-in
                        val toolbar = binding.mainMenu.requireToolbar()
                        val slideDistance = toolbar.width.toFloat()

                        // Start from right side
                        searchView.translationX = slideDistance
                        searchView.alpha = 0f

                        // Animate to center with smooth deceleration
                        searchView.animate()
                            .translationX(0f)
                            .alpha(1f)
                            .setDuration(350)
                            .setInterpolator(android.view.animation.DecelerateInterpolator(1.5f))
                            .start()
                    }
                }

                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem?): Boolean {
                if (isSearchOpen) {
                    getCurrentFragment()?.onSearchClosed()
                }

                isSearchOpen = false

                // Animate search bar disappearance with smooth translation (slide out to right)
                mSearchView?.let { searchView ->
                    val toolbar = binding.mainMenu.requireToolbar()
                    val slideDistance = toolbar.width.toFloat()

                    searchView.animate()
                        .translationX(slideDistance)
                        .alpha(0f)
                        .setDuration(300)
                        .setInterpolator(android.view.animation.AccelerateInterpolator(1.2f))
                        .withEndAction {
                            searchView.translationX = 0f
                            searchView.alpha = 1f
                            binding.mainDialpadButton.beGone() // Always hide the dialpad button
                        }
                        .start()
                } ?: run {
                    binding.mainDialpadButton.beGone() // Always hide the dialpad button
                }

                return true
            }
        })
    }

    @SuppressLint("NewApi")
    private fun checkShortcuts() {
        val iconColor = getProperPrimaryColor()
        if (isNougatMR1Plus() && config.lastHandledShortcutColor != iconColor) {
            val createNewContact = getCreateNewContactShortcut(iconColor)

            try {
                shortcutManager.dynamicShortcuts = listOf(createNewContact)
                config.lastHandledShortcutColor = iconColor
            } catch (ignored: Exception) {
            }
        }
    }

    @SuppressLint("NewApi")
    private fun getCreateNewContactShortcut(iconColor: Int): ShortcutInfo {
        val newEvent = getString(com.goodwy.commons.R.string.create_new_contact)
        val drawable = AppCompatResources.getDrawable(this, R.drawable.shortcut_plus)
        (drawable as LayerDrawable).findDrawableByLayerId(R.id.shortcut_plus_background).applyColorFilter(iconColor)
        val bmp = drawable.convertToBitmap()

        val intent = Intent(this, EditContactActivity::class.java)
        intent.action = Intent.ACTION_VIEW
        return ShortcutInfo.Builder(this, "create_new_contact")
            .setShortLabel(newEvent)
            .setLongLabel(newEvent)
            .setIcon(Icon.createWithBitmap(bmp))
            .setIntent(intent)
            .build()
    }

    private fun getCurrentFragment(): MyViewPagerFragment<*>? {
        val currentItem = binding.viewPager.currentItem
        val showTabs = config.showTabs
        var fragmentIndex = 0

        if (showTabs and TAB_FAVORITES != 0) {
            if (fragmentIndex == currentItem) return getFavoritesFragment()
            fragmentIndex++
        }

        if (showTabs and TAB_CONTACTS != 0) {
            if (fragmentIndex == currentItem) return getContactsFragment()
            fragmentIndex++
        }

        if (showTabs and TAB_GROUPS != 0) {
            if (fragmentIndex == currentItem) return getGroupsFragment()
        }

        return null
    }

    private fun setupTabColors() {
        if (isDynamicTheme() && !isSystemInDarkMode()) binding.mainHolder.setBackgroundColor(getSurfaceColor())

        // bottom tab bar
        if (config.bottomNavigationBar) {
            // MyLiquidNavigationView handles colors internally through Compose
            // No need to manually update tab item colors
            
            val bottomBarColor =
                if (isDynamicTheme() && !isSystemInDarkMode()) getColoredMaterialStatusBarColor()
                else getSurfaceColor()
            binding.mainTabsHolder.setBackgroundColor(bottomBarColor)
            if (binding.mainTabsHolder.tabCount != 1) updateNavigationBarColor(bottomBarColor)
            else {
                // TODO TRANSPARENT Navigation Bar
                setWindowTransparency(true) { _, bottomNavigationBarSize, leftNavigationBarSize, rightNavigationBarSize ->
                    binding.mainCoordinator.setPadding(leftNavigationBarSize, 0, rightNavigationBarSize, 0)
                    binding.mainAddButton.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                        setMargins(0, 0, 0, bottomNavigationBarSize + pixels(com.goodwy.commons.R.dimen.activity_margin).toInt())
                    }
                }
            }

            val properTextColor = getProperTextColor()
            val properPrimaryColor = getProperPrimaryColor()
            getAllFragments().forEach {
                it?.setupColors(properTextColor, properPrimaryColor, getProperAccentColor())
                binding.mainTopTabsHolder.setTabTextColors(properTextColor, properPrimaryColor)
            }
        } else {
            // top tab bar
            val properTextColor = getProperTextColor()
            val properPrimaryColor = getProperPrimaryColor()

            if (binding.viewPager.adapter != null) {

                if (config.needRestart) {
                    if (config.useIconTabs) {
                        binding.mainTopTabsHolder.getTabAt(0)?.text = null
                        binding.mainTopTabsHolder.getTabAt(1)?.text = null
                        binding.mainTopTabsHolder.getTabAt(2)?.text = null
                    } else {
                        binding.mainTopTabsHolder.getTabAt(0)?.icon = null
                        binding.mainTopTabsHolder.getTabAt(1)?.icon = null
                        binding.mainTopTabsHolder.getTabAt(2)?.icon = null
                    }
                }

                getInactiveTabIndexes(binding.viewPager.currentItem).forEach {
                    binding.mainTopTabsHolder.getTabAt(it)?.icon?.applyColorFilter(properTextColor)
                    binding.mainTopTabsHolder.getTabAt(it)?.icon?.alpha = 220 // max 255
                    binding.mainTopTabsHolder.setTabTextColors(properTextColor, properPrimaryColor)
                }

                binding.mainTopTabsHolder.getTabAt(binding.viewPager.currentItem)?.icon?.applyColorFilter(properPrimaryColor)
                binding.mainTopTabsHolder.getTabAt(binding.viewPager.currentItem)?.icon?.alpha = 220 // max 255
                getAllFragments().forEach {
                    it?.setupColors(properTextColor, properPrimaryColor, getProperAccentColor())
                    binding.mainTopTabsHolder.setTabTextColors(properTextColor, properPrimaryColor)
                }
            }

            val lastUsedPage = getDefaultTab()
            binding.mainTopTabsHolder.apply {
                setSelectedTabIndicatorColor(getProperBackgroundColor())
                getTabAt(lastUsedPage)?.select()
                getTabAt(lastUsedPage)?.icon?.applyColorFilter(properPrimaryColor)
                getTabAt(lastUsedPage)?.icon?.alpha = 220 // max 255

                getInactiveTabIndexes(lastUsedPage).forEach {
                    getTabAt(it)?.icon?.applyColorFilter(properTextColor)
                    getTabAt(it)?.icon?.alpha = 220 // max 255
                }
            }

            binding.mainTopTabsHolder.onTabSelectionChanged(
                tabUnselectedAction = {
                    it.icon?.applyColorFilter(properTextColor)
                    it.icon?.alpha = 220 // max 255
                },
                tabSelectedAction = {
                    if (config.closeSearch) {
                        closeSearch()
                    } else {
                        //On tab switch, the search string is not deleted
                        //It should not start on the first startup
                        if (isSearchOpen) getCurrentFragment()?.onSearchQueryChanged(searchQuery)
                    }

                    binding.viewPager.currentItem = it.position
                    it.icon?.applyColorFilter(properPrimaryColor)
                    it.icon?.alpha = 220 // max 255

                    if (config.openSearch) {
                        if (getCurrentFragment() is ContactsFragment) {
                            mSearchMenuItem!!.expandActionView()
                        }
                    }
                }
            )
        }
    }

    private fun getInactiveTabIndexes(activeIndex: Int) = (0 until binding.mainTabsHolder.tabCount).filter { it != activeIndex }

    private fun initFragments() {
        binding.viewPager.offscreenPageLimit = 2 //tabsList.size - 1
        binding.viewPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrollStateChanged(state: Int) {}

            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                if (config.bottomNavigationBar && config.changeColourTopBar) scrollChange()
            }

            override fun onPageSelected(position: Int) {
                if (config.bottomNavigationBar) {
                    binding.mainTabsHolder.getTabAt(position)?.select()
                } else binding.mainTopTabsHolder.getTabAt(position)?.select()

                getAllFragments().forEach {
                    it?.finishActMode()
                }
                refreshMenuItems()
            }
        })

        binding.viewPager.onGlobalLayout {
            refreshContacts(ALL_TABS_MASK)
            refreshMenuItems()
            if (config.bottomNavigationBar && config.changeColourTopBar) scrollChange()
        }

        handleExternalIntent()

        binding.mainDialpadButton.setOnClickListener {
            launchDialpad()
        }

        binding.mainAddButton.setOnClickListener {
            getCurrentFragment()?.let { fragment ->
                when (fragment) {
                    is FavoritesFragment -> fragment.fabClicked()
                    is ContactsFragment -> fragment.fabClicked()
                    is GroupsFragment -> fragment.fabClicked()
                }
            }
        }

        binding.mainTopTabsHolder.removeAllTabs()
        var skippedTabs = 0
        tabsList.forEachIndexed { index, value ->
            if (config.showTabs and value == 0) {
                skippedTabs++
            } else {
                val tab = if (config.useIconTabs) binding.mainTopTabsHolder.newTab().setIcon(getTabIcon(index)) else binding.mainTopTabsHolder.newTab().setText(getTabLabel(index))
                tab.contentDescription = getTabLabel(index)
                binding.mainTopTabsHolder.addTab(tab, index - skippedTabs, getDefaultTab() == index - skippedTabs)
                binding.mainTopTabsHolder.setTabTextColors(getProperTextColor(),
                    getProperPrimaryColor())
            }
        }

        // selecting the proper tab sometimes glitches, add an extra selector to make sure we have it right
        binding.mainTopTabsHolder.onGlobalLayout {
            mainHandler.postDelayed({
                binding.mainTopTabsHolder.getTabAt(getDefaultTab())?.select()
                invalidateOptionsMenu()
                refreshMenuItems()
            }, 100L)
        }

        binding.mainTopTabsContainer.beGoneIf(binding.mainTopTabsHolder.tabCount == 1 || config.bottomNavigationBar)
    }

    private fun handleExternalIntent() {
        val uri = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM)
            else -> null
        }

        if (uri != null) {
            tryImportContactsFromFile(uri) { success ->
                if (success) {
                    runOnUiThread {
                        refreshContacts(ALL_TABS_MASK)
                    }
                }
            }
            intent.action = null
        }
    }

    private fun scrollChange() {
        val myRecyclerView = getCurrentFragment()?.myRecyclerView()
        scrollingView = myRecyclerView

        val scrollingViewOffset = scrollingView?.computeVerticalScrollOffset() ?: 0
        currentOldScrollY = scrollingViewOffset

        val useSurfaceColor = isDynamicTheme() && !isSystemInDarkMode()
        val backgroundColor = if (useSurfaceColor) getSurfaceColor() else getProperBackgroundColor()
        val statusBarColor = if (config.changeColourTopBar) getRequiredStatusBarColor(useSurfaceColor) else backgroundColor

        binding.mainMenu.updateColors(statusBarColor, scrollingViewOffset)
        setupSearchMenuScrollListenerNew(myRecyclerView, binding.mainMenu, useSurfaceColor)
    }

    private fun setupSearchMenuScrollListenerNew(scrollingView: ScrollingView?, searchMenu: MySearchMenu, surfaceColor: Boolean) {
        this.scrollingView = scrollingView
        this.mySearchMenu = searchMenu
        if (scrollingView is RecyclerView) {
            scrollingView.setOnScrollChangeListener { _, _, _, _, _ ->
                val newScrollY = scrollingView.computeVerticalScrollOffset()
                if (newScrollY == 0 || currentOldScrollY == 0) scrollingChanged(newScrollY, surfaceColor)
                currentScrollY = newScrollY
                currentOldScrollY = currentScrollY
            }
        }
    }

    private fun scrollingChanged(newScrollY: Int, surfaceColor: Boolean) {
        val colorFrom = if (surfaceColor) {
            getSurfaceColor()
        } else {
            getProperBackgroundColor()
        }
        val colorTo = if (newScrollY > 0 && currentOldScrollY == 0) {
            getColoredMaterialStatusBarColor()
        } else if (newScrollY == 0 && currentOldScrollY > 0) {
            getRequiredStatusBarColor(surfaceColor)
        } else {
            return
        }
        animateMySearchMenuColors(colorFrom, colorTo)
    }

    override fun getStartRequiredStatusBarColor(): Int {
        val useSurfaceColor = isDynamicTheme() && !isSystemInDarkMode()
        val scrollingViewOffset = scrollingView?.computeVerticalScrollOffset() ?: 0
        return if (scrollingViewOffset == 0) {
            if (useSurfaceColor) getSurfaceColor() else getProperBackgroundColor()
        } else {
            getColoredMaterialStatusBarColor()
        }
    }

    // Tab configuration data class for better organization
    private data class TabConfig(
        val iconRes: Int,
        val labelRes: Int
    )
    
    private val tabConfigs = arrayOf(
        TabConfig(com.goodwy.commons.R.drawable.ic_star_vector, com.goodwy.commons.R.string.favorites_tab),
        TabConfig(com.goodwy.commons.R.drawable.ic_person_rounded, com.goodwy.commons.R.string.contacts_tab),
        TabConfig(R.drawable.ic_people_rounded, com.goodwy.commons.R.string.groups_tab)
    )

    private fun getTabLabelFromConfig(position: Int): String {
        val config = tabConfigs.getOrNull(position) ?: tabConfigs.last()
        return resources.getString(config.labelRes)
    }

    private fun getTabIconRes(position: Int): Int {
        val config = tabConfigs.getOrNull(position) ?: tabConfigs.last()
        return config.iconRes
    }

    private fun setupTabs() {
        binding.viewPager.adapter = null
        binding.mainTabsHolder.removeAllTabs()
        
        tabsList.forEachIndexed { index, value ->
            if (config.showTabs and value != 0) {
                binding.mainTabsHolder.newTab().apply {
                    setIcon(getTabIconRes(index))
                    setText(getTabLabelFromConfig(index))
                }.also { binding.mainTabsHolder.addTab(it) }
            }
        }

        binding.mainTabsHolder.onTabSelectionChanged(
            tabUnselectedAction = {
            },
            tabSelectedAction = {
                if (config.closeSearch) {
                    binding.mainMenu.closeSearch()
                } else {
                    //On tab switch, the search string is not deleted
                    //It should not start on the first startup
                    if (binding.mainMenu.isSearchOpen) getCurrentFragment()?.onSearchQueryChanged(binding.mainMenu.getCurrentQuery())
                }

                binding.viewPager.currentItem = it.position

                if (config.openSearch) {
                    if (getCurrentFragment() is ContactsFragment) {
                        binding.mainMenu.requestFocusAndShowKeyboard()
                    }
                }
            }
        )

        binding.mainTabsHolder.beGoneIf(binding.mainTabsHolder.tabCount == 1)
        storedShowTabs = config.showTabs
        storedStartNameWithSurname = config.startNameWithSurname
        storedShowPhoneNumbers = config.showPhoneNumbers
    }

    private fun showSortingDialog(showCustomSorting: Boolean) {
        val blurTarget = findViewById<BlurTarget>(com.goodwy.commons.R.id.mainBlurTarget)
            ?: throw IllegalStateException("mainBlurTarget not found")
        ChangeSortingDialog(this, showCustomSorting, blurTarget) {
            refreshContacts(TAB_CONTACTS or TAB_FAVORITES)
        }
    }

    fun showFilterDialog() {
        val blurTarget = findViewById<BlurTarget>(com.goodwy.commons.R.id.mainBlurTarget)
            ?: throw IllegalStateException("mainBlurTarget not found")
        FilterContactSourcesDialog(this, blurTarget) {
            getContactsFragment()?.forceListRedraw = true
            refreshContacts(TAB_CONTACTS or TAB_FAVORITES)
        }
    }

    private fun launchDialpad() {
        hideKeyboard()
        val simpleDialer = "com.android.dialer"
        val simpleDialerDebug = "com.android.dialer"
        if ((0..config.appRecommendationDialogCount).random() == 2 && (!isPackageInstalled(simpleDialer) && !isPackageInstalled(simpleDialerDebug))) {
            runOnUiThread {
                val blurTarget = findViewById<BlurTarget>(com.goodwy.commons.R.id.mainBlurTarget)
                    ?: throw IllegalStateException("mainBlurTarget not found")
                NewAppDialog(this, simpleDialer, getString(com.goodwy.strings.R.string.recommendation_dialog_dialer_g), getString(com.goodwy.commons.R.string.right_dialer),
                    AppCompatResources.getDrawable(this, R.drawable.ic_launcher_dialer), blurTarget) {}
            }
        } else {
            Intent(Intent.ACTION_DIAL).apply {
                try {
                    startActivity(this)
                } catch (e: ActivityNotFoundException) {
                    toast(com.goodwy.commons.R.string.no_app_found)
                } catch (e: Exception) {
                    showErrorToast(e)
                }
            }
        }
    }

    private fun startActionMode() {
        val currentFragment = getCurrentFragment()
        val adapter = currentFragment?.myRecyclerView()?.adapter as? com.goodwy.commons.adapters.MyRecyclerViewAdapter
        adapter?.startActMode()
    }

    private fun launchScanQrCode() {
        Intent(this, ScanQrCodeActivity::class.java).apply {
            startActivity(this)
        }
    }

    private fun launchSettings() {
        binding.mainMenu.closeSearch()
        closeSearch()
        hideKeyboard()
        startActivity(Intent(applicationContext, SettingsActivity::class.java))
    }

    override fun refreshContacts(refreshTabsMask: Int) {
        if (isDestroyed || isFinishing || isGettingContacts) {
            return
        }

        isGettingContacts = true

        if (binding.viewPager.adapter == null) {
            binding.viewPager.adapter = ViewPagerAdapter(this, tabsList, config.showTabs)
            mainHandler.post {
                val index = getDefaultTab()
                binding.viewPager.setCurrentItem(index, false)
                // onPageSelected callback will automatically select the tab in mainTabsHolder
            }
        }

        ContactsHelper(this).getContacts { contacts ->
            isGettingContacts = false
            if (isDestroyed || isFinishing) {
                return@getContacts
            }

            // Batch fragment updates
            if (refreshTabsMask and TAB_FAVORITES != 0) {
                getFavoritesFragment()?.apply {
                    skipHashComparing = true
                    refreshContacts(contacts)
                }
            }

            if (refreshTabsMask and TAB_CONTACTS != 0) {
                getContactsFragment()?.apply {
                    skipHashComparing = true
                    refreshContacts(contacts)
                }
            }

            if (refreshTabsMask and TAB_GROUPS != 0) {
                getGroupsFragment()?.apply {
                    skipHashComparing = true
                    refreshContacts(contacts)
                }
            }

            if (isSearchOpen) {
                getCurrentFragment()?.onSearchQueryChanged(searchQuery)
            }
        }
    }

    override fun contactClicked(contact: Contact) {
        handleGenericContactClick(contact)
    }

    private fun getAllFragments(): List<MyViewPagerFragment<*>?> {
        val fragments = mutableListOf<MyViewPagerFragment<*>?>()
        val showTabs = config.showTabs
        
        if (showTabs and TAB_FAVORITES != 0) {
            fragments.add(getFavoritesFragment())
        }
        if (showTabs and TAB_CONTACTS != 0) {
            fragments.add(getContactsFragment())
        }
        if (showTabs and TAB_GROUPS != 0) {
            fragments.add(getGroupsFragment())
        }
        
        return fragments
    }
    
    private fun getContactsFragment(): ContactsFragment? {
        if (cachedContactsFragment == null) {
            cachedContactsFragment = findViewById(R.id.contacts_fragment)
        }
        return cachedContactsFragment
    }

    private fun getFavoritesFragment(): FavoritesFragment? {
        if (cachedFavoritesFragment == null) {
            cachedFavoritesFragment = findViewById(R.id.favorites_fragment)
        }
        return cachedFavoritesFragment
    }

    private fun getGroupsFragment(): GroupsFragment? {
        if (cachedGroupsFragment == null) {
            cachedGroupsFragment = findViewById(R.id.groups_fragment)
        }
        return cachedGroupsFragment
    }
    
    private fun clearFragmentCache() {
        cachedContactsFragment = null
        cachedFavoritesFragment = null
        cachedGroupsFragment = null
    }

    private fun getDefaultTab(): Int {
        val showTabsMask = config.showTabs
        return when (config.defaultTab) {
            TAB_LAST_USED -> config.lastUsedViewPagerPage
            TAB_FAVORITES -> 0
            TAB_CONTACTS -> if (showTabsMask and TAB_FAVORITES > 0) 1 else 0
            else -> {
                if (showTabsMask and TAB_GROUPS > 0) {
                    if (showTabsMask and TAB_FAVORITES > 0) {
                        if (showTabsMask and TAB_CONTACTS > 0) {
                            2
                        } else {
                            1
                        }
                    } else {
                        if (showTabsMask and TAB_CONTACTS > 0) {
                            1
                        } else {
                            0
                        }
                    }
                } else {
                    0
                }
            }
        }
    }

    private fun closeSearch() {
        if (isSearchOpen) {
            getAllFragments().forEach {
                it?.onSearchQueryChanged("")
            }
            mSearchMenuItem?.collapseActionView()
        }
    }

    private fun checkWhatsNewDialog() {
        val releases = listOf(
            Release(414, R.string.release_414),
            Release(500, R.string.release_500),
            Release(510, R.string.release_510),
            Release(520, R.string.release_520),
            Release(521, R.string.release_521),
            Release(522, R.string.release_522),
            Release(523, R.string.release_523),
            Release(524, R.string.release_524),
            Release(610, R.string.release_610),
            Release(611, R.string.release_611),
            Release(612, R.string.release_612),
            Release(620, R.string.release_620),
            Release(621, R.string.release_621),
            Release(700, R.string.release_700),
            Release(701, R.string.release_701)
        )
        checkWhatsNew(releases, BuildConfig.VERSION_CODE)
    }
}
