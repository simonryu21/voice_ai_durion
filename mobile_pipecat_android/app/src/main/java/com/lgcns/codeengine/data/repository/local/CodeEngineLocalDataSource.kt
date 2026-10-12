package com.lgcns.codeengine.data.repository.local

import com.lgcns.codeengine.data.Result
import com.lgcns.codeengine.data.Result.Error
import com.lgcns.codeengine.data.Result.Success
import com.lgcns.codeengine.data.entity.Place
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Concrete implementation of a data source as a db.
 */
class CodeEngineLocalDataSource internal constructor (
    private val placeDao: PlaceDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    fun getPlacesStream(): Flow<Result<List<Place>>> {
        return placeDao.observePlaces().map {
            Success(it)
        }
    }

    fun getPlaceStream(placeId: Long): Flow<Result<Place>> {
        return placeDao.observePlaceById(placeId).map {
            Success(it)
        }
    }

    suspend fun getPlaces(): Result<List<Place>> = withContext(ioDispatcher) {
        return@withContext try {
            Success(placeDao.getPlaces())
        } catch (e: Exception) {
            Error(e)
        }
    }

    suspend fun getPlace(placeId: Long): Result<Place> = withContext(ioDispatcher) {
        try {
            val place = placeDao.getPlaceById(placeId)
            if (place != null) {
                return@withContext Success(place)
            } else {
                return@withContext Error(Exception("Place not found!"))
            }
        } catch (e: Exception) {
            return@withContext Error(e)
        }
    }

    suspend fun insertPlace(place: Place) = withContext(ioDispatcher) {
        placeDao.insertPlace(place)
    }

    suspend fun favoritePlace(place: Place, isFavorite: Boolean) = withContext(ioDispatcher) {
        placeDao.updateFavorite(place.id, isFavorite)
    }

    suspend fun deleteAllPlaces() = withContext(ioDispatcher) {
        placeDao.deletePlaces()
    }

    suspend fun deletePlace(placeId: Long) = withContext<Unit>(ioDispatcher) {
        placeDao.deletePlaceById(placeId)
    }
}
