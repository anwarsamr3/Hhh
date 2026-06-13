package com.example.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SettingsManager(context: Context) {

    private val sharedPrefs = context.getSharedPreferences("smarter_iptv_prefs", Context.MODE_PRIVATE)

    private val _theme = MutableStateFlow(getSavedTheme())
    val theme: StateFlow<String> = _theme

    private val _lang = MutableStateFlow(getSavedLang())
    val lang: StateFlow<String> = _lang

    private val _autoUpdate = MutableStateFlow(getSavedAutoUpdate())
    val autoUpdate: StateFlow<Boolean> = _autoUpdate

    fun setTheme(newTheme: String) {
        sharedPrefs.edit().putString("key_theme", newTheme).apply()
        _theme.value = newTheme
    }

    fun setLang(newLang: String) {
        sharedPrefs.edit().putString("key_lang", newLang).apply()
        _lang.value = newLang
    }

    fun setAutoUpdate(enabled: Boolean) {
        sharedPrefs.edit().putBoolean("key_auto_update", enabled).apply()
        _autoUpdate.value = enabled
    }

    private fun getSavedTheme(): String {
        return sharedPrefs.getString("key_theme", "SLATE") ?: "SLATE"
    }

    private fun getSavedLang(): String {
        return sharedPrefs.getString("key_lang", "AR") ?: "AR"
    }

    private fun getSavedAutoUpdate(): Boolean {
        return sharedPrefs.getBoolean("key_auto_update", true)
    }

    // Standard translation mappings
    fun getTranslation(key: String, currentLang: String): String {
        val isAr = currentLang == "AR"
        return when (key) {
            "app_title" -> if (isAr) "سـمارتر IPTV الذكي" else "Smarter IPTV Pro"
            "welcome" -> if (isAr) "أهلاً بك في مشغل IPTV الذكي" else "Welcome to Smarter IPTV Player"
            "add_playlist" -> if (isAr) "إضافة قائمة قنوات" else "Add New Playlist"
            "playlist_name" -> if (isAr) "اسم قائمة القنوات" else "Playlist Name"
            "playlist_type" -> if (isAr) "نوع القائمة" else "Playlist Type"
            "m3u_url" -> if (isAr) "رابط ملف M3U" else "M3U Playlist URL"
            "xtream_server" -> if (isAr) "رابط خادم Xtream (Server URL)" else "Xtream Server URL"
            "username" -> if (isAr) "اسم المستخدم" else "Username"
            "password" -> if (isAr) "كلمة المرور" else "Password"
            "cancel" -> if (isAr) "إلغاء" else "Cancel"
            "save" -> if (isAr) "حفظ القائمة وتحميلها" else "Save & Fetch Playlist"
            "loading" -> if (isAr) "جاري التحميل والمزامنة..." else "Loading & Syncing..."
            "home" -> if (isAr) "الرئيسية" else "Home"
            "live_tv" -> if (isAr) "البث المباشر" else "Live TV"
            "movies" -> if (isAr) "الأفلام VOD" else "Movies VOD"
            "series" -> if (isAr) "المسلسلات" else "TV Series"
            "favorites" -> if (isAr) "المفضلة" else "Favorites"
            "settings" -> if (isAr) "الإعدادات" else "Settings"
            "all_channels" -> if (isAr) "كل القنوات" else "All Streams"
            "search_hint" -> if (isAr) "ابحث عن قنوات أو أفلام..." else "Search channels or movies..."
            "empty_favorites" -> if (isAr) "لا توجد عناصر في قائمة المفضلة بعد." else "No favorite items added yet."
            "epg_no_program" -> if (isAr) "لا يوجد برنامج مجدول حالياً" else "No scheduled program right now"
            "epg_guide" -> if (isAr) "دليل البرامج الإلكتروني EPG" else "Electronic Program Guide"
            "epg_details" -> if (isAr) "تفاصيل العرض" else "Program Info"
            "fullscreen" -> if (isAr) "كامل الشاشة" else "Toggle Fullscreen"
            "theme_select" -> if (isAr) "تخصيص ثيم التطبيق" else "App Theme Customization"
            "language_select" -> if (isAr) "لغة الواجهة" else "Interface Language"
            "auto_update_label" -> if (isAr) "تحديث تلقائي للقنوات (يومياً)" else "Auto-update streams (Daily)"
            "playlist_details" -> if (isAr) "تفاصيل قائمة القنوات" else "Playlist Details"
            "delete_playlist" -> if (isAr) "حذف القائمة" else "Delete Playlist"
            "delete_confirm" -> if (isAr) "هل أنت متأكد من حذف هذه قائمة القنوات بالكامل؟" else "Are you sure you want to delete this playlist?"
            "add_failed" -> if (isAr) "فشل التحميل، يرجى التحقق من الرابط والاتصال" else "Failed to load. Please check link & internet."
            "channels_count" -> if (isAr) "قناة" else "channels"
            "movies_count" -> if (isAr) "فيلم" else "movies"
            "series_count" -> if (isAr) "مسلسل" else "series"
            "unassigned" -> if (isAr) "غير مصنف" else "Uncategorized"
            "zap_list" -> if (isAr) "قائمة التنقل السريع" else "Zap List"
            "play_stream" -> if (isAr) "تشغيل الآن" else "Play Stream"
            "playback_ratio" -> if (isAr) "أبعاد الفيديو" else "Aspect Ratio"
            "fit" -> if (isAr) "ملاءمة الشاشة" else "Fit"
            "fill" -> if (isAr) "تمديد (Fill)" else "Stretch (Fill)"
            "sixteen_nine" -> if (isAr) "أبعاد 16:9" else "16:9"
            "four_three" -> if (isAr) "أبعاد 4:3" else "4:3"
            "zoom" -> if (isAr) "تقريب (Zoom)" else "Zoom"
            else -> key
        }
    }
}
