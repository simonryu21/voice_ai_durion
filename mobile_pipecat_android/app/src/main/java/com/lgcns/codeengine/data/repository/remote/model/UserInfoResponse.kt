package com.lgcns.codeengine.data.repository.remote.model

import com.google.gson.annotations.SerializedName
import com.lgcns.codeengine.data.entity.UserInfo

data class UserInfoResponse(
    @SerializedName("data")
    val userInfo: UserInfo,
    val message: String
)