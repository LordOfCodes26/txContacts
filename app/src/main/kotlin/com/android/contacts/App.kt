package com.android.contacts

import com.goodwy.commons.RightApp
import com.goodwy.commons.helpers.DatabasePhoneNumberFormatter
import com.goodwy.commons.helpers.PhoneNumberFormatManager
import com.goodwy.commons.helpers.PhonePrefixLocationHelper
// import com.goodwy.commons.extensions.isRuStoreInstalled
// import com.goodwy.commons.helpers.rustore.RuStoreModule

class App : RightApp() {

    override fun onCreate() {
        super.onCreate()
        // if (isRuStoreInstalled()) RuStoreModule.install(this, "685363647") //TODO rustore
        
        // Initialize database-based phone number formatter
        val formatter = DatabasePhoneNumberFormatter(this)
        PhoneNumberFormatManager.customFormatter = formatter
        
        // Load phone number formats from JSON file if not already loaded
        val formatHelper = PhonePrefixLocationHelper(this)
        formatHelper.hasFormats { hasFormats ->
            android.util.Log.d("App", "Phone number formats check: hasFormats=$hasFormats")
            if (!hasFormats) {
                try {
                    // Access commons resources by creating a context for the commons package
                    val commonsContext = createPackageContext("com.goodwy.commons", 0)
                    val commonsFormatHelper = PhonePrefixLocationHelper(commonsContext)
                    // Both helpers use the same database (via applicationContext), so formats loaded
                    // with commons context will be accessible to formatter using app context
                    val resourceId = commonsContext.resources.getIdentifier("phone_number_formats", "raw", "com.goodwy.commons")
                    
                    android.util.Log.d("App", "Phone number formats resource ID: $resourceId")
                    if (resourceId != 0) {
                        commonsFormatHelper.loadFormatsFromRaw(resourceId) { count ->
                            android.util.Log.d("App", "Loaded $count phone number formats from JSON")
                            // Invalidate formatter cache so it picks up the newly loaded formats
                            // Note: PhonePrefixLocationHelper.loadFormatsFromRaw already tries to invalidate,
                            // but we do it here too to be sure
                            formatter.invalidateCache()
                            android.util.Log.d("App", "Formatter cache invalidated after loading formats")
                        }
                    } else {
                        android.util.Log.w("App", "Could not find phone_number_formats resource in commons library")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("App", "Error loading phone number formats", e)
                }
            } else {
                // Formats already exist, but invalidate cache to ensure fresh load on startup
                android.util.Log.d("App", "Formats already exist, invalidating cache")
                formatter.invalidateCache()
            }
        }
    }
}
