package com.android.contacts.activities

import android.annotation.SuppressLint
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
import java.util.concurrent.atomic.AtomicInteger
import android.speech.RecognizerIntent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.ScrollingView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.ViewPager
import com.android.common.helper.IconItem
import com.android.common.view.MTabBar
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
import com.qmdeve.liquidglass.view.LiquidGlassTabs
import me.grantland.widget.AutofitHelper
import java.util.*
import kotlin.and
import kotlin.text.toFloat


class MainActivity : SimpleActivity(), RefreshContactsListener {
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
        setupEdgeToEdge()

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

        val contrastColor = properBackgroundColor.getContrastColor()
        val iconColor = if (baseConfig.topAppBarColorIcon) properPrimaryColor else contrastColor
        
        val dialpadIcon = resources.getColoredDrawableWithColor(com.goodwy.commons.R.drawable.ic_dialpad_vector, iconColor)
        binding.mainDialpadButton.apply {
            setImageDrawable(dialpadIcon)
            beVisibleIf(config.showDialpadButton)
        }
        val addIcon = resources.getColoredDrawableWithColor(com.goodwy.commons.R.drawable.ic_plus_vector, iconColor)
        binding.mainAddButton.setImageDrawable(addIcon)
        val overflowIcon = resources.getColoredDrawableWithColor(com.goodwy.commons.R.drawable.ic_three_dots_vector, iconColor)
        binding.mainMenu.requireCustomToolbar().overflowIcon = overflowIcon

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
        val customToolbar = binding.mainMenu.requireCustomToolbar()
        if (customToolbar.isSearchExpanded) {
            customToolbar.collapseSearch()
            binding.mainMenu.isSearchOpen = false
            getCurrentFragment()?.onSearchClosed()
            searchQuery = ""
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
            val contactsHelper = ContactsHelper(this)
            val completedOperations = AtomicInteger(0)
            val totalOperations = (if (addedContacts.isNotEmpty()) 1 else 0) + (if (removedContacts.isNotEmpty()) 1 else 0)
            
            if (totalOperations == 0) {
                refreshContacts(TAB_FAVORITES)
            } else {
                val refreshCallback = {
                    val completed = completedOperations.incrementAndGet()
                    if (completed >= totalOperations) {
                        runOnUiThread {
                            refreshContacts(TAB_FAVORITES)
                        }
                    }
                }
                
                if (addedContacts.isNotEmpty()) {
                    contactsHelper.addFavorites(addedContacts, refreshCallback)
                }
                if (removedContacts.isNotEmpty()) {
                    contactsHelper.removeFavorites(removedContacts, refreshCallback)
                }
            }
        } else {
            // Handle export contacts result
            ContactsAdapter.handleExportResult(this, requestCode, resultCode, resultData)
        }
    }

    private fun refreshMenuItems() {
        val currentFragment = getCurrentFragment()
        val groupsFragment = getGroupsFragment()
        val favoritesFragment = getFavoritesFragment()
        binding.mainMenu.requireCustomToolbar().menu.apply {
            findItem(R.id.search).isVisible = /*!config.bottomNavigationBar*/ true
            findItem(R.id.sort).isVisible = currentFragment != groupsFragment
            findItem(R.id.filter).isVisible = currentFragment != groupsFragment
            findItem(R.id.select).isVisible = currentFragment != null
            //findItem(R.id.dialpad).isVisible = !config.showDialpadButton
            findItem(R.id.change_view_type).isVisible = currentFragment == favoritesFragment
            findItem(R.id.column_count).isVisible = currentFragment == favoritesFragment && config.viewType == VIEW_TYPE_GRID
        }
        // Update menu button visibility after changing menu item visibility
        binding.mainMenu.requireCustomToolbar().invalidateMenu()
    }

    private fun setupOptionsMenu() {
        binding.mainMenu.apply {
            val customToolbar = requireCustomToolbar()
            customToolbar.inflateMenu(R.menu.menu)
            updateMenuItemColors(customToolbar.menu)
            
            // Setup search text changed listener for CustomToolbar
            customToolbar.setOnSearchTextChangedListener { text ->
                searchQuery = text
                if (customToolbar.isSearchExpanded) {
                    getCurrentFragment()?.onSearchQueryChanged(text)
                }
            }
            
            // Setup search expand/collapse listeners
            customToolbar.setOnSearchExpandListener {
                isSearchOpen = true
                // Restore previous search query if it exists
                if (searchQuery.isNotEmpty()) {
                    customToolbar.setSearchText(searchQuery)
                    getCurrentFragment()?.onSearchQueryChanged(searchQuery)
                }
            }
            
            // Setup search back click listener
            customToolbar.setOnSearchBackClickListener {
                // CustomToolbar already calls collapseSearch() internally
                isSearchOpen = false
                getCurrentFragment()?.onSearchClosed()
                searchQuery = ""
            }
            
            customToolbar.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.sort -> showSortingDialog(showCustomSorting = getCurrentFragment() is FavoritesFragment)
                    R.id.filter -> showFilterDialog()
                    R.id.select -> startActionMode()
                    R.id.dialpad -> launchDialpad()
                    R.id.change_view_type -> changeViewType()
                    R.id.column_count -> changeColumnCount()
                    R.id.scan_qr_code -> launchScanQrCode()
                    R.id.settings -> launchSettings()
                    R.id.search -> toggleCustomSearchBar()
//                    R.id.about -> launchAbout()
                    else -> return@setOnMenuItemClickListener false
                }
                return@setOnMenuItemClickListener true
            }
            // Invalidate menu to ensure menu button visibility is updated
            customToolbar.invalidateMenu()
        }
    }

    private fun toggleCustomSearchBar() {
        val customToolbar = binding.mainMenu.requireCustomToolbar()
        if (customToolbar.isSearchExpanded) {
            customToolbar.collapseSearch()
            binding.mainMenu.isSearchOpen = false
            getCurrentFragment()?.onSearchClosed()
            searchQuery = ""
        } else {
            customToolbar.expandSearch()
            binding.mainMenu.isSearchOpen = true
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


    @SuppressLint("NewApi")
    private fun checkShortcuts() {
        val iconColor = getProperPrimaryColor()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N_MR1 && config.lastHandledShortcutColor != iconColor) {
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
            // MTabBar handles colors internally
            // No need to manually update tab item colors
            
            val bottomBarColor =
                if (isDynamicTheme() && !isSystemInDarkMode()) getColoredMaterialStatusBarColor()
                else getSurfaceColor()
            window.setSystemBarsAppearance(bottomBarColor)
            
            // Get tab count from the tab items list
            val tabCount = tabsList.count { config.showTabs and it != 0 }
            if (tabCount == 1) {
                // Handle transparent navigation bar with window insets
                ViewCompat.setOnApplyWindowInsetsListener(binding.mainCoordinator) { view, insets ->
                    val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                    view.setPadding(systemBars.left, 0, systemBars.right, 0)
                    binding.mainAddButton.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                        setMargins(0, 0, 0, systemBars.bottom + pixels(com.goodwy.commons.R.dimen.activity_margin).toInt())
                    }
                    insets
                }
            }

            val properTextColor = getProperTextColor()
            val properPrimaryColor = getProperPrimaryColor()
            getAllFragments().forEach {
                it?.setupColors(properTextColor, properPrimaryColor, getProperAccentColor())
            }
        }
    }

    private fun getInactiveTabIndexes(activeIndex: Int): List<Int> {
        val tabCount = tabsList.count { config.showTabs and it != 0 }
        return (0 until tabCount).filter { it != activeIndex }
    }

    private fun initFragments() {
        binding.viewPager.offscreenPageLimit = 2 //tabsList.size - 1
        binding.viewPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrollStateChanged(state: Int) {}

            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                if (config.bottomNavigationBar && config.changeColourTopBar) scrollChange()
            }

            override fun onPageSelected(position: Int) {
                if (config.bottomNavigationBar) {
                    binding.tabBar.setSelection(position)
                }

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

        var skippedTabs = 0
        tabsList.forEachIndexed { index, value ->
            if (config.showTabs and value == 0) {
                skippedTabs++
            }
        }

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
        
        // Create IconItem list for MTabBar
        val tabItems = ArrayList<IconItem>()
        tabsList.forEachIndexed { index, value ->
            if (config.showTabs and value != 0) {
                val iconItem = IconItem()
                iconItem.icon = getTabIconRes(index)
                iconItem.title = getTabLabelFromConfig(index)
                tabItems.add(iconItem)
            }
        }
        
        // Get BlurTarget for MTabBar
        val blurTarget = findViewById<BlurTarget>(com.goodwy.commons.R.id.mainBlurTarget)
            ?: throw IllegalStateException("mainBlurTarget not found")
        
        // Setup MTabBar
        binding.tabBar.setTabs(this, tabItems, blurTarget)
        
        // Setup tab selection listener
        binding.tabBar.setOnTabSelectedListener(object : LiquidGlassTabs.OnTabSelectedListener {
            override fun onTabSelected(index: Int) {
                if (config.closeSearch) {
                    binding.mainMenu.closeSearch()
                } else {
                    //On tab switch, the search string is not deleted
                    //It should not start on the first startup
                    if (binding.mainMenu.isSearchOpen) getCurrentFragment()?.onSearchQueryChanged(binding.mainMenu.getCurrentQuery())
                }

                binding.viewPager.setCurrentItem(index, true)

                if (config.openSearch) {
                    if (getCurrentFragment() is ContactsFragment) {
                        binding.mainMenu.requestFocusAndShowKeyboard()
                    }
                }
            }

            override fun onTabUnselected(position: Int) {
                // No action needed
            }

            override fun onTabReselected(position: Int) {
                // No action needed
            }
        })

        // Hide tab bar if only one tab
        val tabCount = tabItems.size
        binding.tabBar.beGoneIf(tabCount == 1)
        
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
                // onPageSelected callback will automatically select the tab in tabBar
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

            val customToolbar = binding.mainMenu.requireCustomToolbar()
            if (customToolbar.isSearchExpanded) {
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
        val customToolbar = binding.mainMenu.requireCustomToolbar()
        if (customToolbar.isSearchExpanded) {
            customToolbar.collapseSearch()
            binding.mainMenu.isSearchOpen = false
            getAllFragments().forEach {
                it?.onSearchQueryChanged("")
            }
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
