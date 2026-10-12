package com.lgcns.codeengine.di

import android.content.Context
import androidx.room.Room
import com.lgcns.codeengine.data.repository.CodeEngineRepository
import com.lgcns.codeengine.data.repository.CodeEngineRepositoryImpl
import com.lgcns.codeengine.data.repository.local.CodeEngineDatabase
import com.lgcns.codeengine.data.repository.local.CodeEngineLocalDataSource
import com.lgcns.codeengine.data.repository.remote.AuthAuthenticator
import com.lgcns.codeengine.data.repository.remote.AuthInterceptor
import com.lgcns.codeengine.data.repository.remote.CodeEngineRemoteDataSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class RemoteCodeEngineDataSource

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class LocalCodeEngineDataSource

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Singleton
    @Provides
    fun provideCodeEngineRepository(
        @RemoteCodeEngineDataSource remoteDataSource: CodeEngineRemoteDataSource,
        @LocalCodeEngineDataSource localDataSource: CodeEngineLocalDataSource,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ): CodeEngineRepository {
        return CodeEngineRepositoryImpl(remoteDataSource, localDataSource, ioDispatcher)
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DataSourceModule {

    @Singleton
    @RemoteCodeEngineDataSource
    @Provides
    fun provideCodeEngineRemoteDataSource(
        authInterceptor: AuthInterceptor,
        authAuthenticator: AuthAuthenticator
    ): CodeEngineRemoteDataSource {
        return CodeEngineRemoteDataSource(authInterceptor, authAuthenticator)
    }

    @Singleton
    @LocalCodeEngineDataSource
    @Provides
    fun provideCodeEngineLocalDataSource(
        database: CodeEngineDatabase,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ): CodeEngineLocalDataSource {
        return CodeEngineLocalDataSource(database.placeDao(), ioDispatcher)
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Singleton
    @Provides
    fun provideDataBase(@ApplicationContext context: Context): CodeEngineDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            CodeEngineDatabase::class.java,
            "CodeEngine.db"
        ).build()
    }
}
