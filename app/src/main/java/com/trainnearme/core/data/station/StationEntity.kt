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
    /** Provider codes with the lines each serves, as "DDR=WESTERN;DR=CENTRAL+HARBOUR". */
    val codeLines: String,
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
    codeLines = codeLines.entries.joinToString(";") { (code, lines) ->
        "$code=${lines.joinToString("+") { it.name }}"
    },
)

fun StationEntity.toStation() = Station(
    id = id,
    name = name,
    lat = lat,
    lng = lng,
    lines = lines.split(",").map(Line::valueOf).toSet(),
    codeLines = codeLines.split(";").associate { entry ->
        val (code, lines) = entry.split("=")
        code to lines.split("+").map(Line::valueOf).toSet()
    },
)
