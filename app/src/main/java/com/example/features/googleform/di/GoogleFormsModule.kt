package com.example.features.googleform.di

import com.example.features.googleform.data.api.GoogleFormsApiService
import com.example.features.googleform.data.repository.TenantRegistrationFormRepositoryImpl
import com.example.features.googleform.domain.repository.TenantRegistrationFormRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object GoogleFormsNetworkModule {

    @Provides
    @Singleton
    fun provideGoogleFormsOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()
    }

    @Provides
    @Singleton
    fun provideGoogleFormsMoshi(): Moshi {
        return Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    @Provides
    @Singleton
    fun provideGoogleFormsApiService(
        okHttpClient: OkHttpClient,
        moshi: Moshi
    ): GoogleFormsApiService {
        return Retrofit.Builder()
            .baseUrl("https://forms.googleapis.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GoogleFormsApiService::class.java)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class GoogleFormsBindingModule {

    @Binds
    @Singleton
    abstract fun bindTenantRegistrationFormRepository(
        impl: TenantRegistrationFormRepositoryImpl
    ): TenantRegistrationFormRepository

    @Binds
    @Singleton
    abstract fun bindPendingTenantRegistrationRepository(
        impl: com.example.features.googleform.data.repository.PendingTenantRegistrationRepositoryImpl
    ): com.example.features.googleform.domain.repository.PendingTenantRegistrationRepository
}
