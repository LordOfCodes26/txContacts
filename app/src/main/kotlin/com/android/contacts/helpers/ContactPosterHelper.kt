package com.android.contacts.helpers

import android.content.Context
import android.content.Intent
import com.android.contacts.activities.ContactPosterEditorActivity
import com.goodwy.commons.databases.ContactsDatabase
import com.goodwy.commons.models.contacts.ContactPoster
import com.goodwy.commons.models.contacts.ContactPosterData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object ContactPosterHelper {
    /**
     * Launch the Contact Poster editor for a contact.
     */
    fun launchEditor(
        context: Context,
        contactId: Int,
        contactName: String,
        subtitle: String? = null
    ) {
        val intent = Intent(context, ContactPosterEditorActivity::class.java).apply {
            putExtra(ContactPosterEditorActivity.EXTRA_CONTACT_ID, contactId)
            putExtra(ContactPosterEditorActivity.EXTRA_CONTACT_NAME, contactName)
            subtitle?.let {
                putExtra(ContactPosterEditorActivity.EXTRA_SUBTITLE, it)
            }
        }
        context.startActivity(intent)
    }

    /**
     * Get poster data for a contact (suspend function for coroutines).
     */
    suspend fun getPosterData(context: Context, contactId: Int): ContactPosterData? {
        return withContext(Dispatchers.IO) {
            val database = ContactsDatabase.getInstance(context)
            val posterDao = database.ContactPosterDao()
            posterDao.getPosterForContact(contactId)?.toPosterData()
        }
    }

    /**
     * Get poster data for a contact (blocking, use in background thread).
     */
    fun getPosterDataSync(context: Context, contactId: Int): ContactPosterData? {
        val database = ContactsDatabase.getInstance(context)
        val posterDao = database.ContactPosterDao()
        return posterDao.getPosterForContactSync(contactId)?.toPosterData()
    }

    /**
     * Save poster data for a contact (suspend function for coroutines).
     */
    suspend fun savePosterData(
        context: Context,
        contactId: Int,
        posterData: ContactPosterData
    ) {
        withContext(Dispatchers.IO) {
            val database = ContactsDatabase.getInstance(context)
            val posterDao = database.ContactPosterDao()
            val poster = ContactPoster.fromPosterData(contactId, posterData)
            posterDao.insertOrUpdate(poster)
        }
    }

    /**
     * Delete poster for a contact (suspend function for coroutines).
     */
    suspend fun deletePoster(context: Context, contactId: Int) {
        withContext(Dispatchers.IO) {
            val database = ContactsDatabase.getInstance(context)
            val posterDao = database.ContactPosterDao()
            posterDao.deletePosterForContact(contactId)
        }
    }
}
