package com.android.contacts.helpers

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import com.behaviorule.arturdumchev.library.*
import com.android.contacts.R
import com.android.contacts.databinding.ActivityViewContactBinding
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import eightbitlab.com.blurview.BlurTarget


class ViewContactsTopBehavior(
    context: Context?,
    attrs: AttributeSet?
) : BehaviorByRules(context, attrs) {
    private lateinit var binding: ActivityViewContactBinding

    override fun calcAppbarHeight(child: View): Int = with(child) {
        return height
    }

    private fun ensureBindingInitialized(view: View): ActivityViewContactBinding {
        if (::binding.isInitialized) {
            return binding
        }

        // Find the root view of the activity layout (BlurTarget)
        // The binding expects the root view of activity_call_history.xml
        val activityRoot = view.rootView

        // Try to find mainBlurTarget first (the root of the layout)
        var layoutRoot = activityRoot.findViewById<View>(R.id.mainBlurTarget)

        // If not found, find call_history_wrapper and traverse up to find BlurTarget
        if (layoutRoot == null) {
            val wrapperView = activityRoot.findViewById<View>(R.id.contact_wrapper)
            if (wrapperView != null) {
                // Traverse up from call_history_wrapper to find BlurTarget
                var parent: View? = wrapperView.parent as? View
                while (parent != null && parent != activityRoot) {
                    // Check if this is the BlurTarget (it should be the direct parent of call_history_wrapper)
                    if (parent.javaClass.simpleName.contains("BlurTarget", ignoreCase = true)) {
                        layoutRoot = parent
                        break
                    }
                    parent = parent.parent as? View
                }
            }
        }

        // Fallback to activity root if we couldn't find the layout root
        layoutRoot = layoutRoot ?: activityRoot

        binding = ActivityViewContactBinding.bind(layoutRoot)
        return binding
    }

    override fun View.provideAppbar(): AppBarLayout {
        return ensureBindingInitialized(this).contactAppbar
    }

    override fun View.provideCollapsingToolbar(): CollapsingToolbarLayout {
        return ensureBindingInitialized(this).collapsingToolbar
    }
    override fun canUpdateHeight(progress: Float): Boolean = progress >= GONE_VIEW_THRESHOLD

    override fun View.setUpViews(): List<RuledView> {
        val binding = ensureBindingInitialized(this)
        val heightView = calcAppbarHeight(this)
        val height = if (heightView < 5) pixels(R.dimen.toolbar_height) else heightView.toFloat()

        val density = context.resources.displayMetrics.density
        val screenWidthDp = context.resources.configuration.screenWidthDp * density
        val factor = if (screenWidthDp < 1200) 0.092 else 0.088
        val screenWidth =
            ((screenWidthDp - context.resources.getDimension(com.goodwy.commons.R.dimen.activity_padding_left_right)).toDouble() * factor).toFloat()

        return listOf(
            RuledView(
                binding.topDetails.root,
                BRuleYOffset(
                    min = -(height/4),
                    max = pixels(R.dimen.zero),
                    interpolator = LinearInterpolator()
                )
            ),
            RuledView(
                binding.topDetails.contactPhoto,
                BRuleXOffset(
                    min = 0f, max = pixels(R.dimen.image_right_margin),
                    interpolator = ReverseInterpolator(LinearInterpolator())
                ),
                BRuleYOffset(
                    min = pixels(R.dimen.zero), max = pixels(R.dimen.image_top_margin),
                    interpolator = ReverseInterpolator(LinearInterpolator())
                ),
                BRuleScale(min = 0.5f, max = 1f)
            ),
            RuledView(
                binding.topDetails.contactName,
                BRuleXOffset(
                    min = 0f, max = screenWidth,
                    interpolator = ReverseInterpolator(LinearInterpolator())
                ),
                BRuleYOffset(
                    min = -pixels(R.dimen.name_top_margin), max = pixels(R.dimen.zero),
                    interpolator = LinearInterpolator()
                ),
                BRuleScale(min = 0.8f, max = 1f)
            ),
            RuledView(
                binding.topDetails.contactCompanyHolder,
                BRuleXOffset(
                    min = 0f, max = pixels(R.dimen.image_right_margin),
                    interpolator = ReverseInterpolator(LinearInterpolator())
                ),
                BRuleYOffset(
                    min = -pixels(R.dimen.company_top_margin), max = pixels(R.dimen.zero),
                    interpolator = LinearInterpolator()
                ),
                BRuleScale(min = 0.8f, max = 1f),
                BRuleAppear(visibleUntil = GONE_VIEW_THRESHOLD),
            ),
            RuledView(
                binding.topDetails.contactCompanyHolder,
                BRuleAlpha(min = 0f, max = 0.6f),
            ),
            RuledView(
                binding.topDetails.contactOrganizationJobPosition,
                BRuleYOffset(
                    min = pixels(com.goodwy.commons.R.dimen.medium_margin), max = pixels(com.goodwy.commons.R.dimen.big_margin),
                    interpolator = LinearInterpolator()
                ),
                BRuleAlpha(min = 0f, max = 0.6f),
            )
        )
    }

//    private fun actionBarSize(context: Context?): Float {
//        val styledAttributes = context!!.theme?.obtainStyledAttributes(IntArray(1) { android.R.attr.actionBarSize })
//        val actionBarSize = styledAttributes?.getDimension(0, 0F)
//        styledAttributes?.recycle()
//        return actionBarSize ?: context.pixels(R.dimen.toolbar_height)
//    }
//
//    private fun getScreenWidth(): Int {
//        return Resources.getSystem().displayMetrics.widthPixels
//    }

    companion object {
        const val GONE_VIEW_THRESHOLD = 0.4f
    }
}
