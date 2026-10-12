package com.lgcns.codeengine.data.repository.appinventory

import com.lgcns.codeengine.data.repository.appinventory.model.AppInventoryRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface AppInventoryApi {

    @POST("api/v1/users/me/app-inventory")
    suspend fun uploadAppInventory(
        @Header("Authorization") auth: String,
        @Body request: AppInventoryRequest
    ): Response<Void>
}
