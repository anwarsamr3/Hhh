package com.example.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class IPTVRepository(context: Context) {

    private val db = IPTVDatabase.getDatabase(context)
    private val dao = db.iptvDao()
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // --- Core Exposures ---
    val playlists: Flow<List<IPTVPlaylist>> = dao.getAllPlaylistsFlow()
    val selectedPlaylist: Flow<IPTVPlaylist?> = dao.getSelectedPlaylistFlow()

    fun getCategoriesFlow(playlistId: Int, type: String): Flow<List<IPTVCategory>> {
        return dao.getCategoriesFlow(playlistId, type).flowOn(Dispatchers.IO)
    }

    fun getChannelsFlow(playlistId: Int, type: String, categoryId: String): Flow<List<IPTVChannel>> {
        return dao.getChannelsFlow(playlistId, type, categoryId).flowOn(Dispatchers.IO)
    }

    fun searchChannelsFlow(playlistId: Int, type: String, query: String): Flow<List<IPTVChannel>> {
        return dao.searchChannelsFlow(playlistId, type, "%$query%").flowOn(Dispatchers.IO)
    }

    fun getFavoritesFlow(playlistId: Int): Flow<List<IPTVChannel>> {
        return dao.getFavoritesFlow(playlistId).flowOn(Dispatchers.IO)
    }

    fun getEPGForChannelFlow(playlistId: Int, channelId: String, currentTime: Long): Flow<List<EPGProgram>> {
        return dao.getEPGForChannelFlow(playlistId, channelId, currentTime).flowOn(Dispatchers.IO)
    }

    suspend fun getSelectedPlaylistDirect(): IPTVPlaylist? = withContext(Dispatchers.IO) {
        dao.getSelectedPlaylist()
    }

    suspend fun getActiveProgramForChannel(playlistId: Int, channelId: String): EPGProgram? = withContext(Dispatchers.IO) {
        dao.getActiveProgramForChannel(playlistId, channelId, System.currentTimeMillis())
    }

    suspend fun selectPlaylist(playlistId: Int) = withContext(Dispatchers.IO) {
        dao.setSelectedPlaylist(playlistId)
    }

    suspend fun deletePlaylist(playlist: IPTVPlaylist) = withContext(Dispatchers.IO) {
        dao.deletePlaylist(playlist)
        dao.deleteCategoriesByPlaylist(playlist.id)
        dao.deleteChannelsByPlaylist(playlist.id)
        dao.deleteEPGByPlaylist(playlist.id)
    }

    suspend fun toggleFavorite(channelId: String, isFav: Boolean) = withContext(Dispatchers.IO) {
        dao.updateFavoriteStatus(channelId, isFav)
    }

    /**
     * Enters a new Playlist/Xtream Account into the system
     */
    suspend fun addPlaylist(
        name: String,
        type: String, // "M3U" or "XTREAM"
        playlistUrl: String = "",
        username: String = "",
        password: String = "",
        serverUrl: String = ""
    ): Result<IPTVPlaylist> = withContext(Dispatchers.IO) {
        try {
            // Securely encrypt sensitive fields
            val encryptedPassword = if (password.isNotEmpty()) SecurityUtils.encrypt(password) else ""
            val encryptedUrl = if (playlistUrl.isNotEmpty()) SecurityUtils.encrypt(playlistUrl) else ""
            val encryptedServer = if (serverUrl.isNotEmpty()) SecurityUtils.encrypt(serverUrl) else ""

            val playlist = IPTVPlaylist(
                name = name,
                type = type,
                playlistUrl = encryptedUrl,
                username = username,
                password = encryptedPassword,
                serverUrl = encryptedServer,
                lastUpdated = System.currentTimeMillis()
            )

            val newId = dao.insertPlaylist(playlist)
            val insertedPlaylist = playlist.copy(id = newId.toInt())
            
            // Set as selected automatically
            dao.setSelectedPlaylist(newId.toInt())

            // Fetch list content
            refreshPlaylist(newId.toInt())

            Result.success(insertedPlaylist)
        } catch (e: Exception) {
            Log.e("IPTVRepository", "Error adding playlist", e)
            Result.failure(e)
        }
    }

    /**
     * Refresh Channel Listings, Categories, and EPG for a given playlist
     */
    suspend fun refreshPlaylist(playlistId: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val allPlaylists = dao.getAllPlaylists()
            val playlist = allPlaylists.find { it.id == playlistId } 
                ?: return@withContext Result.failure(Exception("Playlist not found"))

            // Clear old cached metadata if any
            dao.deleteCategoriesByPlaylist(playlistId)
            dao.deleteChannelsByPlaylist(playlistId)
            dao.deleteEPGByPlaylist(playlistId)

            val decryptedPassword = SecurityUtils.decrypt(playlist.password)
            val decryptedUrl = SecurityUtils.decrypt(playlist.playlistUrl)
            val decryptedServer = SecurityUtils.decrypt(playlist.serverUrl)

            if (playlist.type == "XTREAM") {
                refreshXtream(playlistId, decryptedServer, playlist.username, decryptedPassword)
            } else {
                refreshM3U(playlistId, decryptedUrl)
            }

            // Sync update time
            dao.updatePlaylist(playlist.copy(lastUpdated = System.currentTimeMillis()))
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("IPTVRepository", "Error refreshing playlist $playlistId", e)
            Result.failure(e)
        }
    }

    /**
     * Parse and Sync M3U Playlists over Network
     */
    private suspend fun refreshM3U(playlistId: Int, url: String) {
        val request = Request.Builder().url(url).build()
        
        val parsedCategories = mutableSetOf<String>()
        val parsedChannels = mutableListOf<IPTVChannel>()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Failed to fetch M3U playlist. Status: ${response.code}")
            
            val stream = response.body?.byteStream() ?: throw Exception("M3U body is empty")
            val reader = BufferedReader(InputStreamReader(stream))
            
            var line: String? = reader.readLine()
            if (line == null || !line.trim().startsWith("#EXTM3U")) {
                throw Exception("Invalid M3U file format")
            }

            var currentChannelMeta: IPTVChannelMeta? = null
            var channelIndex = 1

            while (reader.readLine().also { line = it } != null) {
                val currentLine = line!!.trim()
                if (currentLine.isEmpty()) continue

                if (currentLine.startsWith("#EXTINF:")) {
                    currentChannelMeta = parseM3ULine(currentLine)
                } else if (!currentLine.startsWith("#")) {
                    // This is the channel streaming URL
                    if (currentChannelMeta != null) {
                        val categoryName = currentChannelMeta.categoryName.ifEmpty { "Other Live TV" }
                        parsedCategories.add(categoryName)

                        val channelId = "${playlistId}_LIVE_${currentChannelMeta.tvgId.ifEmpty { currentChannelMeta.name }}_${channelIndex++}"
                        
                        val isVideo = currentLine.contains(".mp4") || currentLine.contains(".mkv") || currentLine.contains(".avi")
                        val isSeries = currentLine.contains("series")
                        val streamType = if (isVideo) "MOVIE" else if (isSeries) "SERIES" else "LIVE"

                        parsedChannels.add(
                            IPTVChannel(
                                id = channelId,
                                playlistId = playlistId,
                                categoryId = categoryName.lowercase().replace(" ", "_"),
                                streamId = channelId,
                                name = currentChannelMeta.name,
                                logoUrl = currentChannelMeta.logoUrl,
                                streamUrl = currentLine,
                                type = streamType,
                                num = channelIndex
                            )
                        )
                        currentChannelMeta = null
                    }
                }
            }
        }

        // Save categories in database
        val categoriesList = parsedCategories.map { name ->
            val catId = name.lowercase().replace(" ", "_")
            IPTVCategory(
                id = "${playlistId}_${catId}_LIVE",
                playlistId = playlistId,
                categoryId = catId,
                name = name,
                type = "LIVE"
            )
        }
        dao.insertCategories(categoriesList)

        // Save channels in chunks of 500
        parsedChannels.chunked(500).forEach { chunk ->
            dao.insertChannels(chunk)
        }

        // Generate synthetic EPG so the user gets instant feedback
        generateSyntheticEPG(playlistId, parsedChannels.filter { it.type == "LIVE" })
    }

    /**
     * Regex M3U ExtInf Header Parser
     */
    private fun parseM3ULine(line: String): IPTVChannelMeta {
        var tvgId = ""
        var logoUrl = ""
        var categoryName = "Live TV"
        var channelName = ""

        val tvgIdRegex = """tvg-id=["']([^"']*)["']""".toRegex()
        val logoRegex = """tvg-logo=["']([^"']*)["']""".toRegex()
        val groupRegex = """group-title=["']([^"']*)["']""".toRegex()

        tvgIdRegex.find(line)?.let { tvgId = it.groupValues[1] }
        logoRegex.find(line)?.let { logoUrl = it.groupValues[1] }
        groupRegex.find(line)?.let { categoryName = it.groupValues[1] }

        val commaIndex = line.lastIndexOf(",")
        if (commaIndex != -1 && commaIndex < line.length - 1) {
            channelName = line.substring(commaIndex + 1).trim()
        }

        if (channelName.isEmpty()) {
            channelName = "Unknown Channel"
        }

        return IPTVChannelMeta(tvgId, channelName, logoUrl, categoryName)
    }

    private data class IPTVChannelMeta(
        val tvgId: String,
        val name: String,
        val logoUrl: String,
        val categoryName: String
    )

    /**
     * Parse and Sync Xtream Codes API
     */
    private suspend fun refreshXtream(playlistId: Int, serverUrl: String, user: String, pass: String) {
        val baseUrl = serverUrl.trimEnd('/')

        // Fetch Categories
        val liveCats = fetchXtreamCategories(playlistId, baseUrl, user, pass, "get_live_categories", "LIVE")
        val vodCats = fetchXtreamCategories(playlistId, baseUrl, user, pass, "get_vod_categories", "MOVIE")
        val seriesCats = fetchXtreamCategories(playlistId, baseUrl, user, pass, "get_series_categories", "SERIES")

        val allCategories = liveCats + vodCats + seriesCats
        dao.insertCategories(allCategories)

        // Fetch Live Channels
        val liveChannels = fetchXtreamStreams(playlistId, baseUrl, user, pass, "get_live_streams", "LIVE")
        dao.insertChannels(liveChannels)

        // Fetch Movies
        val movieChannels = fetchXtreamStreams(playlistId, baseUrl, user, pass, "get_vod_streams", "MOVIE")
        dao.insertChannels(movieChannels)

        // Fetch Series
        val seriesChannels = fetchXtreamStreams(playlistId, baseUrl, user, pass, "get_series_streams", "SERIES")
        dao.insertChannels(seriesChannels)

        // Generate synthetic EPG Programs for LIVE TV channels
        generateSyntheticEPG(playlistId, liveChannels)
    }

    private fun fetchXtreamCategories(
        playlistId: Int,
        baseUrl: String,
        user: String,
        pass: String,
        action: String,
        type: String
    ): List<IPTVCategory> {
        val url = "$baseUrl/player_api.php?username=$user&password=$pass&action=$action"
        val request = Request.Builder().url(url).build()

        val list = mutableListOf<IPTVCategory>()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()

                val jsonArray = JSONArray(body)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val catId = obj.getString("category_id")
                    val catName = obj.getString("category_name")
                    list.add(
                        IPTVCategory(
                            id = "${playlistId}_${catId}_$type",
                            playlistId = playlistId,
                            categoryId = catId,
                            name = catName,
                            type = type
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("IPTVRepository", "Error fetching Xtream categories for action $action", e)
        }
        return list
    }

    private fun fetchXtreamStreams(
        playlistId: Int,
        baseUrl: String,
        user: String,
        pass: String,
        action: String,
        type: String
    ): List<IPTVChannel> {
        val url = "$baseUrl/player_api.php?username=$user&password=$pass&action=$action"
        val request = Request.Builder().url(url).build()

        val list = mutableListOf<IPTVChannel>()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()

                val jsonArray = JSONArray(body)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val streamId = obj.optString("stream_id", obj.optString("series_id", ""))
                    if (streamId.isEmpty()) continue

                    val name = obj.optString("name", obj.optString("title", "Unknown Stream"))
                    val logo = obj.optString("stream_icon", obj.optString("cover", ""))
                    val categoryId = obj.optString("category_id", "")
                    val num = obj.optInt("num", i + 1)
                    val ext = obj.optString("container_extension", "mp4")

                    // Build Stream URL natively for playback
                    val streamUrl = when (type) {
                        "LIVE" -> "$baseUrl/live/$user/$pass/$streamId.ts"
                        "MOVIE" -> "$baseUrl/movie/$user/$pass/$streamId.$ext"
                        else -> "$baseUrl/series/$user/$pass/$streamId.$ext" // Series episodes stream template
                    }

                    list.add(
                        IPTVChannel(
                            id = "${playlistId}_${type}_$streamId",
                            playlistId = playlistId,
                            categoryId = categoryId,
                            streamId = streamId,
                            name = name,
                            logoUrl = logo,
                            streamUrl = streamUrl,
                            type = type,
                            num = num,
                            containerExtension = ext
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("IPTVRepository", "Error fetching Xtream streams for action $action", e)
        }
        return list
    }

    /**
     * Synthesise continuous local electronic programming guides (EPG)
     * so that the EPG guide can show programs anytime, anywhere without relying on external fragile XML feeds.
     */
    private suspend fun generateSyntheticEPG(playlistId: Int, liveChannels: List<IPTVChannel>) {
        val programs = mutableListOf<EPGProgram>()
        val startOfDay = System.currentTimeMillis() - (12 * 60 * 60 * 1000) // 12 hours ago
        val oneHour = 60 * 60 * 1000L

        // Typical program lists in Arabic & English
        val arabicShows = listOf(
            Pair("أخبار الساعة والتحليلات اليومية", "نشرة مفصلة وافية عن الأحداث السياسية والاجتماعية والاقتصادية المحلية والدولية."),
            Pair("حديث المساء الثقافي والمجتمعي", "حوار ترفيهي ثقافي يستعرض مستجدات الأنشطة الفنية وثقافة المجتمع العصري."),
            Pair("مسلسل الدراما والغموض الشيق", "الموسم الجديد من قصة درامية معقدة تكشف العديد من الأسرار الغامضة."),
            Pair("على الصدارة الكروية - الاستوديو التحليلي", "تحليل فني عميق لمباريات كروية بمشاركة نخبة من المعلقين والنجوم من مختلف الملاعب."),
            Pair("شريط الطبيعة والقرية الوثائقي", "رحلة بصرية تأخذنا إلى أعماق المحيطات والغابات الاستوائية والقرى النائية الساحرة.")
        )

        val englishShows = listOf(
            Pair("World News Bulletin LIVE", "Providing major highlights and in-depth political, socio-economic analysis of global events."),
            Pair("Midnight Cinema Special", "An award-winning vintage film presentation selected by our expert film curators."),
            Pair("Championship League Live Show", "Special visual studio live coverage of major games, lineups, and tactician interviews."),
            Pair("Wild Kingdom Documentary Tour", "Exploratory look into rare species, survival adaptations and wilderness landscapes."),
            Pair("Startup Hub & Innovation Insights", "Interactive talks with global visionary tech founders and future paradigm shapers.")
        )

        for (channel in liveChannels) {
            // Predictable offset per channel so schedules are scattered but persistent
            val hash = Math.abs(channel.name.hashCode())
            val playlistSelector = if (hash % 2 == 0) arabicShows else englishShows

            for (h in 0..48) { // 48 hours of schedule
                val start = startOfDay + (h * oneHour) + (hash % 10 * 6 * 1000) // slight offset
                val end = start + oneHour
                val itemIndex = (h + hash) % playlistSelector.size
                val (title, info) = playlistSelector[itemIndex]

                val programId = "${playlistId}_${channel.id}_$start"
                programs.add(
                    EPGProgram(
                        id = programId,
                        playlistId = playlistId,
                        channelId = channel.id,
                        title = title,
                        description = info,
                        startTimestamp = start,
                        endTimestamp = end
                    )
                )
            }
        }

        dao.insertEPGPrograms(programs)
        dao.deleteExpiredEPG(System.currentTimeMillis() - (24 * 60 * 60 * 1000))
    }

    /**
     * Seeds highly-curated free legal streaming playlists on first boot
     * so that the user is immediately greeted by a responsive and functional interface.
     */
    suspend fun seedMockPlaylistsIfEmpty() = withContext(Dispatchers.IO) {
        val existing = dao.getAllPlaylists()
        if (existing.isEmpty()) {
            val dbPlaylistId = dao.insertPlaylist(
                IPTVPlaylist(
                    name = "باقة القنوات المجانية الافتراضية (Free Sports & Docs)",
                    type = "M3U",
                    playlistUrl = SecurityUtils.encrypt("local_mock_stream_url"),
                    lastUpdated = System.currentTimeMillis(),
                    isSelected = true
                )
            ).toInt()

            // Seed Categories
            val mockCategories = listOf(
                IPTVCategory("${dbPlaylistId}_sports_live", dbPlaylistId, "sports", "رياضة وألعاب (Sports)", "LIVE"),
                IPTVCategory("${dbPlaylistId}_docs_live", dbPlaylistId, "docs", "وثائقيات وطبيعة (Documentaries)", "LIVE"),
                IPTVCategory("${dbPlaylistId}_news_live", dbPlaylistId, "news", "أخبار عالمية (News Channels)", "LIVE"),
                IPTVCategory("${dbPlaylistId}_cinema_movie", dbPlaylistId, "cinema", "أفلام ومسرحيات (Movies VOD)", "MOVIE"),
                IPTVCategory("${dbPlaylistId}_scifi_series", dbPlaylistId, "scifi", "مسلسلات علمية وشبابية (Sci-Fi TV)", "SERIES")
            )
            dao.insertCategories(mockCategories)

            // Seed Public High Quality legal streams
            val mockChannels = listOf(
                // Live Sports
                IPTVChannel(
                    id = "${dbPlaylistId}_LIVE_redbull",
                    playlistId = dbPlaylistId,
                    categoryId = "sports",
                    streamId = "redbull",
                    name = "قناة ريد بول الرياضية (Red Bull TV Sports)",
                    logoUrl = "https://img.youtube.com/vi/gEV9_lU8V8c/0.jpg",
                    streamUrl = "https://edge.api.brightcove.com/playback/v1/accounts/1660653140001/videos/ref:6108151241001/master.m3u8?fastly_token=NjE0MGVjYzRfYTM0YjUzYTc0MTNiZTc4MWVkOGEzMmVkNDE2YzhmY2IwYWJkYWU4ZDA1ZDg3N2FiZGYwYmZkOTBlNjg5MTNiOQ==",
                    type = "LIVE",
                    num = 1
                ),
                IPTVChannel(
                    id = "${dbPlaylistId}_LIVE_nasa",
                    playlistId = dbPlaylistId,
                    categoryId = "docs",
                    streamId = "nasa",
                    name = "تلفزيون ناسا الدولي (NASA TV LIVE)",
                    logoUrl = "https://www.nasa.gov/wp-content/themes/nasa/assets/images/nasa-logo.svg",
                    streamUrl = "https://nasatv-lh.akamaihd.net/i/NASA-NTV1_1@312812/index_3200_av-b.m3u8",
                    type = "LIVE",
                    num = 2
                ),
                IPTVChannel(
                    id = "${dbPlaylistId}_LIVE_france24_ar",
                    playlistId = dbPlaylistId,
                    categoryId = "news",
                    streamId = "france24_ar",
                    name = "فرانس 24 العربية (France 24 Arabic)",
                    logoUrl = "https://upload.wikimedia.org/wikipedia/commons/e/e0/France24_ar.svg",
                    streamUrl = "https://static.france24.com/live/F24_AR_G_GLOBAL/live_ar.m3u8",
                    type = "LIVE",
                    num = 3
                ),
                IPTVChannel(
                    id = "${dbPlaylistId}_LIVE_france24_en",
                    playlistId = dbPlaylistId,
                    categoryId = "news",
                    streamId = "france24_en",
                    name = "فرانس 24 الإنجليزية (France 24 English)",
                    logoUrl = "https://upload.wikimedia.org/wikipedia/commons/0/07/F24_English.svg",
                    streamUrl = "https://static.france24.com/live/F24_EN_LO_GLOBAL/live_en.m3u8",
                    type = "LIVE",
                    num = 4
                ),
                // Movies (VOD - Blender films)
                IPTVChannel(
                    id = "${dbPlaylistId}_MOVIE_bbb",
                    playlistId = dbPlaylistId,
                    categoryId = "cinema",
                    streamId = "bbb",
                    name = "فيلم الأرنب الكبير (Big Buck Bunny - Movie)",
                    logoUrl = "https://upload.wikimedia.org/wikipedia/commons/c/c5/Big_Buck_Bunny_Main_Poster.jpg",
                    streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                    type = "MOVIE",
                    num = 5,
                    description = "مغامرة فكاهية لسينما الرسوم المتحركة في غابة سحرية تواجه فيها الشخصيات أعداء صغارًا بمقالب ظريفة ومضحكة."
                ),
                IPTVChannel(
                    id = "${dbPlaylistId}_MOVIE_sintel",
                    playlistId = dbPlaylistId,
                    categoryId = "cinema",
                    streamId = "sintel",
                    name = "فيلم الخيال سينتيل (Sintel - Action)",
                    logoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/c/cf/Sintel_poster_cropped.jpg/800px-Sintel_poster_cropped.jpg",
                    streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
                    type = "MOVIE",
                    num = 6,
                    description = "رحلة بطولية مشوقة لفتاة تبحث عن تنينها الصغير المختطف في عوالم مغامرات ساحرة ومستويات خيال خلابة."
                ),
                IPTVChannel(
                    id = "${dbPlaylistId}_MOVIE_tears",
                    playlistId = dbPlaylistId,
                    categoryId = "cinema",
                    streamId = "tears",
                    name = "فيلم الخيال والعملاق (Tears of Steel)",
                    logoUrl = "https://upload.wikimedia.org/wikipedia/commons/a/a2/Tears_of_Steel_poster.jpg",
                    streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
                    type = "MOVIE",
                    num = 7,
                    description = "خيال علمي مستقبلي في مدينة ممتلئة بالروبوتات الضخمة والمركبات الفضائية السريعة بمؤثرات سينمائية مبهرة."
                ),
                // Series (Sci-Fi / Documentaries)
                IPTVChannel(
                    id = "${dbPlaylistId}_SERIES_elephant_1",
                    playlistId = dbPlaylistId,
                    categoryId = "scifi",
                    streamId = "elephant_1",
                    name = "حكايات الفيل البري - الحلقة الأولى (Elephant's Dream - Ep 1)",
                    logoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/c/ca/Elephants_Dream_poster_cropped.jpg/800px-Elephants_Dream_poster_cropped.jpg",
                    streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                    type = "SERIES",
                    num = 8,
                    description = "الحلقة الافتتاحية للموسم الأول: نرى فيها تصميم المدينة الصناعية العجيبة ونظام التروس ومقالب الشخصيتين الرئيسيتين."
                ),
                IPTVChannel(
                    id = "${dbPlaylistId}_SERIES_subaru_2",
                    playlistId = dbPlaylistId,
                    categoryId = "scifi",
                    streamId = "subaru_2",
                    name = "شريط حكايات الترحال - الحلقة الثانية (Travel Stories - Ep 2)",
                    logoUrl = "https://img.youtube.com/vi/q9mN83zR02o/0.jpg",
                    streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/SubaruOutbackOnStreetAndDirt.mp4",
                    type = "SERIES",
                    num = 9,
                    description = "الحلقة الثانية: رحلات استكشافية للبراري باستخدام سيارات دفع رباعي ومغامرات التخييم في الهواء الطلق."
                )
            )
            dao.insertChannels(mockChannels)

            // Generate synthetic EPG
            generateSyntheticEPG(dbPlaylistId, mockChannels.filter { it.type == "LIVE" })
        }
    }
}
