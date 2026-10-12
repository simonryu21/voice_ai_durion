package com.lgcns.codeengine.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Immutable model class for a Place. In order to compile with Room
 *
 * @param name name of the place
 * @param address address of the place
 * @param isFavorite whether or not this place is checked for the favorite
 * @param id id of the place
 */
@Entity(tableName = "place")
data class Place @JvmOverloads constructor(
    @ColumnInfo(name = "name") var name: String = "",
    @ColumnInfo(name = "address") var address: String = "",
    @ColumnInfo(name = "longitude") var longitude: Double = 0.0,
    @ColumnInfo(name = "latitude") var latitude: Double = 0.0,
    @ColumnInfo(name = "isFavorite") var isFavorite: Boolean = false,
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") var id: Long = 0)
{
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false
        if (other !is Place) return false

        if (name == other.name && longitude == other.longitude && latitude == other.latitude) return true

        return false
    }
}
