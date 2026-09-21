sed -i '/fun provideBackupRepository(/,/^    }/c\
    @Provides\
    @Singleton\
    fun provideBackupRepository(\
        backupRepositoryImpl: com.example.features.backup.data.repository.BackupRepositoryImpl\
    ): com.example.features.backup.domain.repository.BackupRepository {\
        return backupRepositoryImpl\
    }' app/src/main/java/com/example/core/di/Modules.kt
