package com.lgcns.codeengine.data.repository.local

import androidx.room.*
import com.lgcns.codeengine.data.entity.Place
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for the places table.
 */
@Dao
interface PlaceDao {

    /**
     * Observes list of places.
     *
     * @return all places.
     */
    @Query("SELECT * FROM Place")
    fun observePlaces(): Flow<List<Place>>

    /**
     * Observes a single place.
     *
     * @param placeId the place id.
     * @return the place with placeId.
     */
    @Query("SELECT * FROM Place WHERE id = :placeId")
    fun observePlaceById(placeId: Long): Flow<Place>

    /**
     * Select all places from the places table.
     *
     * @return all places.
     */
    @Query("SELECT * FROM Place")
    suspend fun getPlaces(): List<Place>

    /**
     * Select a place by id.
     *
     * @param placeId the place id.
     * @return the place with placeId.
     */
    @Query("SELECT * FROM Place WHERE id = :placeId")
    suspend fun getPlaceById(placeId: Long): Place?

    /**
     * Insert a place in the database. If the place already exists, replace it.
     *
     * @param place the place to be inserted.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlace(place: Place)

    /**
     * Update a place.
     *
     * @param place place to be updated
     * @return the number of places updated. This should always be 1.
     */
    @Update
    suspend fun updatePlace(place: Place): Int

    /**
     * Update the favorite status of a place
     *
     * @param placeId id of the place
     * @param isFavorite status it is favorite
     */
    @Query("UPDATE Place SET isFavorite = :isFavorite WHERE id = :placeId")
    suspend fun updateFavorite(placeId: Long, isFavorite: Boolean)

    /**
     * Delete a place by id.
     *
     * @return the number of places deleted. This should always be 1.
     */
    @Query("DELETE FROM Place WHERE id = :placeId")
    suspend fun deletePlaceById(placeId: Long): Int

    /**
     * Delete all places.
     */
    @Query("DELETE FROM Place")
    suspend fun deletePlaces()

}
