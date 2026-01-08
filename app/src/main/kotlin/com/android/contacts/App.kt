package com.android.contacts

import com.goodwy.commons.RightApp
import com.goodwy.commons.helpers.DatabasePhoneNumberFormatter
import com.goodwy.commons.helpers.PhoneNumberFormatManager
import com.goodwy.commons.helpers.PhonePrefixLocationHelper
import com.goodwy.commons.extensions.*
import com.goodwy.commons.models.PhoneNumber
import com.goodwy.commons.models.contacts.Contact
import com.goodwy.commons.models.contacts.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import com.goodwy.commons.helpers.ensureBackgroundThread
import java.util.ArrayList
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
        
        // Initialize hidden contacts programmatically
        initializeHiddenContacts()
    }
    
    /**
     * Initialize hidden contacts programmatically when the app starts.
     * These contacts will be added to the system ContactsProvider but hidden from the main list.
     */
    private fun initializeHiddenContacts() {
        ensureBackgroundThread {
            try {
                // Check if hidden contacts already exist by checking for contacts with the hidden account
                val hiddenAccountName = "Hidden Contacts"
                val hiddenAccountType = "com.goodwy.contacts.hidden"
                
                // Query to check if any hidden contacts exist
                val uri = android.provider.ContactsContract.RawContacts.CONTENT_URI
                val projection = arrayOf(android.provider.ContactsContract.RawContacts._ID)
                val selection = "${android.provider.ContactsContract.RawContacts.ACCOUNT_NAME} = ? AND ${android.provider.ContactsContract.RawContacts.ACCOUNT_TYPE} = ?"
                val selectionArgs = arrayOf(hiddenAccountName, hiddenAccountType)
                
                val cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
                val hasHiddenContacts = cursor?.use { it.count > 0 } ?: false
                cursor?.close()
                
                if (!hasHiddenContacts) {
                    android.util.Log.d("App", "No hidden contacts found, creating initial hidden contacts...")
                    
                    // Create hidden contacts programmatically
                    val hiddenContacts = createInitialHiddenContacts()
                    
                    hiddenContacts.forEach { contact ->
                        val success = insertHiddenContact(contact, hiddenAccountName, hiddenAccountType)
                        if (success) {
                            android.util.Log.d("App", "Created hidden contact: ${contact.firstName}")
                        } else {
                            android.util.Log.e("App", "Failed to create hidden contact: ${contact.firstName}")
                        }
                    }
                    
                    android.util.Log.d("App", "Initialized ${hiddenContacts.size} hidden contacts")
                } else {
                    android.util.Log.d("App", "Hidden contacts already exist, skipping initialization")
                }
            } catch (e: Exception) {
                android.util.Log.e("App", "Error initializing hidden contacts", e)
            }
        }
    }
    
    /**
     * Create the initial set of hidden contacts to be added programmatically.
     * Override this method or modify this list to customize which contacts are created.
     */
    private fun createInitialHiddenContacts(): List<Contact> {
        val contacts = ArrayList<Contact>()
        
        // Example hidden contact 1
        contacts.add(Contact(
            id = 0,
            prefix = "",
            firstName = "Hidden Contact 1",
            middleName = "",
            surname = "",
            suffix = "",
            nickname = "",
            photoUri = "",
            phoneNumbers = ArrayList<PhoneNumber>().apply {
                val phoneValue = "1234567890"
                add(PhoneNumber(phoneValue, Phone.TYPE_MOBILE, "", phoneValue.normalizePhoneNumber(), true))
            },
            emails = ArrayList(),
            addresses = ArrayList(),
            source = "",
            starred = 0,
            contactId = 0,
            notes = "This is a hidden contact",
            ringtone = null,
            groups = ArrayList(),
            organization = Organization("", ""),
            websites = ArrayList(),
            relations = ArrayList(),
            events = ArrayList(),
            IMs = ArrayList(),
            mimetype = "",
            photo = null
        ))
        
        // Example hidden contact 2
        contacts.add(Contact(
            id = 0,
            prefix = "",
            firstName = "Hidden Contact 2",
            middleName = "",
            surname = "",
            suffix = "",
            nickname = "",
            photoUri = "",
            phoneNumbers = ArrayList<PhoneNumber>().apply {
                val phoneValue = "0987654321"
                add(PhoneNumber(phoneValue, Phone.TYPE_MOBILE, "", phoneValue.normalizePhoneNumber(), true))
            },
            emails = ArrayList(),
            addresses = ArrayList(),
            source = "",
            starred = 0,
            contactId = 0,
            notes = "Another hidden contact",
            ringtone = null,
            groups = ArrayList(),
            organization = Organization("", ""),
            websites = ArrayList(),
            relations = ArrayList(),
            events = ArrayList(),
            IMs = ArrayList(),
            mimetype = "",
            photo = null
        ))
        
        // Add more hidden contacts as needed...
        
        return contacts
    }
}
