package com.helper.mobile.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.helper.mobile.BuildConfig
import com.helper.mobile.data.local.AssistanceDao
import com.helper.mobile.data.local.HelperDatabase
import com.helper.mobile.data.local.OfferDao
import com.helper.mobile.data.local.VehicleDao
import com.helper.mobile.data.location.FusedLocationProvider
import com.helper.mobile.data.remote.AuthInterceptor
import com.helper.mobile.data.remote.HelperApi
import com.helper.mobile.data.repository.AssistanceRepositoryImpl
import com.helper.mobile.data.repository.OfferRepositoryImpl
import com.helper.mobile.data.repository.ProblemCatalog
import com.helper.mobile.data.repository.VehicleRepositoryImpl
import com.helper.mobile.data.session.SessionStore
import com.helper.mobile.data.sync.WorkManagerScheduler
import com.helper.mobile.domain.repository.AssistanceRepository
import com.helper.mobile.domain.repository.LocationProvider
import com.helper.mobile.domain.repository.OfferRepository
import com.helper.mobile.domain.repository.PendingSendScheduler
import com.helper.mobile.domain.repository.ProblemCatalogRepository
import com.helper.mobile.domain.repository.SessionRepository
import com.helper.mobile.domain.repository.VehicleRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): HelperDatabase =
        Room.databaseBuilder(context, HelperDatabase::class.java, "helper.db").build()

    @Provides
    fun vehicleDao(db: HelperDatabase): VehicleDao = db.vehicleDao()

    @Provides
    fun assistanceDao(db: HelperDatabase): AssistanceDao = db.assistanceDao()

    @Provides
    fun offerDao(db: HelperDatabase): OfferDao = db.offerDao()

    @Provides
    @Singleton
    fun preferences(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("session") }

    @Provides
    @Singleton
    fun okHttp(auth: AuthInterceptor): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(auth)
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
                },
            )
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    fun api(client: OkHttpClient): HelperApi {
        val json = Json { ignoreUnknownKeys = true }
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(HelperApi::class.java)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun session(impl: SessionStore): SessionRepository

    @Binds
    abstract fun vehicles(impl: VehicleRepositoryImpl): VehicleRepository

    @Binds
    abstract fun assistances(impl: AssistanceRepositoryImpl): AssistanceRepository

    @Binds
    abstract fun offers(impl: OfferRepositoryImpl): OfferRepository

    @Binds
    abstract fun problems(impl: ProblemCatalog): ProblemCatalogRepository

    @Binds
    abstract fun location(impl: FusedLocationProvider): LocationProvider

    @Binds
    abstract fun scheduler(impl: WorkManagerScheduler): PendingSendScheduler
}
