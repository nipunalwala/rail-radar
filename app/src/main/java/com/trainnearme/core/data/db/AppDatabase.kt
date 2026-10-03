package com.trainnearme.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.trainnearme.core.data.station.StationDao
import com.trainnearme.core.data.station.StationEntity
import com.trainnearme.core.data.timetable.TimetableDao
import com.trainnearme.core.data.timetable.TimetableEntryEntity
import com.trainnearme.core.data.timetable.TimetableMetaEntity

// Bump the version when stations.json changes; the destructive migration
// empties the table and the asset is imported again on next use.
@Database(
    entities = [StationEntity::class, TimetableEntryEntity::class, TimetableMetaEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun stationDao(): StationDao
    abstract fun timetableDao(): TimetableDao
}
