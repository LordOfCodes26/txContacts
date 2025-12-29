package com.android.contacts.adapters

import android.annotation.SuppressLint
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.Menu
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import com.behaviorule.arturdumchev.library.pixels
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller
import com.goodwy.commons.adapters.MyRecyclerViewAdapter
import com.goodwy.commons.dialogs.ConfirmationDialog
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.ContactsHelper
import com.goodwy.commons.helpers.SimpleContactsHelper
import com.goodwy.commons.helpers.TAB_GROUPS
import com.goodwy.commons.helpers.ensureBackgroundThread
import com.goodwy.commons.models.contacts.Group
import com.goodwy.commons.views.MyRecyclerView
import com.android.contacts.R
import com.android.contacts.activities.SimpleActivity
import com.android.contacts.databinding.ItemGroupBinding
import com.android.contacts.dialogs.RenameGroupDialog
import com.android.contacts.extensions.config
import com.android.contacts.interfaces.RefreshContactsListener

class GroupsAdapter(
    activity: SimpleActivity, var groups: ArrayList<Group>, val refreshListener: RefreshContactsListener?, recyclerView: MyRecyclerView,
    itemClick: (Any) -> Unit
) : MyRecyclerViewAdapter(activity, recyclerView, itemClick), RecyclerViewFastScroller.OnPopupTextUpdate {

    private var textToHighlight = ""
    var showContactThumbnails = activity.config.showContactThumbnails
    var fontSize = activity.getTextSize()

    init {
        setupDragListener(true)
    }

    override fun getActionMenuId() = R.menu.cab_groups

    override fun prepareActionMode(menu: Menu) {
        val allSelected = selectedKeys.size == getSelectableItemCount() && getSelectableItemCount() > 0
        val noneSelected = selectedKeys.isEmpty()
        
        menu.apply {
            findItem(R.id.cab_rename).isVisible = isOneItemSelected()
            // Show/hide select_all and deselect_all based on selection state
            findItem(R.id.cab_select_all).isVisible = !allSelected && getSelectableItemCount() > 0
            findItem(R.id.cab_deselect_all).isVisible = !noneSelected
        }
    }

    override fun actionItemPressed(id: Int) {
        // Allow select_all and deselect_all to work even when no items are selected
        when (id) {
            R.id.cab_select_all -> {
                selectAll()
                return
            }
            R.id.cab_deselect_all -> {
                deselectAll()
                return
            }
        }

        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_rename -> renameGroup()
            R.id.cab_delete -> askConfirmDelete()
        }
    }

    override fun getSelectableItemCount() = groups.size

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int) = groups.getOrNull(position)?.id?.toInt()

    override fun getItemKeyPosition(key: Int) = groups.indexOfFirst { it.id!!.toInt() == key }

    private fun deselectAll() {
        val positions = getSelectedItemPositions()
        positions.forEach { position ->
            toggleItemSelection(false, position, false)
        }
        updateTitle()
        if (selectedKeys.isEmpty()) {
            finishActMode()
        }
    }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return createViewHolder(ItemGroupBinding.inflate(layoutInflater, parent, false).root)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val group = groups[position]
        var lastTouchX: Float = -1f
        
        // Disable default action mode, we'll show popup menu instead
        holder.bindView(group, true, false) { itemView, layoutPosition ->
            setupView(itemView, group)
            
            // Track touch position for popup menu positioning
            itemView.setOnTouchListener { view, event ->
                if (event.action == MotionEvent.ACTION_DOWN ||
                    event.action == MotionEvent.ACTION_MOVE) {
                    lastTouchX = event.x
                    // Store in view tag
                    view.tag = lastTouchX
                }
                false  // Don't consume the event
            }
        }
        
        // Set long click listener AFTER bindView to override the default behavior
        holder.itemView.setOnLongClickListener { view ->
            val touchX = (view.tag as? Float) ?: lastTouchX
            showPopupMenu(holder.itemView, group, touchX)
            true
        }
        
        bindViewHolder(holder)
    }

    override fun getItemCount() = groups.size

    private fun getItemWithKey(key: Int): Group? = groups.firstOrNull { it.id!!.toInt() == key }

    fun updateItems(newItems: ArrayList<Group>, highlightText: String = "") {
        if (newItems.hashCode() != groups.hashCode()) {
            groups = newItems
            textToHighlight = highlightText
            notifyDataSetChanged()
            finishActMode()
        } else if (textToHighlight != highlightText) {
            textToHighlight = highlightText
            notifyDataSetChanged()
        }
    }

    private fun renameGroup() {
        val group = getItemWithKey(selectedKeys.first()) ?: return
        val blurTarget = activity.findViewById<eightbitlab.com.blurview.BlurTarget>(com.goodwy.commons.R.id.mainBlurTarget)
            ?: throw IllegalStateException("mainBlurTarget not found")
        RenameGroupDialog(activity, group, blurTarget) {
            finishActMode()
            refreshListener?.refreshContacts(TAB_GROUPS)
        }
    }

    private fun askConfirmDelete() {
        val itemsCnt = selectedKeys.size
        val firstItem = getSelectedItems().first()
        val items = if (itemsCnt == 1) {
            "\"${firstItem.title}\""
        } else {
            resources.getQuantityString(R.plurals.delete_groups, itemsCnt, itemsCnt)
        }

        val baseString = com.goodwy.commons.R.string.deletion_confirmation
        val question = String.format(resources.getString(baseString), items)

        val blurTarget = activity.findViewById<eightbitlab.com.blurview.BlurTarget>(com.goodwy.commons.R.id.mainBlurTarget)
            ?: throw IllegalStateException("mainBlurTarget not found")
        ConfirmationDialog(activity, question, blurTarget = blurTarget) {
            ensureBackgroundThread {
                deleteGroups()
            }
        }
    }

    private fun deleteGroups() {
        if (selectedKeys.isEmpty()) {
            return
        }

        val groupsToRemove = groups.filter { selectedKeys.contains(it.id!!.toInt()) } as ArrayList<Group>
        val positions = getSelectedItemPositions()
        groupsToRemove.forEach {
            if (it.isPrivateSecretGroup()) {
                activity.groupsDB.deleteGroupId(it.id!!)
            } else {
                ContactsHelper(activity).deleteGroup(it.id!!)
            }
        }
        groups.removeAll(groupsToRemove)

        activity.runOnUiThread {
            if (groups.isEmpty()) {
                refreshListener?.refreshContacts(TAB_GROUPS)
                finishActMode()
            } else {
                removeSelectedItems(positions)
            }
        }
    }

    private fun getSelectedItems() = groups.filter { selectedKeys.contains(it.id?.toInt()) } as ArrayList<Group>

    private fun getLastItem() = groups.last()

    private fun setupView(view: View, group: Group) {
        ItemGroupBinding.bind(view).apply {
            groupFrame.isSelected = selectedKeys.contains(group.id!!.toInt())
            val titleWithCnt = "${group.title} (${group.contactsCount})"
            val groupTitle = if (textToHighlight.isEmpty()) {
                titleWithCnt
            } else {
                titleWithCnt.highlightTextPart(textToHighlight, properPrimaryColor)
            }

            groupName.apply {
                setTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)
                text = groupTitle
            }

            groupTmb.beVisibleIf(showContactThumbnails)
            if (showContactThumbnails) {
                groupTmb.setImageDrawable(SimpleContactsHelper(activity).getColoredGroupIcon(group.title))
                val size = (root.context.pixels(com.goodwy.commons.R.dimen.normal_icon_size) * contactThumbnailsSize).toInt()
                groupTmb.setHeightAndWidth(size)
            }

            divider.setBackgroundColor(textColor)
            if (getLastItem() == group || !root.context.config.useDividers) divider.visibility = View.INVISIBLE else divider.visibility = View.VISIBLE
        }
    }

    override fun onChange(position: Int) = groups.getOrNull(position)?.getBubbleText() ?: ""

    private fun showPopupMenu(view: View, group: Group, touchX: Float = -1f) {
        finishActMode()
        val theme = activity.getPopupMenuTheme()
        val contextTheme = ContextThemeWrapper(activity, theme)
        
        // Determine gravity based on touch position: left side = START, right side = END
        val gravity = if (touchX >= 0 && touchX < view.width / 2) {
            Gravity.START
        } else {
            Gravity.END
        }

        PopupMenu(contextTheme, view, gravity).apply {
            inflate(R.menu.cab_groups)
            
            // Prepare menu items visibility
            menu.apply {
                findItem(R.id.cab_rename).isVisible = true
                findItem(R.id.cab_select_all).isVisible = false  // Hide select all in popup menu
            }
            
            setOnMenuItemClickListener { item ->
                executeItemMenuOperation(group.id!!.toInt()) {
                    when (item.itemId) {
                        R.id.cab_rename -> renameGroup()
                        R.id.cab_delete -> askConfirmDelete()
                    }
                }
                true
            }
            show()

            // Adjust X position based on touch location using reflection
            if (touchX >= 0) {
                try {
                    // Access PopupMenu's internal PopupWindow to adjust X position
                    val popupField = PopupMenu::class.java.getDeclaredField("mPopup")
                    popupField.isAccessible = true
                    val menuPopup = popupField.get(this)

                    val popupWindowField = menuPopup.javaClass.getDeclaredField("mPopup")
                    popupWindowField.isAccessible = true
                    val popupWindow = popupWindowField.get(menuPopup) as android.widget.PopupWindow

                    // Calculate X offset: center menu on touch point
                    view.post {
                        val location = IntArray(2)
                        view.getLocationOnScreen(location)
                        val viewX = location[0]
                        val screenWidth = activity.resources.displayMetrics.widthPixels

                        // Get menu width (approximate or measure)
                        val menuWidth = (screenWidth * 0.6).toInt()
                        val offset = activity.resources.getDimensionPixelSize(com.goodwy.commons.R.dimen.smaller_margin)

                        // Calculate desired X position based on touch location
                        val touchXInt = touchX.toInt()
                        val isLeftSide = touchXInt < view.width / 2
                        var menuX: Int = if (isLeftSide) {
                            // Menu starts at touchX with offset
                            viewX + touchXInt + offset
                        } else {
                            // Menu ends at touchX with offset
                            viewX + touchXInt - menuWidth - offset
                        }

                        // Keep within screen bounds
                        if (menuX < 0) menuX = 0
                        if (menuX + menuWidth > screenWidth) menuX = screenWidth - menuWidth

                        // Get current Y position
                        val yLocation = IntArray(2)
                        view.getLocationOnScreen(yLocation)
                        val yOffset = yLocation[1] + view.height

                        // Update popup position
                        popupWindow.update(menuX, yOffset, -1, -1)
                    }
                } catch (e: Exception) {
                    // If reflection fails, use default positioning
                }
            }
        }
    }

    private fun executeItemMenuOperation(groupId: Int, callback: () -> Unit) {
        selectedKeys.add(groupId)
        callback()
        selectedKeys.remove(groupId)
    }
}
