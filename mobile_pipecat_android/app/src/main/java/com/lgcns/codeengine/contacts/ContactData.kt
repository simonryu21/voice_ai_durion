package com.lgcns.codeengine.contacts

data class ContactData(
    val name: String,
    val phones: List<String>,
    val officePhones: List<String>,
    val officeAddress: String,
    val email: String,
    val corpName: String,
    val deptName: String,
    val titleName: String,
    val imageUri: String?
)
