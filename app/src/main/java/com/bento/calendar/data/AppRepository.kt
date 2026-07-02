package com.bento.calendar.data

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

object AppDataSerializer : Serializer<AppData> {
    override val defaultValue: AppData
        get() = AppData()

    override suspend fun readFrom(input: InputStream): AppData = try {
        json.decodeFromString(AppData.serializer(), input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        throw CorruptionException("Cannot parse app data", e)
    }

    override suspend fun writeTo(t: AppData, output: OutputStream) {
        output.write(json.encodeToString(AppData.serializer(), t).encodeToByteArray())
    }
}

private val Context.appDataStore: DataStore<AppData> by dataStore(
    fileName = "bento.calendar.v1.json",
    serializer = AppDataSerializer,
    corruptionHandler = ReplaceFileCorruptionHandler { AppData() },
)

class AppRepository(context: Context) {
    private val store = context.applicationContext.appDataStore

    val data: Flow<AppData> = store.data

    suspend fun update(transform: (AppData) -> AppData): AppData = store.updateData(transform)
}

/** Process-wide singletons shared by the UI and broadcast receivers. */
object AppGraph {
    @Volatile
    private var repo: AppRepository? = null

    fun repository(context: Context): AppRepository =
        repo ?: synchronized(this) {
            repo ?: AppRepository(context.applicationContext).also { repo = it }
        }
}
