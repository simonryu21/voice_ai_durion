package com.lgcns.codeengine.data.repository

import com.lgcns.codeengine.data.Result
import com.lgcns.codeengine.data.entity.Auth
import com.lgcns.codeengine.data.entity.Place
import com.lgcns.codeengine.data.entity.User
import com.lgcns.codeengine.data.repository.remote.model.LoginResponse
import com.lgcns.codeengine.data.repository.remote.model.UserInfoResponse
import kotlinx.coroutines.flow.Flow

/**
 * Interface to the data layer.
 */
interface CodeEngineRepository {

    suspend fun login(auth: Auth): Result<LoginResponse>

    suspend fun getUserInfo(): Result<UserInfoResponse>

    suspend fun getUsers(): Result<List<User>>

    fun getPlacesStream(): Flow<Result<List<Place>>>

    fun getPlaceStream(placeId: Long): Flow<Result<Place>>

    suspend fun getPlace(placeId: Long, forceUpdate: Boolean = false): Result<Place>

    suspend fun insertPlace(place: Place)

    suspend fun favoritePlace(place: Place, isFavorite: Boolean)

    suspend fun favoritePlace(placeId: Long, isFavorite: Boolean)

    suspend fun deleteAllPlaces()

    suspend fun deletePlace(placeId: Long)
}
