package com.trainnearme.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.trainnearme.core.data.station.StationDao
import com.trainnearme.core.data.station.StationEntity
import com.trainnearme.core.data.timetable.TimetableDao
import com.trainnearme.core.data.timetable.TimetableEntryEntity
import com.trainnearme.core.data.timetable.TimetableMetaEntity

// Bump the version when stations.json or a table changes; the destructive
// migration empties the tables, the asset is imported again on next use and
// timetables are fetched again when their station is next shown.
@Database(
    entities = [StationEntity::class, TimetableEntryEntity::class, TimetableMetaEntity::class],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun stationDao(): StationDao
    abstract fun timetableDao(): TimetableDao
}
