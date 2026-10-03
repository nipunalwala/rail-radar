package com.trainnearme.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.trainnearme.core.model.Line
import com.trainnearme.core.model.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Uses a real DataStore on a temporary file, so persistence itself is exercised. */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val file: File by lazy { File(folder.root, "settings.preferences_pb") }

    /** A repository on [file]; cancel the returned job before opening another one. */
    private fun TestScope.open(): Pair<SettingsRepository, Job> {
        val job = Job()
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + job),
        ) { file }
        return DataStoreSettingsRepository(dataStore) to job
    }

    @Test
    fun `defaults match the spec`() = runTest {
        val (repository, job) = open()

        val settings = repository.settings.first()

        assertEquals(Settings(), settings)
        assertEquals(500, settings.radiusMetres)
        assertEquals(true, settings.alertsEnabled)
        assertEquals(Line.entries.toSet(), settings.lines)
        assertEquals(null, settings.pickedStationId)
        job.cancelAndJoin()
    }

    @Test
    fun `changes survive reopening the store`() = runTest {
        val changed = Settings(
            alertsEnabled = false,
            radiusMetres = 800,
            trainCount = 3,
            sound = false,
            vibration = false,
            highAccuracy = true,
            lines = setOf(Line.WESTERN, Line.HARBOUR),
            pickedStationId = "dadar",
            onboardingDone = true,
        )
        val (first, firstJob) = open()
        first.update { changed }
        firstJob.cancelAndJoin()

        val (second, secondJob) = open()
        assertEquals(changed, second.settings.first())

        second.update { it.copy(pickedStationId = null) }
        assertEquals(changed.copy(pickedStationId = null), second.settings.first())
        secondJob.cancelAndJoin()
    }

    @Test
    fun `out of range values are brought back into range`() = runTest {
        val (repository, job) = open()

        repository.update { it.copy(radiusMetres = 5, trainCount = 99, lines = emptySet()) }

        val settings = repository.settings.first()
        assertEquals(Settings.RADIUS_RANGE.first, settings.radiusMetres)
        assertEquals(Settings.TRAIN_COUNT_RANGE.last, settings.trainCount)
        assertEquals(Line.entries.toSet(), settings.lines)
        job.cancelAndJoin()
    }

    @Test
    fun `observers see each change`() = runTest {
        val (repository, job) = open()
        val seen = mutableListOf<Int>()
        val radii = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.settings.collect { seen += it.radiusMetres }
        }

        repository.update { it.copy(radiusMetres = 300) }
        repository.update { it.copy(radiusMetres = 900) }

        assertEquals(listOf(500, 300, 900), seen)
        radii.cancelAndJoin()
        job.cancelAndJoin()
    }
}
