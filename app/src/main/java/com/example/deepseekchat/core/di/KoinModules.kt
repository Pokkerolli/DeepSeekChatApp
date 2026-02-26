package com.example.deepseekchat.core.di

import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.deepseekchat.BuildConfig
import com.example.deepseekchat.data.local.dao.MessageDao
import com.example.deepseekchat.data.local.dao.SessionDao
import com.example.deepseekchat.data.local.datastore.ActiveSessionPreferences
import com.example.deepseekchat.data.local.db.AppDatabase
import com.example.deepseekchat.data.remote.api.DeepSeekApi
import com.example.deepseekchat.data.remote.stream.SseStreamParser
import com.example.deepseekchat.data.repository.ChatRepositoryImpl
import com.example.deepseekchat.domain.repository.ChatRepository
import com.example.deepseekchat.domain.usecase.CreateSessionUseCase
import com.example.deepseekchat.domain.usecase.DeleteSessionUseCase
import com.example.deepseekchat.domain.usecase.GetActiveSessionUseCase
import com.example.deepseekchat.domain.usecase.ObserveMessagesUseCase
import com.example.deepseekchat.domain.usecase.ObserveSessionsUseCase
import com.example.deepseekchat.domain.usecase.RunContextSummarizationIfNeededUseCase
import com.example.deepseekchat.domain.usecase.SendMessageUseCase
import com.example.deepseekchat.domain.usecase.SetSessionContextCompressionEnabledUseCase
import com.example.deepseekchat.domain.usecase.SetSessionSystemPromptUseCase
import com.example.deepseekchat.domain.usecase.SetActiveSessionUseCase
import com.example.deepseekchat.presentation.chat.ChatViewModel
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import retrofit2.Retrofit

val databaseModule = module {
    single<AppDatabase> {
        Room.databaseBuilder(get(), AppDatabase::class.java, "deepseek_chat.db")
            .addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7
            )
            .fallbackToDestructiveMigration()
            .build()
    }

    single<SessionDao> { get<AppDatabase>().sessionDao() }
    single<MessageDao> { get<AppDatabase>().messageDao() }
    single { ActiveSessionPreferences(get()) }
}

val networkModule = module {
    single {
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }

    single {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val builder = chain.request().newBuilder()
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .header("Cache-Control", "no-cache")
                    .header("Accept-Encoding", "identity")

                if (BuildConfig.DEEPSEEK_API_KEY.isNotBlank()) {
                    builder.header("Authorization", "Bearer ${BuildConfig.DEEPSEEK_API_KEY}")
                }

                chain.proceed(builder.build())
            }
            .addInterceptor(loggingInterceptor)
            .build()
    }

    single {
        val json: Json = get()
        Retrofit.Builder()
            .baseUrl(BuildConfig.DEEPSEEK_BASE_URL.ensureTrailingSlash())
            .client(get())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    single<DeepSeekApi> { get<Retrofit>().create(DeepSeekApi::class.java) }
    single { SseStreamParser(get()) }
}

val repositoryModule = module {
    single<ChatRepository> {
        ChatRepositoryImpl(
            database = get(),
            sessionDao = get(),
            messageDao = get(),
            deepSeekApi = get(),
            sseStreamParser = get(),
            activeSessionPreferences = get(),
            json = get()
        )
    }
}

val useCaseModule = module {
    factory { SendMessageUseCase(get()) }
    factory { ObserveMessagesUseCase(get()) }
    factory { ObserveSessionsUseCase(get()) }
    factory { CreateSessionUseCase(get()) }
    factory { DeleteSessionUseCase(get()) }
    factory { SetActiveSessionUseCase(get()) }
    factory { SetSessionSystemPromptUseCase(get()) }
    factory { SetSessionContextCompressionEnabledUseCase(get()) }
    factory { RunContextSummarizationIfNeededUseCase(get()) }
    factory { GetActiveSessionUseCase(get()) }
}

val viewModelModule = module {
    viewModel {
        ChatViewModel(
            sendMessageUseCase = get(),
            observeMessagesUseCase = get(),
            observeSessionsUseCase = get(),
            createSessionUseCase = get(),
            deleteSessionUseCase = get(),
            setActiveSessionUseCase = get(),
            setSessionSystemPromptUseCase = get(),
            setSessionContextCompressionEnabledUseCase = get(),
            runContextSummarizationIfNeededUseCase = get(),
            getActiveSessionUseCase = get()
        )
    }
}

private fun String.ensureTrailingSlash(): String {
    return if (endsWith('/')) this else "$this/"
}

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE chat_sessions ADD COLUMN systemPrompt TEXT")
    }
}

private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE chat_messages ADD COLUMN promptTokens INTEGER")
        db.execSQL("ALTER TABLE chat_messages ADD COLUMN promptCacheHitTokens INTEGER")
        db.execSQL("ALTER TABLE chat_messages ADD COLUMN promptCacheMissTokens INTEGER")
        db.execSQL("ALTER TABLE chat_messages ADD COLUMN completionTokens INTEGER")
    }
}

private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE chat_messages ADD COLUMN totalTokens INTEGER")
    }
}

private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE chat_sessions " +
                "ADD COLUMN contextCompressionEnabled INTEGER NOT NULL DEFAULT 0"
        )
        db.execSQL("ALTER TABLE chat_sessions ADD COLUMN contextSummary TEXT")
        db.execSQL(
            "ALTER TABLE chat_sessions " +
                "ADD COLUMN summarizedMessagesCount INTEGER NOT NULL DEFAULT 0"
        )
    }
}

private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE chat_messages " +
                "ADD COLUMN compressionState TEXT NOT NULL DEFAULT 'ACTIVE'"
        )
    }
}

private val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE chat_sessions " +
                "ADD COLUMN isContextSummarizationInProgress INTEGER NOT NULL DEFAULT 0"
        )
    }
}
