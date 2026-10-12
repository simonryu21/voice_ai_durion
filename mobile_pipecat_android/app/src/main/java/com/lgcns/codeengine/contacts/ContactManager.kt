package com.lgcns.codeengine.contacts

import android.annotation.SuppressLint
import android.content.ContentProviderOperation
import android.content.ContentProviderResult
import android.content.Context
import android.graphics.Bitmap
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.*
import androidx.core.database.getStringOrNull
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import javax.inject.Inject


class ContactManager @Inject constructor(
    @ApplicationContext
    private val context: Context
) {
    private val cr = context.applicationContext.contentResolver

    @SuppressLint("Range", "Recycle")
    fun getContactList(): List<ContactData> {
        val contactCursor = cr.query(
            ContactsContract.Contacts.CONTENT_URI,
            null,
            null,
            null,
            ContactsContract.Data.DISPLAY_NAME
        ) ?: return emptyList()
        val resultList = mutableListOf<ContactData>()
        contactCursor.use { cursor ->
            while (cursor.moveToNext()) {
                try {
                    val contactId =
                        cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts._ID))
                    val displayName =
                        cursor.getString(cursor.getColumnIndex(Phone.DISPLAY_NAME))
                    val phoneNumbers = getPhoneNumbers(contactId)
                    val mobileList = phoneNumbers.first
                    val officePhoneList = phoneNumbers.second
                    val officeAddress = getOfficeAddress(contactId)
                    val emails = getEmails(contactId)
                    val email = emails.firstOrNull() ?: ""
                    val corpName = getCompanyName(contactId)
                    val titleName = getTitleName(contactId)
                    val deptName = getDeptName(contactId)
                    val imageUri =
                        cursor.getStringOrNull(cursor.getColumnIndex(Phone.PHOTO_URI))
                    resultList.add(
                        ContactData(
                            name = displayName,
                            phones = mobileList,
                            officePhones = officePhoneList,
                            officeAddress = officeAddress,
                            email = email,
                            corpName = corpName,
                            deptName = deptName,
                            titleName = titleName,
                            imageUri = imageUri
                        )
                    )
                } catch (e: Exception) {
                    Timber.e(e)
                }
            }
        }
        return resultList
    }

    @SuppressLint("Range")
    fun updateContact(
        contactData: ContactData
    ): Result<ContactData> {
        val contactCursor = cr.query(
            ContactsContract.Contacts.CONTENT_URI,
            null,
            null,
            null,
            null,
        ) ?: return Result.failure(Exception("cursor is null"))
        var contactId = ""
        contactCursor.use { cursor ->
            while (cursor.moveToNext()) {
                try {
                    val id = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts._ID))
                    if (getEmails(id).contains(contactData.email)) {
                        contactId = id
                        break
                    }
                } catch (e: Exception) {
                    return Result.failure(Exception("cursor is null"))
                }
            }
        }
        if (contactId.isEmpty()) {
            return Result.failure(Exception("cursor is null"))
        }
        val contact = ArrayList<ContentProviderOperation>()
        // 이름 변경
        var displayName = contactData.name
        if (contactData.titleName.isNotEmpty()) {
            displayName += " " + contactData.titleName
        }
        if (contactData.corpName.isNotEmpty()) {
            displayName += "/${contactData.corpName}"
        }
        contact.add(updateName(contactId, displayName))
        // 폰번호 변경
        contactData.phones.forEach { phoneNum ->
            contact.add(updatePhoneNum(contactId, phoneNum))
        }
        // 오피스 번호 변경
        contactData.officePhones.forEach { officePhone ->
            contact.add(updateOfficePhone(contactId, officePhone))
        }
        //  이메일 변경
        if (contactData.email.isNotEmpty()) {
            contact.add(updateEmail(contactId, contactData.email))
        }
        // 사무실 주소 변경
        if (contactData.officeAddress.isNotEmpty()) {
            contact.add(updateOfficeAddress(contactId, contactData.officeAddress))
        }
        // 회사명/부서/직급 변경
        contact.add(
            updateCorpInfo(
                contactId,
                contactData.corpName,
                contactData.deptName,
                contactData.titleName
            )
        )
//        // 프로필 사진 변경
//        if (contactData.bitMapImage != null) {
//            contact.add(updateProfile(contactId, contactData.bitMapImage))
//        }
        // Update a contact using contentResolver
        val update = kotlin.runCatching {
            context.applicationContext.contentResolver.applyBatch(
                ContactsContract.AUTHORITY, contact
            )
        }
        return if (update.isSuccess) {
            Result.success(contactData.copy(name = displayName))
        } else {
            Result.failure(Exception("update Fail"))
        }
    }

    fun addContact(
        contactData: ContactData
    ): Result<Array<ContentProviderResult>> {
        /**
         *  Code referred from
         *  https://stackoverflow.com/questions/9096186/how-to-add-contacts-programmatically-in-android
         */

        val contact = ArrayList<ContentProviderOperation>()
        contact.add(
            ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null).build()
        )

        // 이름 추가
        var displayName = contactData.name
        if (contactData.titleName.isNotEmpty()) {
            displayName += " " + contactData.titleName
        }
        if (contactData.corpName.isNotEmpty()) {
            displayName += "/${contactData.corpName}"
        }
        contact.add(insertName(displayName))
        // 폰번호 추가
        contactData.phones.forEach { phoneNum ->
            contact.add(insertPhoneNum(phoneNum))
        }
        // 오피스 번호 추가
        contactData.officePhones.forEach { officePhone ->
            contact.add(insertOfficePhone(officePhone))
        }
        //  이메일 추가
        if (contactData.email.isNotEmpty()) {
            contact.add(insertEmail(contactData.email))
        }
        // 사무실 주소 추가
        if (contactData.officeAddress.isNotEmpty()) {
            contact.add(insertOfficeAddress(contactData.officeAddress))
        }
        // 회사명/부서/직금 저장
        contact.add(
            insertCorpInfo(
                contactData.corpName,
                contactData.deptName,
                contactData.titleName
            )
        )

//        // 프로필 추가
//        if (contactData.bitMapImage != null) {
//            contact.add(insertProfileImg(contactData.bitMapImage))
//        }

        // Creates a new contact using contentResolver
        return kotlin.runCatching {
            context.applicationContext.contentResolver.applyBatch(
                ContactsContract.AUTHORITY, contact
            )
        }
    }

    /**
     * Read Function
     */
    @SuppressLint("Range")
    private fun getPhoneNumbers(contactId: String): Pair<List<String>, List<String>> {
        try {
            val mobileList = mutableListOf<String>()
            val officePhoneList = mutableListOf<String>()
            cr.query(
                Phone.CONTENT_URI,
                null,
                ContactsContract.Data.CONTACT_ID + " = " + contactId,
                null,
                null
            ).use { cursor ->
                while (cursor?.moveToNext() == true) {
                    val number =
                        cursor.getString(cursor.getColumnIndex(Phone.NUMBER))
                    val type: Int =
                        cursor.getInt(cursor.getColumnIndex(Phone.TYPE))
                    if (type == Phone.TYPE_MOBILE) {
                        mobileList.add(number)
                    } else {
                        officePhoneList.add(number)
                    }
                }
            }
            return Pair(mobileList, officePhoneList)
        } catch (e: Exception) {
            return Pair(emptyList(), emptyList())
        }
    }

    @SuppressLint("Range")
    private fun getEmails(contactId: String): List<String> {
        try {
            val emailList = mutableListOf<String>()
            val emailCursor = cr.query(
                Email.CONTENT_URI, null,
                ContactsContract.Data.CONTACT_ID + " = " + contactId, null, null
            )
            emailCursor.use { emails ->
                while (emails?.moveToNext() == true) {
                    emailList.add(
                        emails.getString(emails.getColumnIndex(Email.DATA))
                    )
                }
            }
            return emailList
        } catch (e: Exception) {
            return emptyList()
        }
    }

    @SuppressLint("Range")
    private fun getCompanyName(contactId: String): String {
        try {
            var compName = ""
            val companyCursor = cr.query(
                ContactsContract.Data.CONTENT_URI,
                null,
                ContactsContract.Data.CONTACT_ID + " = ? AND " + ContactsContract.Data.MIMETYPE + " = ?",
                arrayOf(contactId, Organization.CONTENT_ITEM_TYPE),
                null,
                null
            )
            companyCursor.use { cursor ->
                while (cursor?.moveToNext() == true) {
                    compName =
                        cursor.getString(cursor.getColumnIndex(Organization.COMPANY))
                }
            }
            return compName
        } catch (e: Exception) {
            return ""
        }
    }

    @SuppressLint("Range")
    private fun getTitleName(contactId: String): String {
        try {
            var titleName = ""
            val companyCursor = cr.query(
                ContactsContract.Data.CONTENT_URI,
                null,
                ContactsContract.Data.CONTACT_ID + " = ? AND " + ContactsContract.Data.MIMETYPE + " = ?",
                arrayOf(contactId, Organization.CONTENT_ITEM_TYPE),
                null,
                null
            )
            companyCursor.use { cursor ->
                while (cursor?.moveToNext() == true) {
                    titleName = cursor.getString(cursor.getColumnIndex(Organization.TITLE))
                }
            }
            return titleName
        } catch (e: Exception) {
            return ""
        }
    }

    @SuppressLint("Range")
    private fun getDeptName(contactId: String): String {
        var deptName = ""
        try {
            val companyCursor = cr.query(
                ContactsContract.Data.CONTENT_URI,
                null,
                ContactsContract.Data.CONTACT_ID + " = ? AND " + ContactsContract.Data.MIMETYPE + " = ?",
                arrayOf(contactId, Organization.CONTENT_ITEM_TYPE),
                null,
                null
            )
            companyCursor.use { cursor ->
                while (cursor?.moveToNext() == true) {
                    deptName =
                        cursor.getString(cursor.getColumnIndex(Organization.DEPARTMENT))
                }
            }
            return deptName
        } catch (e: Exception) {
            return ""
        }
    }

    @SuppressLint("Range")
    private fun getOfficeAddress(contactId: String): String {
        try {
            var address = ""
            val companyCursor = cr.query(
                StructuredPostal.CONTENT_URI,
                null,
                ContactsContract.Data.CONTACT_ID + " = $contactId",
                null,
                null,
                null
            )
            companyCursor.use { cursor ->
                while (cursor?.moveToNext() == true) {
                    val type: Int =
                        cursor.getInt(cursor.getColumnIndex(StructuredPostal.TYPE))
                    if (type == StructuredPostal.TYPE_WORK) {
                        address = cursor.getString(cursor.getColumnIndex(StructuredPostal.DATA))
                    }
                }
            }
            return address
        } catch (e: Exception) {
            return ""
        }
    }

    //    @SuppressLint("Range")
//    private fun getBitmapImage(contactId: String): String {
//        try {
//            var address = ""
//            val companyCursor = cr.query(
//                StructuredPostal.CONTENT_URI,
//                null,
//                StructuredPostal.CONTACT_ID + " = $contactId",
//                null,
//                null,
//                null
//            )
//            companyCursor.use { cursor ->
//                while (cursor?.moveToNext() == true) {
//                    val type: Int =
//                        cursor.getInt(cursor.getColumnIndex(StructuredPostal.TYPE))
//                    if (type == StructuredPostal.TYPE_WORK) {
//                        address = cursor.getString(cursor.getColumnIndex(StructuredPostal.DATA))
//                    }
//                }
//            }
//            return address
//        } catch (e: Exception) {
//            return ""
//        }
//    }
    private fun updateName(contactId: String, name: String): ContentProviderOperation {
        return ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
            .withSelection(
                ContactsContract.Data.CONTACT_ID + " = ? AND " + ContactsContract.Data.MIMETYPE + " = ?",
                arrayOf(contactId, StructuredName.CONTENT_ITEM_TYPE)
            )
            .withValue(StructuredName.DISPLAY_NAME, name).build()
    }

    private fun updatePhoneNum(contactId: String, phoneNum: String): ContentProviderOperation {
        return ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
            .withSelection(
                ContactsContract.Data.CONTACT_ID + " = ? AND " + ContactsContract.Data.MIMETYPE + " = ? AND " + Organization.TYPE + " = ?",
                arrayOf(contactId, Phone.CONTENT_ITEM_TYPE, Phone.TYPE_MOBILE.toString())
            )
            .withValue(Phone.NUMBER, phoneNum).build()
    }

    private fun updateOfficePhone(
        contactId: String,
        officePhone: String
    ): ContentProviderOperation {
        return ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
            .withSelection(
                ContactsContract.Data.CONTACT_ID + " = ? AND " + ContactsContract.Data.MIMETYPE + " = ? AND " + Phone.TYPE + " = ?",
                arrayOf(contactId, Phone.CONTENT_ITEM_TYPE, Phone.TYPE_WORK.toString())
            )
            .withValue(Phone.NUMBER, officePhone).build()
    }

    private fun updateEmail(contactId: String, email: String): ContentProviderOperation {
        return ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
            .withSelection(
                ContactsContract.Data.CONTACT_ID + " = ? AND " + ContactsContract.Data.MIMETYPE + " = ? AND " + Email.TYPE + " = ?",
                arrayOf(contactId, Email.CONTENT_ITEM_TYPE, Email.TYPE_WORK.toString())
            )
            .withValue(Email.DATA, email).build()
    }

    private fun updateOfficeAddress(contactId: String, address: String): ContentProviderOperation {
        return ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
            .withSelection(
                ContactsContract.Data.CONTACT_ID + " = ? AND " + ContactsContract.Data.MIMETYPE + " = ? AND " + StructuredPostal.TYPE + " = ?",
                arrayOf(
                    contactId,
                    StructuredPostal.CONTENT_ITEM_TYPE,
                    StructuredPostal.TYPE_WORK.toString()
                )
            )
            .withValue(StructuredPostal.DATA, address).build()
    }

    private fun updateCorpInfo(
        contactId: String,
        corpName: String,
        deptName: String,
        titleName: String
    ): ContentProviderOperation {
        val operation: ContentProviderOperation.Builder =
            ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                .withSelection(
                    ContactsContract.Data.CONTACT_ID + " = ? AND " + ContactsContract.Data.MIMETYPE + " = ?",
                    arrayOf(contactId, Organization.CONTENT_ITEM_TYPE)
                )
                .withValue(Organization.COMPANY, corpName)
                .withValue(Organization.TYPE, StructuredPostal.TYPE_WORK)

        if (titleName.isNotEmpty()) {
            operation.withValue(Organization.TITLE, titleName)
                .withValue(Organization.TYPE, StructuredPostal.TYPE_WORK)
        }
        if (deptName.isNotEmpty()) {
            operation.withValue(Organization.DEPARTMENT, deptName)
                .withValue(Organization.TYPE, StructuredPostal.TYPE_WORK)
        }
        return operation.build()
    }

    private fun updateProfile(contactId: String, img: Bitmap): ContentProviderOperation {
        val stream = ByteArrayOutputStream()
        img.compress(Bitmap.CompressFormat.JPEG, 100, stream)
        return ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
            .withSelection(
                ContactsContract.Data.CONTACT_ID + " = ? AND " + ContactsContract.Data.MIMETYPE + " = ?",
                arrayOf(contactId, Photo.CONTENT_ITEM_TYPE)
            ).withValue(Photo.PHOTO, stream.toByteArray()).build()
    }

    /**
     * Insert Function
     */
    private fun insertName(name: String): ContentProviderOperation {
        return ContentProviderOperation
            .newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
            .withValue(ContactsContract.Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
            .withValue(StructuredName.DISPLAY_NAME, name).build()
    }

    private fun insertPhoneNum(phoneNum: String): ContentProviderOperation {
        return ContentProviderOperation
            .newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
            .withValue(ContactsContract.Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
            .withValue(Phone.NUMBER, phoneNum)
            .withValue(Phone.TYPE, Phone.TYPE_MOBILE).build()
    }

    private fun insertOfficePhone(officePhone: String): ContentProviderOperation {
        return ContentProviderOperation
            .newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
            .withValue(ContactsContract.Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
            .withValue(Phone.NUMBER, officePhone)
            .withValue(Phone.TYPE, Phone.TYPE_WORK).build()
    }

    private fun insertEmail(email: String): ContentProviderOperation {
        return ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
            .withValue(ContactsContract.Data.MIMETYPE, Email.CONTENT_ITEM_TYPE)
            .withValue(Email.DATA, email)
            .withValue(Email.TYPE, Email.TYPE_WORK).build()
    }

    private fun insertOfficeAddress(address: String): ContentProviderOperation {
        return ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
            .withValue(ContactsContract.Data.MIMETYPE, StructuredPostal.CONTENT_ITEM_TYPE)
            .withValue(StructuredPostal.DATA, address)
            .withValue(StructuredPostal.TYPE, StructuredPostal.TYPE_WORK)
            .build()
    }

    private fun insertCorpInfo(
        corpName: String,
        deptName: String,
        titleName: String
    ): ContentProviderOperation {
        val operation: ContentProviderOperation.Builder =
            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, Organization.CONTENT_ITEM_TYPE)
                .withValue(Organization.COMPANY, corpName)
                .withValue(Organization.TYPE, StructuredPostal.TYPE_WORK)

        if (titleName.isNotEmpty()) {
            operation.withValue(Organization.TITLE, titleName)
                .withValue(Organization.TYPE, StructuredPostal.TYPE_WORK)
        }
        if (deptName.isNotEmpty()) {
            operation.withValue(Organization.DEPARTMENT, deptName)
                .withValue(Organization.TYPE, StructuredPostal.TYPE_WORK)
        }
        return operation.build()
    }

    private fun insertProfileImg(img: Bitmap): ContentProviderOperation {
        val stream = ByteArrayOutputStream()
        img.compress(Bitmap.CompressFormat.JPEG, 100, stream)
        return ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
            .withValue(ContactsContract.Data.MIMETYPE, Photo.CONTENT_ITEM_TYPE)
            .withValue(Photo.PHOTO, stream.toByteArray())
            .build()
    }
}