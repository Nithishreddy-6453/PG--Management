package com.example.features.settings.data.repository

import android.content.Context
import android.net.Uri
import com.example.features.dashboard.data.DashboardRepository
import com.example.features.settings.domain.repository.BackupRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dashboardRepository: DashboardRepository
) : BackupRepository {

    override suspend fun createBackup(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val rooms = dashboardRepository.getRooms()
            // Simplified JSON Backup implementation
            val root = JSONObject()
            
            val roomsArray = JSONArray()
            rooms.forEach { room ->
                val obj = JSONObject()
                obj.put("roomNumber", room.roomNumber)
                obj.put("capacity", room.capacity)
                obj.put("floor", room.floor)
                obj.put("roomType", room.roomType)
                obj.put("ratePerBed", room.ratePerBed)
                roomsArray.put(obj)
            }
            root.put("rooms", roomsArray)
            
            // Note: A full implementation would also serialize tenants, payments, expenses, etc.
            
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(root.toString(4).toByteArray())
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun restoreBackup(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Simplified restore validation logic
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val jsonString = inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(jsonString)
                if (!root.has("rooms")) {
                    return@withContext Result.failure(Exception("Invalid backup format"))
                }
                // Note: A full implementation would deserialize and insert into Room
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
