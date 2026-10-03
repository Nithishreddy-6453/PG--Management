package com.example.features.tenants.di

import com.example.features.tenants.data.api.GoogleDriveApiService
import com.example.features.tenants.data.repository.TenantMediaRepositoryImpl
import com.example.features.tenants.domain.repository.TenantMediaRepository
import com.squareup.moshi.Moshi
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TenantMediaNetworkModule {

    @Provides
    @Singleton
    fun provideGoogleDriveApiService(
        okHttpClient: OkHttpClient,
        moshi: Moshi
    ): GoogleDriveApiService {
        return Retrofit.Builder()
            .baseUrl("https://www.googleapis.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GoogleDriveApiService::class.java)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class TenantMediaBindingModule {

    @Binds
    @Singleton
    abstract fun bindTenantMediaRepository(
        impl: TenantMediaRepositoryImpl
    ): TenantMediaRepository
}
