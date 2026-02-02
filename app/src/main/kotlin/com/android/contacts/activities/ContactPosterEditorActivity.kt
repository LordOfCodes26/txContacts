package com.android.contacts.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import androidx.lifecycle.lifecycleScope
import com.goodwy.commons.activities.BaseComposeActivity
import com.goodwy.commons.compose.extensions.enableEdgeToEdgeSimple
import com.goodwy.commons.compose.theme.AppThemeSurface
import com.goodwy.commons.databases.ContactsDatabase
import com.goodwy.commons.models.contacts.ContactPoster
import com.goodwy.commons.models.contacts.ContactPosterData
import com.android.contacts.compose.ContactPosterEditorScreen
import kotlinx.coroutines.launch

class ContactPosterEditorActivity : BaseComposeActivity() {
    companion object {
        const val EXTRA_CONTACT_ID = "contact_id"
        const val EXTRA_CONTACT_NAME = "contact_name"
        const val EXTRA_SUBTITLE = "subtitle"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdgeSimple()

        val contactId = intent.getIntExtra(EXTRA_CONTACT_ID, -1)
        val contactName = intent.getStringExtra(EXTRA_CONTACT_NAME) ?: ""
        val subtitle = intent.getStringExtra(EXTRA_SUBTITLE)

        if (contactId == -1) {
            finish()
            return
        }

        val database = ContactsDatabase.getInstance(this)
        val posterDao = database.ContactPosterDao()

        // Load existing poster data
        lifecycleScope.launch {
            val existingPoster = posterDao.getPosterForContact(contactId)
            val initialPosterData = existingPoster?.toPosterData()

            setContent {
                AppThemeSurface {
                    ContactPosterEditorScreen(
                        contactName = contactName,
                        subtitle = subtitle,
                        initialPosterData = initialPosterData,
                        onSave = { posterData ->
                            lifecycleScope.launch {
                                val poster = ContactPoster.fromPosterData(contactId, posterData)
                                posterDao.insertOrUpdate(poster)
                                setResult(RESULT_OK)
                                finish()
                            }
                        },
                        onCancel = {
                            finish()
                        }
                    )
                }
            }
        }
    }
}
