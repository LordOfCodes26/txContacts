package com.android.contacts.adapters

import android.annotation.SuppressLint
import android.util.TypedValue
import android.view.Gravity
import android.view.Menu
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import com.goodwy.commons.views.BlurPopupMenu
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

    // Disable action mode by returning 0
    override fun getActionMenuId() = 0

    override fun prepareActionMode(menu: Menu) {
        // No-op: action mode is disabled
    }

    override fun actionItemPressed(id: Int) {
        // No-op: action mode is disabled
    }

    override fun getSelectableItemCount() = 0

    override fun getIsItemSelectable(position: Int) = false

    override fun getItemSelectionKey(position: Int) = null

    override fun getItemKeyPosition(key: Int) = -1

    override fun onActionModeCreated() {
        // No-op: action mode is disabled
    }

    override fun onActionModeDestroyed() {
        // No-op: action mode is disabled
    }

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
            var lastTouchY: Float = -1f
            itemView.setOnTouchListener { view, event ->
                if (event.action == MotionEvent.ACTION_DOWN ||
                    event.action == MotionEvent.ACTION_MOVE) {
                    lastTouchX = event.x
                    lastTouchY = event.y
                    // Store in view tag as Pair<Float, Float>
                    view.tag = Pair(lastTouchX, lastTouchY)
                }
                false  // Don't consume the event
            }
        }
        
        // Set long click listener AFTER bindView to override the default behavior
        holder.itemView.setOnLongClickListener { view ->
            val touchPos = (view.tag as? Pair<*, *>)
            val touchX = (touchPos?.first as? Float) ?: lastTouchX
            val touchY = (touchPos?.second as? Float) ?: -1f
            showPopupMenu(holder.itemView, group, touchX, touchY)
            true
        }
        
        bindViewHolder(holder)
    }

    override fun getItemCount() = groups.size

    fun updateItems(newItems: ArrayList<Group>, highlightText: String = "") {
        if (newItems.hashCode() != groups.hashCode()) {
            groups = newItems
            textToHighlight = highlightText
            notifyDataSetChanged()
        } else if (textToHighlight != highlightText) {
            textToHighlight = highlightText
            notifyDataSetChanged()
        }
    }

    private fun renameGroup(group: Group) {
        val blurTarget = activity.findViewById<eightbitlab.com.blurview.BlurTarget>(com.goodwy.commons.R.id.mainBlurTarget)
            ?: throw IllegalStateException("mainBlurTarget not found")
        RenameGroupDialog(activity, group, blurTarget) {
            refreshListener?.refreshContacts(TAB_GROUPS)
        }
    }

    private fun askConfirmDelete(group: Group) {
        val item = "\"${group.title}\""
        val baseString = com.goodwy.commons.R.string.deletion_confirmation
        val question = String.format(resources.getString(baseString), item)

        val blurTarget = activity.findViewById<eightbitlab.com.blurview.BlurTarget>(com.goodwy.commons.R.id.mainBlurTarget)
            ?: throw IllegalStateException("mainBlurTarget not found")
        ConfirmationDialog(activity, question, blurTarget = blurTarget) {
            ensureBackgroundThread {
                deleteGroups(listOf(group))
            }
        }
    }

    private fun deleteGroups(groupsToRemove: List<Group>) {
        if (groupsToRemove.isEmpty()) {
            return
        }

        val groupsToRemoveList = ArrayList(groupsToRemove)
        groupsToRemoveList.forEach {
            if (it.isPrivateSecretGroup()) {
                activity.groupsDB.deleteGroupId(it.id!!)
            } else {
                ContactsHelper(activity).deleteGroup(it.id!!)
            }
        }

        activity.runOnUiThread {
            refreshListener?.refreshContacts(TAB_GROUPS)
        }
    }

    private fun getLastItem() = groups.last()

    private fun setupView(view: View, group: Group) {
        ItemGroupBinding.bind(view).apply {
            groupFrame.isSelected = false
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

    private fun showPopupMenu(view: View, group: Group, touchX: Float = -1f, touchY: Float = -1f) {
        // Safety checks to prevent crashes
        if (activity.isDestroyed || activity.isFinishing) {
            return
        }
        
        // Determine gravity based on touch position: left side = START, right side = END
        // Use view.width only if it's been measured (width > 0)
        val gravity = if (touchX >= 0 && view.width > 0 && touchX < view.width / 2) {
            Gravity.START
        } else {
            Gravity.END
        }

        try {
            BlurPopupMenu(activity, view, gravity, touchX, touchY).apply {
                inflate(R.menu.cab_groups)
                
                // Prepare menu items visibility
                menu.apply {
                    findItem(R.id.cab_rename).isVisible = true
                    findItem(R.id.cab_select_all).isVisible = false  // Hide select all in popup menu
                    findItem(R.id.cab_deselect_all).isVisible = false  // Hide deselect all in popup menu
                }
                
                setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.cab_rename -> {
                            renameGroup(group)
                        }
                        R.id.cab_delete -> {
                            askConfirmDelete(group)
                        }
                    }
                    true
                }
                
                // Ensure menu shows
                try {
                    show()
                } catch (e: Exception) {
                    // If show fails, log and try again
                    android.util.Log.e("GroupsAdapter", "Error showing popup menu", e)
                    try {
                        show()
                    } catch (e2: Exception) {
                        android.util.Log.e("GroupsAdapter", "Error showing popup menu (retry)", e2)
                    }
                }
            }
        } catch (e: Exception) {
            // Log and silently handle any exceptions during popup menu creation/showing
            android.util.Log.e("GroupsAdapter", "Error showing popup menu", e)
        }
    }

}
