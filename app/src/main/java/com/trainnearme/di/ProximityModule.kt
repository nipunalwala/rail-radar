package com.trainnearme.di

import com.trainnearme.proximity.GeofenceManager
import com.trainnearme.proximity.GeofenceSyncer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ProximityModule {

    /** Lives as long as the app process; for work that must outlast a screen. */
    @Provides
    @Singleton
    fun provideAppScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    fun provideGeofenceSyncer(manager: GeofenceManager): GeofenceSyncer = manager
}
