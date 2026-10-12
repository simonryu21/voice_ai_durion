package com.lgcns.codeengine.di

import com.lgcns.codeengine.data.repository.datastore.*
import com.lgcns.codeengine.data.repository.device.DeviceRepository
import com.lgcns.codeengine.data.repository.device.DeviceRepositoryImpl
import com.lgcns.codeengine.data.repository.media.MediaRepository
import com.lgcns.codeengine.data.repository.media.MediaRepositoryImpl
import com.lgcns.codeengine.data.repository.location.LocationRepository
import com.lgcns.codeengine.data.repository.location.LocationRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
abstract class SingletonRepositoryModule {

    @Singleton
    @Binds
    abstract fun provideWebDataStoreRepository(impl: WebDataStoreRepositoryImpl): WebDataStoreRepository

    @Singleton
    @Binds
    abstract fun provideSecureDataStoreRepository(impl: SecureDataRepositoryImpl): SecureDataStoreRepository

    @Singleton
    @Binds
    abstract fun provideLocationRepository(impl: LocationRepositoryImpl): LocationRepository

    @Singleton
    @Binds
    abstract fun provideMediaRepository(impl: MediaRepositoryImpl): MediaRepository

    @Singleton
    @Binds
    abstract fun provideDeviceRepository(impl: DeviceRepositoryImpl): DeviceRepository

    @Singleton
    @Binds
    abstract fun provideDataStoreRepository(impl: DataStoreRepositoryImpl): DataStoreRepository

}
