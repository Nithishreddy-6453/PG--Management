package com.example.core.di

import android.content.Context
import com.example.core.common.PgLogger
import com.example.core.common.PgLoggerImpl
import com.example.data.database.AppDatabase
import com.example.data.database.ConflictRecordDao
import com.example.data.database.ExpenseDao
import com.example.data.database.OwnerProfileDao
import com.example.data.database.PropertyDao
import com.example.data.database.RentPaymentDao
import com.example.data.database.RoomDao
import com.example.data.database.SyncQueueDao
import com.example.data.database.TenantDao
import com.example.data.repository.PgRepository
import com.example.data.sync.SyncCoordinator
import com.example.features.properties.data.CurrentPropertyManager
import com.example.features.properties.data.repository.PropertyRepositoryImpl
import com.example.features.properties.domain.repository.PropertyRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MainDispatcher

@Module
@InstallIn(SingletonComponent::class)
object CoroutinesModule {
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @MainDispatcher
    fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main
}

@Module
@InstallIn(SingletonComponent::class)
abstract class LoggerModule {
    @Binds
    @Singleton
    abstract fun bindLogger(loggerImpl: PgLoggerImpl): PgLogger
}

@Module
@InstallIn(SingletonComponent::class)
object LoggerImplModule {
    @Provides
    @Singleton
    fun providePgLoggerImpl(): PgLoggerImpl = PgLoggerImpl()
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    @Provides
    fun providePropertyDao(database: AppDatabase): PropertyDao = database.propertyDao()

    @Provides
    fun provideRoomDao(database: AppDatabase): RoomDao = database.roomDao()

    @Provides
    fun provideTenantDao(database: AppDatabase): TenantDao = database.tenantDao()

    @Provides
    fun provideRentPaymentDao(database: AppDatabase): RentPaymentDao = database.rentPaymentDao()

    @Provides
    fun provideExpenseDao(database: AppDatabase): ExpenseDao = database.expenseDao()

    @Provides
    fun provideOwnerProfileDao(database: AppDatabase): OwnerProfileDao = database.ownerProfileDao()

    @Provides
    fun provideSyncQueueDao(database: AppDatabase): SyncQueueDao = database.syncQueueDao()

    @Provides
    fun provideConflictRecordDao(database: AppDatabase): ConflictRecordDao = database.conflictRecordDao()
}

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    @Provides
    @Singleton
    fun providePropertyRepository(
        propertyDao: PropertyDao,
        currentPropertyManager: CurrentPropertyManager,
        auth: FirebaseAuth,
        syncCoordinator: SyncCoordinator
    ): PropertyRepository {
        return PropertyRepositoryImpl(propertyDao, currentPropertyManager, auth, syncCoordinator)
    }

    @Provides
    @Singleton
    fun providePgRepository(
        roomDao: RoomDao,
        tenantDao: TenantDao,
        rentPaymentDao: RentPaymentDao,
        expenseDao: ExpenseDao,
        ownerProfileDao: OwnerProfileDao,
        propertyDao: PropertyDao,
        currentPropertyManager: CurrentPropertyManager,
        syncCoordinator: SyncCoordinator
    ): PgRepository {
        return PgRepository(roomDao, tenantDao, rentPaymentDao, expenseDao, ownerProfileDao, propertyDao, currentPropertyManager, syncCoordinator)
    }

    @Provides
    @Singleton
    fun provideTenantRepository(
        roomDao: RoomDao,
        tenantDao: TenantDao,
        rentPaymentDao: RentPaymentDao,
        currentPropertyManager: CurrentPropertyManager,
        syncCoordinator: SyncCoordinator
    ): com.example.features.tenants.domain.repository.TenantRepository {
        return com.example.features.tenants.data.repository.TenantRepositoryImpl(roomDao, tenantDao, rentPaymentDao, currentPropertyManager, syncCoordinator)
    }

    @Provides
    @Singleton
    fun provideRoomRepository(
        roomDao: RoomDao,
        tenantDao: TenantDao,
        rentPaymentDao: RentPaymentDao,
        currentPropertyManager: CurrentPropertyManager,
        syncCoordinator: SyncCoordinator
    ): com.example.features.rooms.domain.repository.RoomRepository {
        return com.example.features.rooms.data.repository.RoomRepositoryImpl(roomDao, tenantDao, rentPaymentDao, currentPropertyManager, syncCoordinator)
    }

    @Provides
    @Singleton
    fun provideDashboardRepository(
        roomDao: RoomDao,
        tenantDao: TenantDao,
        rentPaymentDao: RentPaymentDao,
        expenseDao: ExpenseDao,
        ownerProfileDao: OwnerProfileDao,
        currentPropertyManager: CurrentPropertyManager
    ): com.example.features.dashboard.data.DashboardRepository {
        return com.example.features.dashboard.data.DashboardRepository(
            roomDao, tenantDao, rentPaymentDao, expenseDao, ownerProfileDao, currentPropertyManager
        )
    }

    @Provides
    @Singleton
    fun provideRentRepository(
        rentPaymentDao: RentPaymentDao,
        tenantDao: TenantDao,
        currentPropertyManager: CurrentPropertyManager,
        syncCoordinator: SyncCoordinator
    ): com.example.features.rent.domain.repository.RentRepository {
        return com.example.features.rent.data.repository.RentRepositoryImpl(rentPaymentDao, tenantDao, currentPropertyManager, syncCoordinator)
    }

    @Provides
    @Singleton
    fun provideExpenseRepository(
        expenseDao: ExpenseDao,
        currentPropertyManager: CurrentPropertyManager,
        syncCoordinator: SyncCoordinator
    ): com.example.features.expenses.domain.repository.ExpenseRepository {
        return com.example.features.expenses.data.repository.ExpenseRepositoryImpl(expenseDao, currentPropertyManager, syncCoordinator)
    }

    @Provides
    @Singleton
    fun provideSettingsRepository(
        @ApplicationContext context: Context
    ): com.example.features.settings.domain.repository.SettingsRepository {
        return com.example.features.settings.data.repository.SettingsRepositoryImpl(context)
    }

    @Provides
    @Singleton
    fun provideBackupRepository(
        backupRepositoryImpl: com.example.features.backup.data.repository.BackupRepositoryImpl
    ): com.example.features.backup.domain.repository.BackupRepository {
        return backupRepositoryImpl
    }
}
