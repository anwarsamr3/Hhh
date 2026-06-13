package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.*
import com.example.ui.IPTVPlayer
import com.example.ui.theme.IPTVTheme
import com.example.viewmodel.IPTVViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: IPTVViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeState by viewModel.currentTheme.collectAsState()
            val langState by viewModel.currentLang.collectAsState()

            IPTVTheme(themeState = themeState) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    IPTVScreen(viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IPTVScreen(viewModel: IPTVViewModel) {
    val currentLang by viewModel.currentLang.collectAsState()
    val currentTab by viewModel.currentTab.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val selectedPlaylist by viewModel.selectedPlaylist.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val loadError by viewModel.loadError.collectAsState()

    // Screen dimension checks
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    // Dialog trigger states
    var showAddPlaylist by remember { mutableStateOf(false) }
    var showPlaylistSelector by remember { mutableStateOf(false) }
    var playlistToDelete by remember { mutableStateOf<IPTVPlaylist?>(null) }

    // Navigation and screen rendering logic
    val activeChannel by viewModel.activeChannel.collectAsState()
    val activeCategoryChannels by viewModel.activeCategoryChannels.collectAsState()

    // Ensure we support RTL for Arabic
    val isRtl = currentLang == "AR"
    val layoutDirection = if (isRtl) androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr

    CompositionLocalProvider(
        androidx.compose.ui.platform.LocalLayoutDirection provides layoutDirection
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            
            // Primary scaffolding
            Scaffold(
                topBar = {
                    if (activeChannel == null) {
                        CenterAlignedTopAppBar(
                            title = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable { showPlaylistSelector = true }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlaylistPlay,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = selectedPlaylist?.name ?: viewModel.settingsManager.getTranslation("app_title", currentLang),
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.widthIn(max = 200.dp)
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        tint = Color.Gray
                                    )
                                }
                            },
                            actions = {
                                // Manual updates refresh trigger
                                if (selectedPlaylist != null) {
                                    IconButton(
                                        onClick = { viewModel.refreshSelectedPlaylist() },
                                        enabled = !isRefreshing
                                    ) {
                                        if (isRefreshing) {
                                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                        } else {
                                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }

                                IconButton(onClick = { showAddPlaylist = true }, modifier = Modifier.testTag("add_playlist_header_button")) {
                                    Icon(Icons.Default.Add, contentDescription = "Add playlist", tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                titleContentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.testTag("top_bar_dashboard")
                        )
                    }
                },
                bottomBar = {
                    // Render bottom navigation on standard vertical devices when not playing stream
                    if (!isLandscape && activeChannel == null) {
                        IPTVBottomNavigationBar(viewModel, currentTab, currentLang)
                    }
                }
            ) { paddingValues ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    // Render Navigation Rail on tablets or landscape views
                    if (isLandscape && activeChannel == null) {
                        IPTVNavigationRail(viewModel, currentTab, currentLang)
                        Divider(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(1.dp),
                            color = Color.Gray.copy(alpha = 0.2f)
                        )
                    }

                    // Content switcher container
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                    ) {
                        when (currentTab) {
                            "HOME" -> HomeDashboard(viewModel, currentLang, onSwitchPlaylist = { showPlaylistSelector = true })
                            "LIVE" -> ChannelsListScreen(viewModel, "LIVE", currentLang)
                            "MOVIE" -> ChannelsListScreen(viewModel, "MOVIE", currentLang)
                            "SERIES" -> ChannelsListScreen(viewModel, "SERIES", currentLang)
                            "FAVORITES" -> FavoritesScreen(viewModel, currentLang)
                            "SETTINGS" -> SettingsScreen(viewModel, currentLang, onDeletePlaylist = { playlistToDelete = it })
                        }
                    }
                }
            }

            // --- Error toast overlay ---
            loadError?.let { errorMsg ->
                Snackbar(
                    action = {
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text(if (currentLang == "AR") "موافق" else "OK", color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                ) {
                    Text(text = errorMsg)
                }
            }

            // --- Dialog Overlays ---
            if (showAddPlaylist) {
                AddPlaylistDialog(
                    viewModel = viewModel,
                    currentLang = currentLang,
                    onDismiss = { showAddPlaylist = false }
                )
            }

            if (showPlaylistSelector) {
                PlaylistSelectorDialog(
                    viewModel = viewModel,
                    currentLang = currentLang,
                    onDismiss = { showPlaylistSelector = false }
                )
            }

            playlistToDelete?.let { playlist ->
                AlertDialog(
                    onDismissRequest = { playlistToDelete = null },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.deletePlaylist(playlist)
                                playlistToDelete = null
                            }
                        ) {
                            Text(if (currentLang == "AR") "حذف القائمة" else "Delete", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { playlistToDelete = null }) {
                            Text(viewModel.settingsManager.getTranslation("cancel", currentLang))
                        }
                    },
                    title = { Text(viewModel.settingsManager.getTranslation("delete_playlist", currentLang)) },
                    text = { Text(viewModel.settingsManager.getTranslation("delete_confirm", currentLang)) },
                    icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                )
            }

            // --- IMMERSIVE VIDEO PLAYER OUTSIDE SCAFFOLD ---
            activeChannel?.let { playTarget ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    IPTVPlayer(
                        channel = playTarget,
                        categoryChannels = activeCategoryChannels,
                        isFavorite = playTarget.isFavorite,
                        settingsManager = viewModel.settingsManager,
                        onToggleFavorite = { viewModel.toggleFavorite(playTarget) },
                        onChannelSelected = { nextChan ->
                            viewModel.playChannel(nextChan, activeCategoryChannels)
                        },
                        onClose = { viewModel.closePlayer() }
                    )
                }
            }
        }
    }
}

// --- Navigation Composable Helpers ---
@Composable
fun IPTVBottomNavigationBar(viewModel: IPTVViewModel, currentTab: String, currentLang: String) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .testTag("bottom_nav_bar")
    ) {
        val tabs = listOf(
            Triple("HOME", Icons.Default.Dashboard, "home"),
            Triple("LIVE", Icons.Default.Tv, "live_tv"),
            Triple("MOVIE", Icons.Default.Movie, "movies"),
            Triple("SERIES", Icons.Default.VideoLibrary, "series"),
            Triple("FAVORITES", Icons.Default.Favorite, "favorites"),
            Triple("SETTINGS", Icons.Default.Settings, "settings")
        )

        tabs.forEach { (tab, icon, labelKey) ->
            NavigationBarItem(
                selected = currentTab == tab,
                onClick = { viewModel.selectTab(tab) },
                icon = { Icon(icon, contentDescription = null) },
                label = {
                    Text(
                        text = viewModel.settingsManager.getTranslation(labelKey, currentLang),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.Black,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = Color.Gray,
                    unselectedTextColor = Color.Gray
                ),
                modifier = Modifier.testTag("nav_item_$tab")
            )
        }
    }
}

@Composable
fun IPTVNavigationRail(viewModel: IPTVViewModel, currentTab: String, currentLang: String) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surface,
        header = {
            Icon(
                Icons.Default.SettingsInputAntenna,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(40.dp)
                    .padding(vertical = 4.dp)
            )
        },
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        val tabs = listOf(
            Triple("HOME", Icons.Default.Dashboard, "home"),
            Triple("LIVE", Icons.Default.Tv, "live_tv"),
            Triple("MOVIE", Icons.Default.Movie, "movies"),
            Triple("SERIES", Icons.Default.VideoLibrary, "series"),
            Triple("FAVORITES", Icons.Default.Favorite, "favorites"),
            Triple("SETTINGS", Icons.Default.Settings, "settings")
        )

        tabs.forEach { (tab, icon, labelKey) ->
            NavigationRailItem(
                selected = currentTab == tab,
                onClick = { viewModel.selectTab(tab) },
                icon = { Icon(icon, contentDescription = null) },
                label = {
                    Text(
                        text = viewModel.settingsManager.getTranslation(labelKey, currentLang),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = Color.Black,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = Color.Gray,
                    unselectedTextColor = Color.Gray
                )
            )
        }
    }
}

// --- Home Dashboard Screen ---
@Composable
fun HomeDashboard(
    viewModel: IPTVViewModel,
    currentLang: String,
    onSwitchPlaylist: () -> Unit
) {
    val selectedPlaylist by viewModel.selectedPlaylist.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    
    // Quick statistics (simulated/extracted aggregates from flows)
    val favoritesList: List<IPTVChannel> by viewModel.getFavoritesFlow(selectedPlaylist?.id ?: 0).collectAsState(initial = emptyList())
    
    // Scrolled details
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Welcoming card decoration with a clean, high-contrast gradient layout
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.secondary
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Column {
                Text(
                    text = viewModel.settingsManager.getTranslation("welcome", currentLang),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (selectedPlaylist != null) 
                        "قائمتك النشطة: ${selectedPlaylist?.name}" 
                    else 
                        "يرجى إضافة قائمة قنوات M3U أو حساب Xtream للبدء فوراً.",
                    fontSize = 12.sp,
                    color = Color.Black.copy(alpha = 0.8f)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Quick Statistics Grid
        Text(
            text = "إحصائيات قائمة القنوات",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DashboardStatCard(
                icon = Icons.Default.Tv,
                count = if (selectedPlaylist != null) "M3U+" else "0",
                label = viewModel.settingsManager.getTranslation("live_tv", currentLang),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            DashboardStatCard(
                icon = Icons.Default.Favorite,
                count = favoritesList.size.toString(),
                label = viewModel.settingsManager.getTranslation("favorites", currentLang),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Playlist Switcher Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "قوائـم القنوات النشطة والمزامنة",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            TextButton(onClick = onSwitchPlaylist) {
                Text(if (currentLang == "AR") "إدارة الحسابات" else "Manage", color = MaterialTheme.colorScheme.primary)
            }
        }

        if (playlists.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("لا توجد قوائم قنوات محفوظة حالياً.")
            }
        } else {
            playlists.forEach { pl ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { viewModel.selectPlaylist(pl.id) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (pl.isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        else MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = if (pl.isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    if (pl.isSelected) MaterialTheme.colorScheme.primary
                                    else Color.Gray.copy(alpha = 0.2f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (pl.type == "XTREAM") Icons.Default.VpnKey else Icons.Default.FileDownload,
                                contentDescription = null,
                                tint = if (pl.isSelected) Color.Black else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = pl.name,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "النوع: ${pl.type} • تم التحديث: منذ قليل",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }

                        if (pl.isSelected) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Active",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Security encryption notice banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "نظام تشفير آمن (Secure Encryption Engine)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "تم تشفير بيانات تسجيل الدخول وروابط الخادم محلياً باستخدام خوارزميات Base64-XOR لمنع تسريب بيانات اشتراكك.",
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
fun DashboardStatCard(
    icon: ImageVector,
    count: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = count, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Text(text = label, fontSize = 11.sp, color = Color.Gray)
        }
    }
}

// --- Dynamic Stream Channels List Screen ---
@Composable
fun ChannelsListScreen(
    viewModel: IPTVViewModel,
    streamType: String,
    currentLang: String
) {
    val categories by viewModel.categoriesFlow.collectAsState(initial = emptyList())
    val selectedCategory by viewModel.selectedCategoryId.collectAsState()
    val channelsList by viewModel.channelsFlow.collectAsState(initial = emptyList())
    val searchQuery by viewModel.searchQuery.collectAsState()

    // Auto load first category if none selected
    LaunchedEffect(categories) {
        if (selectedCategory.isEmpty() && categories.isNotEmpty()) {
            viewModel.selectCategory(categories.first().categoryId)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header Search field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            placeholder = {
                Text(
                    text = viewModel.settingsManager.getTranslation("search_hint", currentLang),
                    fontSize = 14.sp
                )
            },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
            trailingIcon = if (searchQuery.isNotEmpty()) {
                {
                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            } else null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .testTag("stream_search_input"),
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = MaterialTheme.colorScheme.primary
            )
        )

        if (categories.isEmpty() && searchQuery.isEmpty()) {
            // Empty state placeholder
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.SignalCellularConnectedNoInternet0Bar,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "لا توجد قنوات أو فئات متاحة في هذه القائمة.",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }
            }
        } else {
            // Category Slider Rows
            if (searchQuery.isEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(categories) { cat ->
                        val isSelected = cat.categoryId == selectedCategory
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.selectCategory(cat.categoryId) },
                            label = { Text(cat.name, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = Color.Black,
                                containerColor = MaterialTheme.colorScheme.surface,
                                labelColor = MaterialTheme.colorScheme.onSurface
                            ),
                            shape = RoundedCornerShape(16.dp)
                        )
                    }
                }
            }

            // Grid displaying streams
            if (channelsList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (currentLang == "AR") "جاري جلب القنوات للقسم المختار..." else "Sourcing streams data...")
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = if (streamType == "LIVE") 130.dp else 110.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .testTag("streams_grid"),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(channelsList) { chan ->
                        if (streamType == "LIVE") {
                            LiveChannelGridItem(
                                channel = chan,
                                viewModel = viewModel,
                                onClick = { viewModel.playChannel(chan, channelsList) }
                            )
                        } else {
                            MediaVODGridItem(
                                movie = chan,
                                viewModel = viewModel,
                                onClick = { viewModel.playChannel(chan, channelsList) }
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- Live View Grid Card Component ---
@Composable
fun LiveChannelGridItem(
    channel: IPTVChannel,
    viewModel: IPTVViewModel,
    onClick: () -> Unit
) {
    var activeProgram by remember { mutableStateOf<EPGProgram?>(null) }

    LaunchedEffect(channel.id) {
        activeProgram = viewModel.getActiveEPGForChannel(channel.id)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("channel_card_${channel.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.05f)),
                contentAlignment = Alignment.Center
            ) {
                if (channel.logoUrl.isNotEmpty()) {
                    AsyncImage(
                        model = channel.logoUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = channel.name,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Dynamic EPG Guide overview
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = activeProgram?.title ?: "برنامج عام مباشر",
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            // Linear timeline progress bar indicator
            if (activeProgram != null) {
                Spacer(modifier = Modifier.height(6.dp))
                val start = activeProgram!!.startTimestamp
                val end = activeProgram!!.endTimestamp
                val current = System.currentTimeMillis()
                val progress = if (end > start) {
                    ((current - start).toFloat() / (end - start).toFloat()).coerceIn(0f, 1f)
                } else 0.5f

                LinearProgressIndicator(
                    progress = progress,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Gray.copy(alpha = 0.2f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(CircleShape)
                )
            }
        }
    }
}

// --- Movie/Series poster Grid Card Component ---
@Composable
fun MediaVODGridItem(
    movie: IPTVChannel,
    viewModel: IPTVViewModel,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clickable(onClick = onClick)
            .testTag("vod_card_${movie.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(10.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (movie.logoUrl.isNotEmpty()) {
                AsyncImage(
                    model = movie.logoUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.DarkGray),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (movie.type == "MOVIE") Icons.Default.Movie else Icons.Default.VideoLibrary,
                        contentDescription = null,
                        tint = Color.LightGray,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            // Darkened footer label overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        )
                    )
                    .padding(8.dp)
            ) {
                Column {
                    Text(
                        text = movie.name,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "VOD . ${movie.containerExtension.uppercase()}",
                        fontSize = 8.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// --- Favorites Tab Screen ---
@Composable
fun FavoritesScreen(viewModel: IPTVViewModel, currentLang: String) {
    val selectedPlaylist by viewModel.selectedPlaylist.collectAsState()
    val favoritesList: List<IPTVChannel> by viewModel.getFavoritesFlow(selectedPlaylist?.id ?: 0).collectAsState(initial = emptyList())

    if (favoritesList.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.FavoriteBorder,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = viewModel.settingsManager.getTranslation("empty_favorites", currentLang),
                    fontSize = 14.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = viewModel.settingsManager.getTranslation("favorites", currentLang),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Adaptive(130.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(favoritesList) { chan ->
                    LiveChannelGridItem(
                        channel = chan,
                        viewModel = viewModel,
                        onClick = { viewModel.playChannel(chan, favoritesList) }
                    )
                }
            }
        }
    }
}

// --- Custom Configuration & Settings Screen ---
@Composable
fun SettingsScreen(
    viewModel: IPTVViewModel,
    currentLang: String,
    onDeletePlaylist: (IPTVPlaylist) -> Unit
) {
    val playlists by viewModel.playlists.collectAsState()
    val currentTheme by viewModel.currentTheme.collectAsState()
    val autoUpdate by viewModel.autoUpdate.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = viewModel.settingsManager.getTranslation("settings", currentLang),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Theme Customizer Selectors
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = viewModel.settingsManager.getTranslation("theme_select", currentLang),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeSelectButton("SLATE", "الكوني (Slate)", currentTheme, viewModel)
                    ThemeSelectButton("CYBER", "الذهبي (Cyber)", currentTheme, viewModel)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeSelectButton("OCEAN", "المحيط (Ocean)", currentTheme, viewModel)
                    ThemeSelectButton("LIGHT", "النهاري (Light)", currentTheme, viewModel)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Language Selectors
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = viewModel.settingsManager.getTranslation("language_select", currentLang),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(
                        selected = currentLang == "AR",
                        onClick = { viewModel.changeLanguage("AR") },
                        label = { Text("العربية") }
                    )
                    FilterChip(
                        selected = currentLang == "EN",
                        onClick = { viewModel.changeLanguage("EN") },
                        label = { Text("English") }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Auto check switch updates
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Sync, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = viewModel.settingsManager.getTranslation("auto_update_label", currentLang),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Switch(
                    checked = autoUpdate,
                    onCheckedChange = { viewModel.toggleAutoUpdate(it) }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Lists deletion controls
        Text(
            text = "إدارة ملفات الحسابات المضافة",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        playlists.forEach { pl ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(pl.name, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(pl.type, fontSize = 10.sp, color = Color.Gray)
                    }
                    IconButton(onClick = { onDeletePlaylist(pl) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
fun RowScope.ThemeSelectButton(
    themeTag: String,
    label: String,
    activeTheme: String,
    viewModel: IPTVViewModel
) {
    val isSelected = activeTheme == themeTag
    Button(
        onClick = { viewModel.changeTheme(themeTag) },
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.15f),
            contentColor = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurface
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.weight(1f)
    ) {
        Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

// --- Interactive Dialog for adding new resources ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPlaylistDialog(
    viewModel: IPTVViewModel,
    currentLang: String,
    onDismiss: () -> Unit
) {
    var listType by remember { mutableStateOf("M3U") } // M3U vs. XTREAM
    var name by remember { mutableStateOf("") }
    var m3uUrl by remember { mutableStateOf("") }
    
    // Xtream Codes attributes
    var serverUrl by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }

    var isError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .testTag("add_playlist_dialog")
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = viewModel.settingsManager.getTranslation("add_playlist", currentLang),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Dialog tab switches
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { listType = "M3U" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (listType == "M3U") MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.15f),
                            contentColor = if (listType == "M3U") Color.Black else MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("رابط M3U URL", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { listType = "XTREAM" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (listType == "XTREAM") MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.15f),
                            contentColor = if (listType == "XTREAM") Color.Black else MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("خادم Xtream API", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Name field
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(viewModel.settingsManager.getTranslation("playlist_name", currentLang)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .testTag("playlist_name_field"),
                    singleLine = true
                )

                if (listType == "M3U") {
                    // M3U Link
                    OutlinedTextField(
                        value = m3uUrl,
                        onValueChange = { m3uUrl = it },
                        label = { Text(viewModel.settingsManager.getTranslation("m3u_url", currentLang)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .testTag("m3u_field"),
                        singleLine = true
                    )
                } else {
                    // Xtream attributes
                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = { serverUrl = it },
                        label = { Text(viewModel.settingsManager.getTranslation("xtream_server", currentLang)) },
                        placeholder = { Text("http://example.com:8080") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .testTag("server_field"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = user,
                        onValueChange = { user = it },
                        label = { Text(viewModel.settingsManager.getTranslation("username", currentLang)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .testTag("username_field"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = pass,
                        onValueChange = { pass = it },
                        label = { Text(viewModel.settingsManager.getTranslation("password", currentLang)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .testTag("password_field"),
                        singleLine = true
                    )
                }

                if (isError) {
                    Text(
                        text = "الرجاء تعبئة كافة الحقول المطلوبة بشكل صحيح.",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bottom row button controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(viewModel.settingsManager.getTranslation("cancel", currentLang))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val valid = if (listType == "M3U") {
                                name.isNotEmpty() && m3uUrl.isNotEmpty()
                            } else {
                                name.isNotEmpty() && serverUrl.isNotEmpty() && user.isNotEmpty() && pass.isNotEmpty()
                            }

                            if (valid) {
                                viewModel.addNewPlaylist(
                                    name = name,
                                    type = listType,
                                    url = m3uUrl,
                                    user = user,
                                    pass = pass,
                                    server = serverUrl
                                )
                                onDismiss()
                            } else {
                                isError = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.testTag("save_playlist_button")
                    ) {
                        Text(
                            text = viewModel.settingsManager.getTranslation("save", currentLang),
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// --- Quick Playlist Selector Switch popup dialog ---
@Composable
fun PlaylistSelectorDialog(
    viewModel: IPTVViewModel,
    currentLang: String,
    onDismiss: () -> Unit
) {
    val playlists by viewModel.playlists.collectAsState()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "تبديل الحساب النشط",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    items(playlists.size) { index ->
                        val pl = playlists[index]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.selectPlaylist(pl.id)
                                    onDismiss()
                                }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (pl.isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (pl.isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = pl.name,
                                fontSize = 14.sp,
                                fontWeight = if (pl.isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (pl.isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Divider(color = Color.Gray.copy(alpha = 0.2f))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text(viewModel.settingsManager.getTranslation("cancel", currentLang))
                    }
                }
            }
        }
    }
}
