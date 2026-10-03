package com.trainnearme.core.data.station

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.trainnearme.core.model.Line
import com.trainnearme.core.model.Station

@Entity(tableName = "stations")
data class StationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    /** Comma-separated [Line] names. */
    val lines: String,
    /** Comma-separated provider codes. */
    val providerCodes: String,
)

@Dao
interface StationDao {
    @Query("SELECT COUNT(*) FROM stations")
    suspend fun count(): Int

    @Query("SELECT * FROM stations ORDER BY name")
    suspend fun getAll(): List<StationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(stations: List<StationEntity>)
}

fun Station.toEntity() = StationEntity(
    id = id,
    name = name,
    lat = lat,
    lng = lng,
    lines = lines.joinToString(",") { it.name },
    providerCodes = providerCodes.joinToString(","),
)

fun StationEntity.toStation() = Station(
    id = id,
    name = name,
    lat = lat,
    lng = lng,
    lines = lines.split(",").map(Line::valueOf).toSet(),
    providerCodes = providerCodes.split(","),
)
