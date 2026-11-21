package com.example.ui.util

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppLanguage(
    val code: String,
    val displayName: String,
    val shortBadge: String
) {
    ENGLISH("EN", "English", "EN"),
    HINDI("HI", "हिंदी (Hindi)", "हिंदी");

    companion object {
        fun fromCode(code: String?): AppLanguage {
            return entries.find { it.code.equals(code, ignoreCase = true) } ?: ENGLISH
        }
    }
}

enum class ReminderLanguage(
    val code: String,
    val displayName: String,
    val shortLabel: String
) {
    HINDI("HI", "हिंदी (Hindi)", "हिंदी"),
    ENGLISH("EN", "English", "English"),
    BILINGUAL("HI_EN", "हिंदी + English (Both)", "हिंदी + EN");

    companion object {
        fun fromCode(code: String?): ReminderLanguage {
            return entries.find { it.code.equals(code, ignoreCase = true) } ?: HINDI
        }
    }
}

/**
 * Global, reactive manager for App UI Language (English / Hindi) and
 * WhatsApp Vaccine Reminder Language (Hindi / English / Bilingual).
 * Persists preferences in SharedPreferences without altering the Room database schema.
 */
object AppLanguageManager {
    private const val PREFS_NAME = "clinic_language_prefs"
    private const val KEY_APP_LANGUAGE = "app_language"
    private const val KEY_WHATSAPP_LANGUAGE = "whatsapp_reminder_language"

    private val _appLanguageFlow = MutableStateFlow(AppLanguage.ENGLISH)
    val appLanguageFlow: StateFlow<AppLanguage> = _appLanguageFlow.asStateFlow()

    private val _whatsappLanguageFlow = MutableStateFlow(ReminderLanguage.HINDI)
    val whatsappLanguageFlow: StateFlow<ReminderLanguage> = _whatsappLanguageFlow.asStateFlow()

    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedAppLang = prefs.getString(KEY_APP_LANGUAGE, AppLanguage.ENGLISH.code)
        val savedWaLang = prefs.getString(KEY_WHATSAPP_LANGUAGE, ReminderLanguage.HINDI.code)
        _appLanguageFlow.value = AppLanguage.fromCode(savedAppLang)
        _whatsappLanguageFlow.value = ReminderLanguage.fromCode(savedWaLang)
        isInitialized = true
    }

    val currentAppLanguage: AppLanguage
        get() = _appLanguageFlow.value

    val currentWhatsAppLanguage: ReminderLanguage
        get() = _whatsappLanguageFlow.value

    fun setAppLanguage(context: Context, language: AppLanguage) {
        _appLanguageFlow.value = language
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_APP_LANGUAGE, language.code).apply()
        // When user switches app language to Hindi, also default WhatsApp reminders to Hindi
        if (language == AppLanguage.HINDI && _whatsappLanguageFlow.value == ReminderLanguage.ENGLISH) {
            setWhatsAppReminderLanguage(context, ReminderLanguage.HINDI)
        }
    }

    fun toggleAppLanguage(context: Context): AppLanguage {
        val next = if (_appLanguageFlow.value == AppLanguage.ENGLISH) AppLanguage.HINDI else AppLanguage.ENGLISH
        setAppLanguage(context, next)
        return next
    }

    fun setWhatsAppReminderLanguage(context: Context, language: ReminderLanguage) {
        _whatsappLanguageFlow.value = language
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_WHATSAPP_LANGUAGE, language.code).apply()
    }

    /**
     * Non-composable string translation helper based on currentAppLanguage.
     */
    fun text(en: String, hi: String): String {
        return if (_appLanguageFlow.value == AppLanguage.HINDI) hi else en
    }
}

/**
 * Reactive Composable helper that returns [hi] when Hindi language is selected, or [en] when English is selected.
 */
@Composable
fun tr(en: String, hi: String): String {
    val lang by AppLanguageManager.appLanguageFlow.collectAsState()
    return if (lang == AppLanguage.HINDI) hi else en
}
