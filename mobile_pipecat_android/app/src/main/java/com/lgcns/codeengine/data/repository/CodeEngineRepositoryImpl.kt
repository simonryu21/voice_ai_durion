package com.lgcns.codeengine.data.repository

import com.lgcns.codeengine.data.Result
import com.lgcns.codeengine.data.entity.Auth
import com.lgcns.codeengine.data.entity.Place
import com.lgcns.codeengine.data.entity.User
import com.lgcns.codeengine.data.repository.local.CodeEngineLocalDataSource
import com.lgcns.codeengine.data.repository.remote.CodeEngineRemoteDataSource
import com.lgcns.codeengine.data.repository.remote.model.LoginResponse
import com.lgcns.codeengine.data.repository.remote.model.UserInfoResponse
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow

/**
 * Implementation class of [CodeEngineRepository]. Single entry point for managing CodeEngine' data.
 *
 * CorutineDispatcher의 종류에는 IO, Main, Default가 있습니다. IO는 local 및 remote data를 조회할 때 사용하며,
 * Main은 UI 작업을 할 때 사용합니다. Default는 복잡한 logic이 있어 CPU resource를 많이 사용해야하는 경우 사용합니다.
 */
class CodeEngineRepositoryImpl(
    private val remoteDataSource: CodeEngineRemoteDataSource,
    private val localDataSource: CodeEngineLocalDataSource,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : CodeEngineRepository {

    override suspend fun login(auth: Auth): Result<LoginResponse> {
        return try {
            Result.Success(remoteDataSource.login(auth))
        } catch (ex: Exception) {
            Result.Error(ex)
        }
    }

    override suspend fun getUserInfo(): Result<UserInfoResponse> {
        return try {
            Result.Success(remoteDataSource.getUserInfo())
        } catch (ex: Exception) {
            Result.Error(ex)
        }
    }

    override suspend fun getUsers(): Result<List<User>> {
        return try {
            Result.Success(remoteDataSource.getUsers())
        } catch (ex: Exception) {
            Result.Error(ex)
        }
    }

    override fun getPlacesStream(): Flow<Result<List<Place>>> {
        return localDataSource.getPlacesStream()
    }

    override fun getPlaceStream(placeId: Long): Flow<Result<Place>> {
        return localDataSource.getPlaceStream(placeId)
    }


    private suspend fun getPlaceWithId(id: Long): Result<Place> {
        return localDataSource.getPlace(id)
    }

    /**
     * Relies on [getPlaces] to fetch data and picks the place with the same ID.
     */
    override suspend fun getPlace(placeId: Long, forceUpdate: Boolean): Result<Place> {
        /*
        // Note: 원격의 data를 local에 가지고 온 후에 local data를 변경하는 경우 사용
        if (forceUpdate) {
            updatePlaceFromRemoteDataSource(placeId)
        }
        */
        return localDataSource.getPlace(placeId)
    }

    override suspend fun insertPlace(place: Place) {
        coroutineScope {
            //launch { remoteDataSource.insertPlace(place) } // Note: 원격의 data도 같이 변경해야 하는 경우 사용
            launch { localDataSource.insertPlace(place) }
        }
    }

    override suspend fun deleteAllPlaces() {
        withContext(ioDispatcher) {
            coroutineScope {
                //launch { remoteDataSource.deleteAllPlaces() } // Note: 원격의 data도 같이 변경해야 하는 경우 사용
                launch { localDataSource.deleteAllPlaces() }
            }
        }
    }

    override suspend fun deletePlace(placeId: Long) {
        coroutineScope {
            //launch { remoteDataSource.deletePlace(placeId) } // Note: 원격의 data도 같이 변경해야 하는 경우 사용
            launch { localDataSource.deletePlace(placeId) }
        }
    }

    override suspend fun favoritePlace(place: Place, isFavorite: Boolean) {
        coroutineScope {
            //launch { remoteDataSource.favoritePlace(place) } // Note: 원격의 data도 같이 변경해야 하는 경우 사용
            launch { localDataSource.favoritePlace(place, isFavorite) }
        }
    }

    override suspend fun favoritePlace(placeId: Long, isFavorite: Boolean) {
        withContext(ioDispatcher) {
            (getPlaceWithId(placeId) as? Result.Success)?.let { it ->
                favoritePlace(it.data, isFavorite)
            }
        }
    }

    /*
    // Note: 원격의 data를 local에 가지고 온 후에 local data를 변경하는 경우 사용
    override suspend fun getPlaces(forceUpdate: Boolean): Result<List<Place>> {
        if (forceUpdate) {
            try {
                updatePlacesFromRemoteDataSource()
            } catch (ex: Exception) {
                return Result.Error(ex)
            }
        }
        return localDataSource.getPlaces()
    }
    */

    /*
    // Note: 원격의 data를 local에 가지고 온 후에 local data를 변경하는 경우 사용
    override suspend fun refreshPlaces() {
        updatePlacesFromRemoteDataSource()
    }
    */

    /*
    // Note: 원격의 data를 local에 가지고 온 후에 local data를 변경하는 경우 사용
    override suspend fun refreshPlace(placeId: Long) {
        updatePlaceFromRemoteDataSource(placeId)
    }
     */

    /*
    // Note: 원격의 data를 local에 가지고 온 후에 local data를 변경하는 경우 사용
    private suspend fun updatePlacesFromRemoteDataSource() {
        val remotePlaces = remoteDataSource.getPlaces()

        if (remotePlaces is Result.Success) {
            localDataSource.deleteAllPlaces()
            remotePlaces.data.forEach { place ->
                localDataSource.insertPlace(place)
            }
        } else if (remotePlaces is Result.Error) {
            throw remotePlaces.exception
        }
    }
     */

    /*
    // Note: 원격의 data를 local에 가지고 온 후에 local data를 변경하는 경우 사용
    private suspend fun updatePlaceFromRemoteDataSource(placeId: Long) {
        val remotePlace = remoteDataSource.getPlace(placeId)

        if (remotePlace is Result.Success) {
            localDataSource.insertPlace(remotePlace.data)
        }
    }
    */

}
