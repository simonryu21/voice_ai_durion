package com.lgcns.codeengine.data.repository.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.lgcns.codeengine.data.entity.Place

/**
 * The Room Database that contains the CodeEngine tables.
 *
 * Note that exportSchema should be true in production databases.
 */
@Database(entities = [Place::class], version = 1, exportSchema = false)
abstract class CodeEngineDatabase : RoomDatabase() {

    //////////////////////////////////////////////////////////////
    // DAO 추가 (Entity 와 1:1 대응)
    abstract fun placeDao(): PlaceDao

    //////////////////////////////////////////////////////////////
}
