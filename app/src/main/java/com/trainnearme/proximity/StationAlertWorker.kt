package com.trainnearme.proximity

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.trainnearme.core.data.DepartureRepository
import com.trainnearme.core.data.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** Fetches the next trains for a station and shows them as a notification. */
@HiltWorker
class StationAlertWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val departures: DepartureRepository,
    private val settings: SettingsRepository,
    private val notifier: Notifier,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val stationId = inputData.getString(STATION_ID) ?: return Result.failure()
        val current = settings.settings.first()
        val board = departures.nextDepartures(stationId, current.trainCount, current.lines)
            ?: return Result.failure()
        notifier.showAlert(board, current.sound, current.vibration)
        return Result.success()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        ForegroundInfo(Notifier.WORKING_ID, notifier.workingNotification())

    companion object {
        const val STATION_ID = "stationId"
    }
}

@Singleton
class WorkAlertScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notifier: Notifier,
) : AlertScheduler {

    override fun schedule(stationId: String) {
        val request = OneTimeWorkRequestBuilder<StationAlertWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setInputData(workDataOf(StationAlertWorker.STATION_ID to stationId))
            .build()
        // A newer alert replaces one still being prepared, so only one is ever shown.
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancel(stationId: String) = notifier.cancelAlert(stationId)

    private companion object {
        const val WORK_NAME = "station-alert"
    }
}
