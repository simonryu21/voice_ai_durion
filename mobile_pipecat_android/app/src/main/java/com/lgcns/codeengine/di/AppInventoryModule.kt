package com.lgcns.codeengine.di

import android.content.Context
import com.lgcns.codeengine.data.repository.appinventory.AppInventoryRepository
import com.lgcns.codeengine.data.repository.datastore.DataStoreRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppInventoryModule {

    @Singleton
    @Provides
    fun provideAppInventoryRepository(
        @ApplicationContext context: Context,
        dataStoreRepository: DataStoreRepository,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ): AppInventoryRepository {
        return AppInventoryRepository(context, dataStoreRepository, ioDispatcher)
    }
}
