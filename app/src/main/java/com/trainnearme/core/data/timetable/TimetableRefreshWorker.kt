package com.trainnearme.core.data.timetable

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.trainnearme.core.data.DepartureRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Keeps cached timetables about a week fresh. Only stations the user has
 * actually been near have a cached timetable, so this stays within the API quota.
 */
@HiltWorker
class TimetableRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val departures: DepartureRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        departures.refreshStaleTimetables(MAX_AGE)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "timetable-refresh"
        private val MAX_AGE = Duration.ofDays(7)

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<TimetableRefreshWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build(),
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
