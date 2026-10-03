package com.trainnearme.core.data.timetable

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import com.trainnearme.core.model.ScheduledDeparture
import com.trainnearme.core.model.TrainType
import java.time.DayOfWeek
import java.time.LocalTime

@Entity(tableName = "timetable_entries", primaryKeys = ["providerCode", "trainNumber"])
data class TimetableEntryEntity(
    val providerCode: String,
    val trainNumber: String,
    val trainName: String,
    val destinationCode: String,
    val destinationName: String,
    val departureMinuteOfDay: Int,
    val dayOffset: Int,
    /** Bit 0 is Monday, bit 6 is Sunday. */
    val runDays: Int,
    val trainType: String,
    val originCode: String = "",
    val originName: String = "",
)

/** One row per provider code whose timetable has been fetched, even if it was empty. */
@Entity(tableName = "timetable_meta")
data class TimetableMetaEntity(
    @PrimaryKey val providerCode: String,
    val fetchedAtMillis: Long,
    /** Zero means the code is probably not the one the provider uses for locals. */
    val localCount: Int,
)

@Dao
interface TimetableDao {
    @Query("SELECT * FROM timetable_entries WHERE providerCode = :providerCode")
    suspend fun entries(providerCode: String): List<TimetableEntryEntity>

    @Query("SELECT * FROM timetable_meta WHERE providerCode = :providerCode")
    suspend fun meta(providerCode: String): TimetableMetaEntity?

    @Query("SELECT * FROM timetable_meta")
    suspend fun allMeta(): List<TimetableMetaEntity>

    @Query("DELETE FROM timetable_entries WHERE providerCode = :providerCode")
    suspend fun deleteEntries(providerCode: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntries(entries: List<TimetableEntryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeta(meta: TimetableMetaEntity)

    @Transaction
    suspend fun replace(meta: TimetableMetaEntity, entries: List<TimetableEntryEntity>) {
        deleteEntries(meta.providerCode)
        insertEntries(entries)
        insertMeta(meta)
    }
}

fun ScheduledDeparture.toEntity(providerCode: String) = TimetableEntryEntity(
    providerCode = providerCode,
    trainNumber = trainNumber,
    trainName = trainName,
    destinationCode = destinationCode,
    destinationName = destinationName,
    departureMinuteOfDay = departure.hour * 60 + departure.minute,
    dayOffset = dayOffset,
    runDays = runDays.fold(0) { mask, day -> mask or (1 shl (day.value - 1)) },
    trainType = trainType.name,
    originCode = originCode,
    originName = originName,
)

fun TimetableEntryEntity.toScheduledDeparture() = ScheduledDeparture(
    trainNumber = trainNumber,
    trainName = trainName,
    destinationCode = destinationCode,
    destinationName = destinationName,
    departure = LocalTime.of(departureMinuteOfDay / 60, departureMinuteOfDay % 60),
    dayOffset = dayOffset,
    runDays = DayOfWeek.entries.filter { runDays and (1 shl (it.value - 1)) != 0 }.toSet(),
    trainType = TrainType.valueOf(trainType),
    originCode = originCode,
    originName = originName,
)
