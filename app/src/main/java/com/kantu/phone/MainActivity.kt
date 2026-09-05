package com.kantu.phone

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val RequiredPermissions = arrayOf(
    Manifest.permission.CALL_PHONE,
    Manifest.permission.READ_CONTACTS,
)

internal val LocalCallGateway = staticCompositionLocalOf<CallGateway?> { null }

private object KantuColors {
    val PageBg = Color(0xFFF6F3EC)
    val Card = Color(0xFFFFFFFF)
    val Primary = Color(0xFF1B8F5A)
    val OnPrimary = Color.White
    val TextPrimary = Color(0xFF1C1C1C)
    val TextSecondary = Color(0xFF5C5C5C)
    val Danger = Color(0xFFD94A3A)
    val Hairline = Color(0xFFE6E1D8)
    val FieldStroke = Color(0xFFD7D1C7)
    val TabTrack = Color(0xFFEFEBE3)
    val Banner = Color(0xFFFFE082)
}

private val CardShape = RoundedCornerShape(20.dp)
private val KeyShape = RoundedCornerShape(20.dp)
private val PillShape = RoundedCornerShape(28.dp)

class MainActivity : ComponentActivity() {
    private lateinit var speech: SpeechController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        speech = SpeechController(this)
        setContent {
            KantuTheme {
                KantuApp(speech)
            }
        }
    }

    override fun onDestroy() {
        speech.shutdown()
        super.onDestroy()
    }
}

@Composable
internal fun KantuTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = androidx.compose.material3.lightColorScheme(
            primary = KantuColors.Primary,
            onPrimary = KantuColors.OnPrimary,
            background = KantuColors.PageBg,
            onBackground = KantuColors.TextPrimary,
            surface = KantuColors.Card,
            onSurface = KantuColors.TextPrimary,
            error = KantuColors.Danger,
            onError = Color.White,
        ),
        content = content,
    )
}

@Composable
private fun KantuApp(
    speech: VoiceAnnouncer,
    data: PhoneDataViewModel = viewModel(),
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var permissionsGranted by remember { mutableStateOf(hasAllPermissions(context)) }
    var permissionDenied by remember { mutableStateOf(false) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionsGranted = hasAllPermissions(context)
                data.permissionsChanged()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val chineseUnavailable by speech.chineseUnavailable.collectAsState()
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        permissionsGranted = RequiredPermissions.all { result[it] == true }
        permissionDenied = !permissionsGranted
        val permissionNames = mapOf(
            Manifest.permission.CALL_PHONE to "拨打电话",
            Manifest.permission.READ_CONTACTS to "通讯录",
            Manifest.permission.READ_CALL_LOG to "通话记录",
        )
        val outcome = RequiredPermissions.joinToString("，") { permission ->
            "${permissionNames.getValue(permission)}权限${if (result[permission] == true) "已允许" else "没有允许"}"
        }
        speech.speak(outcome)
        data.permissionsChanged()
    }

    LaunchedEffect(permissionsGranted) {
        if (permissionsGranted) data.permissionsChanged()
    }

    Surface(Modifier.fillMaxSize(), color = KantuColors.PageBg) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            if (chineseUnavailable) {
                Text(
                    text = "请让家人安装中文语音",
                    modifier = Modifier.fillMaxWidth().background(KantuColors.Banner).padding(12.dp),
                    color = KantuColors.TextPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
            if (permissionsGranted) {
                Box(Modifier.weight(1f)) {
                    PhoneHome(speech = speech, data = data)
                }
            } else {
                Box(Modifier.weight(1f)) {
                    PermissionPage(
                        denied = permissionDenied,
                        onRequest = {
                            speech.speak("请求权限")
                            launcher.launch(RequiredPermissions + Manifest.permission.READ_CALL_LOG)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionPage(denied: Boolean, onRequest: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "请家人帮助设置",
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            color = KantuColors.TextPrimary,
            textAlign = TextAlign.Center,
        )
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            PermissionReason("☎", "拨打电话", "按绿色按钮直接打电话")
            PermissionReason("人", "读取通讯录", "显示家人的名字和照片")
            PermissionReason("时", "读取通话记录", "显示最近联系过的人")
        }
        if (denied) {
            Text(
                "没有权限，请家人再试一次",
                color = KantuColors.Danger,
                fontSize = 27.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
        Button(
            onClick = onRequest,
            modifier = Modifier.fillMaxWidth().height(76.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = KantuColors.Primary,
                contentColor = KantuColors.OnPrimary,
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp, pressedElevation = 1.dp),
        ) {
            Text(if (denied) "重新授权" else "家人点这里授权", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PermissionReason(symbol: String, title: String, explanation: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, CardShape)
            .clip(CardShape)
            .background(KantuColors.Card)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            symbol,
            fontSize = 40.sp,
            modifier = Modifier.width(64.dp),
            textAlign = TextAlign.Center,
            color = KantuColors.Primary,
        )
        Column {
            Text(title, fontSize = 27.sp, fontWeight = FontWeight.Bold, color = KantuColors.TextPrimary)
            Text(explanation, fontSize = 20.sp, color = KantuColors.TextSecondary)
        }
    }
}

@Composable
internal fun PhoneHome(speech: VoiceAnnouncer, data: ContactDirectory) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    var largeError by remember { mutableStateOf<String?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                speech.stop()
                showSettings = false
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); speech.stop() }
    }
    LaunchedEffect(showSettings) {
        if (showSettings) {
            delay(60_000)
            showSettings = false
            speech.speak("已关闭设置")
        }
    }
    LaunchedEffect(Unit) {
        speech.speak("点一下听名字，再点一下打电话。也可以直接输入号码。")
    }

    LaunchedEffect(largeError) {
        if (largeError != null) {
            delay(3500)
            largeError = null
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Header(
                selectedTab = selectedTab,
                onTab = { tab ->
                    speech.stop()
                    selectedTab = tab
                    speech.speak(if (tab == 0) "拨号" else "通讯录")
                },
                onSettings = {
                    speech.speak("设置")
                    showSettings = true
                },
            )
            if (selectedTab == 0) {
                DialerPage(
                    speech = speech,
                    data = data,
                    onError = { largeError = it },
                )
            } else {
                ContactsPage(
                    speech = speech,
                    data = data,
                    onError = { largeError = it },
                )
            }
        }
        largeError?.let { message ->
            Box(
                Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp)
                    .shadow(6.dp, CardShape)
                    .clip(CardShape)
                    .background(KantuColors.Danger)
                    .padding(horizontal = 28.dp, vertical = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    message,
                    color = Color.White,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
    if (showSettings) {
        VolumeDialog(speech = speech, onDismiss = {
            speech.speak("关闭")
            showSettings = false
        })
    }
}

@Composable
private fun Header(selectedTab: Int, onTab: (Int) -> Unit, onSettings: () -> Unit) {
    val base = LocalViewConfiguration.current
    val familyHold = remember(base) {
        object : ViewConfiguration by base {
            override val longPressTimeoutMillis = 3_000L
        }
    }
    CompositionLocalProvider(LocalViewConfiguration provides familyHold) {
        HeaderTabs(selectedTab, onTab, onSettings)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HeaderTabs(selectedTab: Int, onTab: (Int) -> Unit, onSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(KantuColors.PageBg)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(56.dp)
                .clip(PillShape)
                .background(KantuColors.TabTrack)
                .padding(4.dp),
        ) {
            listOf("拨号", "通讯录").forEachIndexed { index, title ->
                val selected = selectedTab == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(24.dp))
                        .background(if (selected) KantuColors.Primary else Color.Transparent)
                        .combinedClickable(
                            onClick = { onTab(index) },
                            onLongClickLabel = "家人长按三秒调整播报音量",
                            onLongClick = onSettings,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        title,
                        color = if (selected) KantuColors.OnPrimary else KantuColors.TextPrimary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun DialerPage(speech: VoiceAnnouncer, data: ContactDirectory, onError: (String) -> Unit) {
    val history by data.history.collectAsStateWithLifecycle()
    val loading by data.loading.collectAsStateWithLifecycle()
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var number by remember { mutableStateOf("") }
    val context = LocalContext.current
    val gateway = LocalCallGateway.current ?: remember(context) { SystemCallGateway(context) }

    LaunchedEffect(history, loading) {
        if (selectedId != null && history.none { it.id == selectedId }) selectedId = null
        if (!loading && history.isEmpty()) speech.speak("没有通话记录")
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            if (history.isEmpty()) {
                EmptyMessage(if (loading) "正在读取" else "没有通话记录")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(history, key = { it.id }) { item ->
                        PersonRow(
                            name = item.name,
                            number = item.number,
                            photoUri = item.photoUri,
                            selected = selectedId == item.id,
                            demo = item.isDemo,
                            onClick = {
                                if (selectedId == item.id) {
                                    if (item.isDemo) speech.speak("这是演示联系人，不会拨号")
                                    else announceAndCall(context, speech, item.name, item.number, onError, gateway)
                                } else {
                                    selectedId = item.id
                                    speech.speak(announceName(item.name, item.number))
                                }
                            },
                        )
                    }
                }
            }
        }
        Keypad(
            modifier = Modifier.fillMaxWidth().weight(1f),
            number = number,
            onDigit = { digit ->
                if (number.length < 20) {
                    speech.speak(digitToSpeech(digit))
                    number += digit
                } else speech.speak("号码太长，请检查")
            },
            onClear = {
                speech.speak("清除")
                number = ""
            },
            onCall = {
                if (number.isBlank()) {
                    speech.speak("请输入号码")
                    onError("请输入号码")
                } else {
                    announceAndCall(context, speech, "", number, onError, gateway)
                }
            },
        )
    }
}

@Composable
private fun Keypad(
    modifier: Modifier,
    number: String,
    onDigit: (String) -> Unit,
    onClear: () -> Unit,
    onCall: () -> Unit,
) {
    Column(modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1.35f)
                .shadow(3.dp, CardShape)
                .clip(CardShape)
                .background(KantuColors.Card)
                .border(1.dp, KantuColors.FieldStroke, CardShape)
                .padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            AutoSizeDialText(number)
        }
        listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9")).forEach { row ->
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { digit -> KeyButton(digit, Modifier.weight(1f).fillMaxHeight()) { onDigit(digit) } }
            }
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            KeyButton(
                label = "清除",
                modifier = Modifier.weight(1f).fillMaxHeight(),
                container = KantuColors.Danger,
                content = KantuColors.OnPrimary,
                onClick = onClear,
            )
            KeyButton("0", Modifier.weight(1f).fillMaxHeight()) { onDigit("0") }
            KeyButton(
                label = "拨打",
                modifier = Modifier.weight(1f).fillMaxHeight(),
                container = KantuColors.Primary,
                content = KantuColors.OnPrimary,
                onClick = onCall,
            )
        }
    }
}

@Composable
private fun AutoSizeDialText(number: String) {
    val placeholder = number.isBlank()
    val text = if (placeholder) "请输入号码" else number
    val maxSp = if (placeholder) 40f else 56f
    val minSp = 20f
    var fontSp by remember(number) { mutableFloatStateOf(maxSp) }
    Text(
        text = text,
        color = if (placeholder) KantuColors.TextSecondary else KantuColors.TextPrimary,
        fontSize = fontSp.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
        onTextLayout = { layout ->
            if (layout.hasVisualOverflow && fontSp > minSp) {
                fontSp = (fontSp - 2f).coerceAtLeast(minSp)
            }
        },
    )
}

@Composable
private fun KeyButton(
    label: String,
    modifier: Modifier,
    container: Color = KantuColors.Card,
    content: Color = KantuColors.TextPrimary,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = KeyShape,
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
        contentPadding = PaddingValues(2.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp, pressedElevation = 1.dp),
    ) {
        Text(label, fontSize = if (label.length > 1) 28.sp else 52.sp, fontWeight = FontWeight.Bold, color = content)
    }
}

@Composable
private fun ContactsPage(speech: VoiceAnnouncer, data: ContactDirectory, onError: (String) -> Unit) {
    val contacts by data.contacts.collectAsStateWithLifecycle()
    val loading by data.loading.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val gateway = LocalCallGateway.current ?: remember(context) { SystemCallGateway(context) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    val selectedIndex = contacts.indexOfFirst { "${it.id}:${it.number}" == selectedId }
    val selected = contacts.getOrNull(selectedIndex)

    LaunchedEffect(contacts, loading) {
        if (selectedId != null && selectedIndex < 0) selectedId = null
        if (!loading && contacts.isEmpty()) speech.speak("通讯录为空")
    }

    fun moveTo(index: Int) {
        if (contacts.isEmpty()) {
            speech.speak("通讯录为空")
            onError("通讯录为空")
            return
        }
        val target = index.coerceIn(0, contacts.lastIndex)
        selectedId = "${contacts[target].id}:${contacts[target].number}"
        speech.speak(announceName(contacts[target].name, contacts[target].number))
        scope.launch { listState.animateScrollToItem(target) }
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            if (contacts.isEmpty()) {
                EmptyMessage(if (loading) "正在读取" else "通讯录为空")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(contacts.size, key = { "${contacts[it].id}:${contacts[it].number}" }) { index ->
                        val item = contacts[index]
                        PersonRow(
                            name = item.name,
                            number = item.number,
                            photoUri = item.photoUri,
                            selected = "${item.id}:${item.number}" == selectedId,
                            demo = item.isDemo,
                            onClick = {
                                if (selectedId == "${item.id}:${item.number}") {
                                    if (item.isDemo) speech.speak("这是演示联系人，不会拨号")
                                    else announceAndCall(context, speech, item.name, item.number, onError, gateway)
                                } else {
                                    selectedId = "${item.id}:${item.number}"
                                    speech.speak(announceName(item.name, item.number))
                                }
                            },
                        )
                    }
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(start = 10.dp, end = 10.dp, top = 12.dp, bottom = 10.dp)
                .navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = {
                    speech.speak("回到顶部")
                    if (contacts.isNotEmpty()) {
                        selectedId = "${contacts.first().id}:${contacts.first().number}"
                        speech.speak("回到顶部，${announceName(contacts.first().name, contacts.first().number)}")
                        scope.launch { listState.animateScrollToItem(0) }
                    } else {
                        speech.speak("通讯录为空")
                        onError("通讯录为空")
                    }
                },
                modifier = Modifier.weight(1f).fillMaxHeight(0.62f),
                shape = CardShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = KantuColors.Card,
                    contentColor = KantuColors.TextPrimary,
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp, pressedElevation = 1.dp),
            ) {
                Text(
                    "回到\n顶部",
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = KantuColors.TextPrimary,
                )
            }
            Column(
                Modifier.weight(0.9f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            ) {
                Button(
                    onClick = { moveTo(if (selectedIndex < 0) 0 else selectedIndex - 1) },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    shape = CardShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = KantuColors.Primary,
                        contentColor = KantuColors.OnPrimary,
                    ),
                    contentPadding = PaddingValues(8.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp, pressedElevation = 1.dp),
                ) { DirectionArrow(up = true, Modifier.fillMaxHeight(0.72f).fillMaxWidth(0.55f)) }
                Button(
                    onClick = { moveTo(if (selectedIndex < 0) 0 else selectedIndex + 1) },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    shape = CardShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = KantuColors.Primary,
                        contentColor = KantuColors.OnPrimary,
                    ),
                    contentPadding = PaddingValues(8.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp, pressedElevation = 1.dp),
                ) { DirectionArrow(up = false, Modifier.fillMaxHeight(0.72f).fillMaxWidth(0.55f)) }
            }
            Button(
                onClick = {
                    if (selected == null) {
                        speech.speak("请先选择联系人")
                        onError("请先选择联系人")
                    } else {
                        if (selected.isDemo) speech.speak("这是演示联系人，不会拨号")
                        else announceAndCall(context, speech, selected.name, selected.number, onError, gateway)
                    }
                },
                modifier = Modifier.weight(1f).fillMaxHeight(0.62f),
                shape = CardShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = KantuColors.Primary,
                    contentColor = KantuColors.OnPrimary,
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp, pressedElevation = 1.dp),
            ) {
                Text(
                    "拨打\n电话",
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = KantuColors.OnPrimary,
                )
            }
        }
    }
}

@Composable
private fun DirectionArrow(up: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val path = Path()
        if (up) {
            path.moveTo(w * 0.5f, h * 0.06f)
            path.lineTo(w * 0.94f, h * 0.48f)
            path.lineTo(w * 0.66f, h * 0.48f)
            path.lineTo(w * 0.66f, h * 0.94f)
            path.lineTo(w * 0.34f, h * 0.94f)
            path.lineTo(w * 0.34f, h * 0.48f)
            path.lineTo(w * 0.06f, h * 0.48f)
            path.close()
        } else {
            path.moveTo(w * 0.5f, h * 0.94f)
            path.lineTo(w * 0.94f, h * 0.52f)
            path.lineTo(w * 0.66f, h * 0.52f)
            path.lineTo(w * 0.66f, h * 0.06f)
            path.lineTo(w * 0.34f, h * 0.06f)
            path.lineTo(w * 0.34f, h * 0.52f)
            path.lineTo(w * 0.06f, h * 0.52f)
            path.close()
        }
        drawPath(path, Color.White)
    }
}

@Composable
private fun PersonRow(
    name: String,
    number: String,
    photoUri: String?,
    selected: Boolean,
    demo: Boolean,
    onClick: () -> Unit,
) {
    val nameColor = if (selected) Color.White else KantuColors.TextPrimary
    val numberColor = if (selected) Color.White else KantuColors.TextSecondary
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(if (selected) 8.dp else 3.dp, CardShape)
            .clip(CardShape)
            .background(if (selected) KantuColors.Primary else KantuColors.Card)
            .then(if (selected) Modifier.border(3.dp, Color.White, CardShape) else Modifier)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ContactPhoto(photoUri, name, number)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    name.ifBlank { number },
                    color = nameColor,
                    fontSize = 27.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                if (name.isNotBlank()) {
                    Text(number, color = numberColor, fontSize = 19.sp, maxLines = 1)
                }
            }
            if (demo) Text("演示", color = numberColor, fontSize = 16.sp)
        }
    }
}

@Composable
private fun ContactPhoto(photoUri: String?, name: String, number: String) {
    val context = LocalContext.current
    var bitmap by remember(photoUri) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(photoUri) {
        bitmap = if (photoUri == null) null else withContext(Dispatchers.IO) {
            runCatching {
                val uri = Uri.parse(photoUri)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (bounds.outWidth / sample > 512 || bounds.outHeight / sample > 512) sample *= 2
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                }
            }.getOrNull()
        }
    }
    val initial = name.trim().firstOrNull()?.toString()
        ?: number.filter(Char::isDigit).firstOrNull()?.toString()
        ?: number.trim().firstOrNull()?.toString()
        ?: "?"
    Box(
        modifier = Modifier.size(72.dp).clip(CircleShape).background(KantuColors.Primary),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(bitmap!!.asImageBitmap(), contentDescription = "联系人照片", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Text(initial, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EmptyMessage(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = KantuColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VolumeDialog(speech: VoiceAnnouncer, onDismiss: () -> Unit) {
    var volume by remember { mutableFloatStateOf(speech.volume * 100f) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = KantuColors.Card,
        title = {
            Text("播报音量", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = KantuColors.TextPrimary)
        },
        text = {
            Column {
                Text("播报音量 ${volume.toInt()}", fontSize = 24.sp, color = KantuColors.TextSecondary)
                Slider(
                    value = volume,
                    onValueChange = {
                        volume = it
                        speech.setVolume((it / 100f).coerceIn(0f, 1f))
                    },
                    onValueChangeFinished = { speech.speak("这是平时的播报声音") },
                    valueRange = 30f..100f,
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                )
                Text("播报强度单独保存，不改系统音量。拨号前使用最大播报强度。实际响度仍受系统音量、静音和耳机影响。",
                    fontSize = 17.sp, color = KantuColors.TextSecondary)
                TextButton(onClick = { speech.speakForCall("这是拨号前的播报声音") }) {
                    Text("试听拨号声音")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.height(58.dp)) {
                Text("关闭", fontSize = 24.sp, color = KantuColors.Primary, fontWeight = FontWeight.Bold)
            }
        },
        shape = RoundedCornerShape(20.dp),
    )
}

private fun hasAllPermissions(context: Context): Boolean = RequiredPermissions.all {
    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
}

private val digitSpeech = mapOf(
    '0' to "零",
    '1' to "幺",
    '2' to "二",
    '3' to "三",
    '4' to "四",
    '5' to "五",
    '6' to "六",
    '7' to "七",
    '8' to "八",
    '9' to "九",
)

private fun digitToSpeech(digit: String): String = digit.map { digitSpeech[it] ?: it.toString() }.joinToString("")

private fun announceName(name: String, number: String): String {
    val trimmed = name.trim()
    if (trimmed.isNotEmpty()) return trimmed
    val spokenNumber = number.filter(Char::isDigit).map { digitSpeech[it] ?: it.toString() }.joinToString(" ")
    return spokenNumber.ifBlank { number }
}

private fun announceAndCall(
    context: Context,
    speech: VoiceAnnouncer,
    name: String,
    number: String,
    onError: (String) -> Unit,
    gateway: CallGateway,
) {
    val normalized = PhonePolicy.dialableNumber(number)
    if (normalized == null) {
        speech.speak("号码不正确，请检查")
        onError("号码不正确，请检查")
        return
    }
    val who = announceName(name, number)
    val place = {
        if ((context as? LifecycleOwner)?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) != false) {
            gateway.call(normalized)?.let { message -> speech.speak(message); onError(message) }
        }
    }
    if (speech.chineseUnavailable.value) {
        speech.speak("正在拨打$who")
        place()
    } else {
        speech.speakForCall("正在拨打$who", place)
    }
}

