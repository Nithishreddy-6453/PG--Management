sed -i 's/getAllRoomsSync()/getAllRooms()/g' app/src/main/java/com/example/features/backup/data/repository/BackupRepositoryImpl.kt
sed -i '/status = "Active", \/\/ Not mapped? Wait, it has status?/d' app/src/main/java/com/example/features/backup/data/repository/BackupRepositoryImpl.kt
sed -i '/dueAmount = 0.0 \/\/ ?/d' app/src/main/java/com/example/features/backup/data/repository/BackupRepositoryImpl.kt
