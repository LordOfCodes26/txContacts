package com.android.contacts.helpers

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.goodwy.commons.models.contacts.Contact
import ezvcard.Ezvcard
import ezvcard.VCard
import ezvcard.VCardVersion
import java.io.ByteArrayOutputStream

object QrCodeHelper {
    /**
     * Converts a Contact to vCard string format for QR code
     */
    fun contactToVCardString(contact: Contact): String {
        val card = VCard()
        
        // Add formatted name
        val formattedName = arrayOf(
            contact.prefix,
            contact.firstName,
            contact.middleName,
            contact.surname,
            contact.suffix
        )
            .filter { it.isNotEmpty() }
            .joinToString(separator = " ")
        
        if (formattedName.isNotEmpty()) {
            card.formattedName = ezvcard.property.FormattedName(formattedName)
        }
        
        // Add structured name
        val structuredName = ezvcard.property.StructuredName().apply {
            if (contact.prefix.isNotEmpty()) prefixes.add(contact.prefix)
            given = contact.firstName
            if (contact.middleName.isNotEmpty()) additionalNames.add(contact.middleName)
            family = contact.surname
            if (contact.suffix.isNotEmpty()) suffixes.add(contact.suffix)
        }
        card.structuredName = structuredName
        
        // Add nickname
        if (contact.nickname.isNotEmpty()) {
            card.setNickname(contact.nickname)
        }
        
        // Add phone numbers
        contact.phoneNumbers.forEach { phoneNumber ->
            val telephone = ezvcard.property.Telephone(phoneNumber.value)
            card.addTelephoneNumber(telephone)
        }
        
        // Add emails
        contact.emails.forEach { email ->
            val emailProperty = ezvcard.property.Email(email.value)
            card.addEmail(emailProperty)
        }
        
        // Add addresses
        contact.addresses.forEach { address ->
            val addressProperty = ezvcard.property.Address().apply {
                streetAddress = address.value
            }
            card.addAddress(addressProperty)
        }
        
        // Add organization
        if (contact.organization.company.isNotEmpty() || contact.organization.jobPosition.isNotEmpty()) {
            val organization = ezvcard.property.Organization()
            if (contact.organization.company.isNotEmpty()) {
                organization.values.add(contact.organization.company)
            }
            card.organization = organization
            
            if (contact.organization.jobPosition.isNotEmpty()) {
                card.titles.add(ezvcard.property.Title(contact.organization.jobPosition))
            }
        }
        
        // Add websites
        contact.websites.forEach { website ->
            card.addUrl(website)
        }
        
        // Add notes
        if (contact.notes.isNotEmpty()) {
            card.addNote(contact.notes)
        }
        
        // Convert to vCard string
        return Ezvcard.write(card).version(VCardVersion.V4_0).go()
    }
    
    /**
     * Generates a QR code bitmap from a string
     */
    fun generateQrCodeBitmap(
        content: String,
        width: Int = 512,
        height: Int = 512
    ): Bitmap? {
        return try {
            val hints = hashMapOf<EncodeHintType, Any>().apply {
                put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H)
                put(EncodeHintType.CHARACTER_SET, "UTF-8")
                put(EncodeHintType.MARGIN, 1)
            }
            
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, width, height, hints)
            
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

