package com.example.core.di

import android.content.Context
import com.example.features.auth.data.repository.FirebaseAuthRepositoryImpl
import com.example.features.auth.domain.repository.FirebaseAuthRepository
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {

    @Provides
    @Singleton
    fun provideFirebaseApp(@ApplicationContext context: Context): FirebaseApp {
        return if (FirebaseApp.getApps(context).isEmpty()) {
            try {
                FirebaseApp.initializeApp(context) ?: createFallbackFirebaseApp(context)
            } catch (e: Exception) {
                createFallbackFirebaseApp(context)
            }
        } else {
            FirebaseApp.getInstance()
        }
    }

    private fun createFallbackFirebaseApp(context: Context): FirebaseApp {
        return try {
            val options = FirebaseOptions.Builder()
                .setApplicationId("1:1234567890:android:vxlmqz")
                .setApiKey("AIzaSyTestApiKeyForRobolectricTesting123")
                .setProjectId("pg-manager-vxlmqz")
                .build()
            FirebaseApp.initializeApp(context, options)
        } catch (e: Exception) {
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                FirebaseApp.getInstance()
            } else {
                throw e
            }
        }
    }

    @Provides
    @Singleton
    fun provideFirebaseAuth(firebaseApp: FirebaseApp): FirebaseAuth {
        return FirebaseAuth.getInstance(firebaseApp)
    }

    @Provides
    @Singleton
    fun provideFirebaseAuthRepository(
        firebaseAuth: FirebaseAuth
    ): FirebaseAuthRepository {
        return FirebaseAuthRepositoryImpl(firebaseAuth)
    }
}

