package com.trainnearme.di

import com.trainnearme.core.location.FusedLocationProvider
import com.trainnearme.core.location.LocationProvider
import com.trainnearme.core.permissions.AndroidPermissionChecker
import com.trainnearme.core.permissions.PermissionChecker
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LocationModule {
    @Binds
    @Singleton
    abstract fun bindLocationProvider(provider: FusedLocationProvider): LocationProvider

    @Binds
    @Singleton
    abstract fun bindPermissionChecker(checker: AndroidPermissionChecker): PermissionChecker
}
