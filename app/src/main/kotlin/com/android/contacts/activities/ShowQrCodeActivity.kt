package com.android.contacts.activities

import android.graphics.Bitmap
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageView
import com.android.contacts.R
import com.android.contacts.databinding.ActivityShowQrcodeBinding
import com.android.contacts.helpers.QrCodeHelper
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.ContactsHelper
import com.goodwy.commons.helpers.ensureBackgroundThread
import com.goodwy.commons.models.contacts.Contact

class ShowQrCodeActivity : SimpleActivity() {
    private lateinit var binding: ActivityShowQrcodeBinding
    private var contact: Contact? = null
    private var qrCodeBitmap: Bitmap? = null

    companion object {
        const val CONTACT_ID_EXTRA = "contact_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityShowQrcodeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val contactId = intent.getIntExtra(CONTACT_ID_EXTRA, 0)
        if (contactId == 0) {
            toast(com.goodwy.commons.R.string.unknown_error_occurred)
            finish()
            return
        }

        ensureBackgroundThread {
            contact = ContactsHelper(this@ShowQrCodeActivity).getContactWithId(contactId)
            if (contact == null) {
                runOnUiThread {
                    toast(com.goodwy.commons.R.string.unknown_error_occurred)
                    finish()
                }
                return@ensureBackgroundThread
            }
            runOnUiThread {
                setupToolbar()
                generateAndDisplayQrCode()
            }
        }
    }

    private fun setupToolbar() {
        binding.toolbar.apply {
            setNavigationIcon(com.goodwy.commons.R.drawable.ic_arrow_left_vector)
            setNavigationIconTint(getProperTextColor())
            setNavigationOnClickListener { finish() }
            title = contact?.getNameToDisplay() ?: getString(R.string.qr_code)
            setTitleColor(getProperTextColor())
        }
    }

    private fun generateAndDisplayQrCode() {
        ensureBackgroundThread {
            val vCardString = QrCodeHelper.contactToVCardString(contact!!)
            val size = resources.getDimensionPixelSize(com.goodwy.commons.R.dimen.normal_margin) * 20
            qrCodeBitmap = QrCodeHelper.generateQrCodeBitmap(vCardString, size, size)
            
            runOnUiThread {
                if (qrCodeBitmap != null) {
                    binding.qrCodeImage.setImageBitmap(qrCodeBitmap)
                    binding.qrCodeImage.scaleType = ImageView.ScaleType.CENTER_INSIDE
                } else {
                    toast(com.goodwy.commons.R.string.unknown_error_occurred)
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_qr_code, menu)
        updateMenuItemColors(menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.share_qr_code -> {
                shareQrCode()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun shareQrCode() {
        qrCodeBitmap?.let { bitmap ->
            ensureBackgroundThread {
                try {
                    val file = getCachePhoto()
                    file.outputStream().use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    sharePathIntent(file.absolutePath, packageName)
                } catch (e: Exception) {
                    runOnUiThread {
                        showErrorToast(e)
                    }
                }
            }
        } ?: run {
            toast(com.goodwy.commons.R.string.unknown_error_occurred)
        }
    }
}

