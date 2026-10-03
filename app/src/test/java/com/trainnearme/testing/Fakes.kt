package com.trainnearme.testing

import com.trainnearme.core.data.TrainDataProvider
import com.trainnearme.core.data.station.StationDao
import com.trainnearme.core.data.station.StationEntity
import com.trainnearme.core.data.timetable.TimetableDao
import com.trainnearme.core.data.timetable.TimetableEntryEntity
import com.trainnearme.core.data.timetable.TimetableMetaEntity
import com.trainnearme.core.location.LatLng
import com.trainnearme.core.location.LocationProvider
import com.trainnearme.core.model.Departure
import com.trainnearme.core.model.ScheduledDeparture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.io.IOException

class FakeStationDao : StationDao {
    val rows = mutableListOf<StationEntity>()
    override suspend fun count() = rows.size
    override suspend fun getAll() = rows.toList()
    override suspend fun insertAll(stations: List<StationEntity>) {
        rows += stations
    }
}

class FakeTimetableDao : TimetableDao {
    val entries = mutableListOf<TimetableEntryEntity>()
    val metas = mutableMapOf<String, TimetableMetaEntity>()
    override suspend fun entries(providerCode: String) = entries.filter { it.providerCode == providerCode }
    override suspend fun meta(providerCode: String) = metas[providerCode]
    override suspend fun allMeta() = metas.values.toList()
    override suspend fun deleteEntries(providerCode: String) {
        entries.removeAll { it.providerCode == providerCode }
    }
    override suspend fun insertEntries(entries: List<TimetableEntryEntity>) {
        this.entries += entries
    }
    override suspend fun insertMeta(meta: TimetableMetaEntity) {
        metas[meta.providerCode] = meta
    }
}

class FakeProvider : TrainDataProvider {
    val timetables = mutableMapOf<String, List<ScheduledDeparture>>()
    val live = mutableMapOf<String, List<Departure>>()
    var liveDelayMillis = 0L
    var liveFails = false
    var timetableFails = false
    val liveCalls = mutableListOf<String>()
    val timetableCalls = mutableListOf<String>()

    override suspend fun timetable(stationCode: String): List<ScheduledDeparture> {
        timetableCalls += stationCode
        if (timetableFails) throw IOException("offline")
        return timetables[stationCode].orEmpty()
    }

    override suspend fun liveBoard(stationCode: String, hoursAhead: Int): List<Departure> {
        liveCalls += stationCode
        delay(liveDelayMillis)
        if (liveFails) throw IOException("offline")
        return live[stationCode].orEmpty()
    }
}

class FakeLocationProvider(var location: LatLng? = null) : LocationProvider {
    override suspend fun current(): LatLng? = location
}

/** Makes viewModelScope run on a test dispatcher. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}
