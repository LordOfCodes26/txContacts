package com.android.contacts.dialogs

import android.app.Activity
import android.graphics.Bitmap
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import com.android.contacts.R
import com.android.contacts.databinding.DialogQrCodeBinding
import com.android.contacts.helpers.QrCodeHelper
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.ensureBackgroundThread
import com.goodwy.commons.models.contacts.Contact
import eightbitlab.com.blurview.BlurTarget

class ShowQrCodeDialog(
    activity: Activity,
    contact: Contact,
    blurTarget: BlurTarget
) {
    private var dialog: AlertDialog? = null
    private var qrCodeBitmap: Bitmap? = null

    init {
        val binding = DialogQrCodeBinding.inflate(activity.layoutInflater)

        // Setup BlurView
        val blurView = binding.blurView
        val decorView = activity.window.decorView
        val windowBackground = decorView.background

        blurView.setOverlayColor(activity.getProperBlurOverlayColor())
        blurView.setupWith(blurTarget)
            .setFrameClearDrawable(windowBackground)
            .setBlurRadius(8f)
            .setBlurAutoUpdate(true)

        // Set dialog title and contact name
        binding.dialogTitle.text = activity.getString(R.string.qr_code)
        binding.dialogTitle.setTextColor(activity.getProperTextColor())
        
        binding.contactName.text = contact.getNameToDisplay()
        binding.contactName.setTextColor(activity.getProperTextColor())

        // Generate QR code
        ensureBackgroundThread {
            val vCardString = QrCodeHelper.contactToVCardString(contact)
            val size = activity.resources.getDimensionPixelSize(com.goodwy.commons.R.dimen.normal_margin) * 20
            qrCodeBitmap = QrCodeHelper.generateQrCodeBitmap(vCardString, size, size)

            activity.runOnUiThread {
                if (qrCodeBitmap != null) {
                    binding.qrCodeImage.setImageBitmap(qrCodeBitmap)
                    binding.qrCodeImage.scaleType = ImageView.ScaleType.CENTER_INSIDE
                } else {
                    activity.toast(com.goodwy.commons.R.string.unknown_error_occurred)
                }
            }
        }

        // Setup buttons
        val primaryColor = activity.getProperPrimaryColor()
        binding.shareButton.setTextColor(primaryColor)
        binding.shareButton.setOnClickListener {
            shareQrCode(activity)
        }

        binding.closeButton.setTextColor(primaryColor)
        binding.closeButton.setOnClickListener {
            dialog?.dismiss()
        }

        val builder = activity.getAlertDialogBuilder()
        builder.apply {
            activity.setupDialogStuff(binding.root, this) { alertDialog ->
                dialog = alertDialog
            }
        }
    }

    private fun shareQrCode(activity: Activity) {
        qrCodeBitmap?.let { bitmap ->
            ensureBackgroundThread {
                try {
                    val file = activity.getCachePhoto()
                    file.outputStream().use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    activity.sharePathIntent(file.absolutePath, activity.packageName)
                } catch (e: Exception) {
                    activity.runOnUiThread {
                        activity.showErrorToast(e)
                    }
                }
            }
        } ?: run {
            activity.toast(com.goodwy.commons.R.string.unknown_error_occurred)
        }
    }
}

