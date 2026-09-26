package com.kantu.phone

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
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

/** Clock for the shaky-double-tap guard; tests provide the Compose test clock. */
internal val LocalUptimeMillis = staticCompositionLocalOf<() -> Long> { SystemClock::uptimeMillis }

/**
 * A second tap on the same row sooner than this after selecting it is treated as a
 * trembling double-tap and ignored, so the elder always hears who they picked before calling.
 */
internal const val CONFIRM_TAP_MIN_GAP_MS = 700L

class MainActivity : ComponentActivity() {
    private lateinit var speech: SpeechController
    private val screenTimer = Handler(Looper.getMainLooper())
    private val releaseScreen = Runnable { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        speech = SpeechController(this)
        setContent {
            KantuTheme {
                KantuApp(speech)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        keepScreenAwake()
    }

    // Elders read and dial slowly; don't let a short system timeout lock the phone mid-number.
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) keepScreenAwake()
        return super.dispatchTouchEvent(event)
    }

    private fun keepScreenAwake() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        screenTimer.removeCallbacks(releaseScreen)
        screenTimer.postDelayed(releaseScreen, SCREEN_AWAKE_MS)
    }

    override fun onPause() {
        screenTimer.removeCallbacks(releaseScreen)
        releaseScreen.run()
        super.onPause()
    }

    override fun onDestroy() {
        speech.shutdown()
        super.onDestroy()
    }

    private companion object {
        const val SCREEN_AWAKE_MS = 2 * 60_000L
    }
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
            shape = CardShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = KantuColors.Primary,
                contentColor = KantuColors.OnPrimary,
            ),
            elevation = kantuButtonElevation(),
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
            .shadow(1.dp, CardShape)
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
    var keypadVisible by rememberSaveable { mutableStateOf(false) }
    var dialNumber by rememberSaveable { mutableStateOf("") }
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

    Box(Modifier.fillMaxSize().background(KantuColors.PageBg)) {
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
                    keypadVisible = keypadVisible,
                    onKeypadVisibility = { keypadVisible = it },
                    number = dialNumber,
                    onNumberChange = { dialNumber = it },
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
            .padding(horizontal = KantuSpacing.Page, vertical = KantuSpacing.Gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(68.dp)
                .clip(PillShape)
                .background(KantuColors.TabTrack)
                .border(1.dp, KantuColors.Hairline, PillShape)
                .padding(4.dp),
        ) {
            listOf("拨号", "通讯录").forEachIndexed { index, title ->
                val selected = selectedTab == index
                val selectedColor = KantuColors.Primary
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(TabShape)
                        .background(if (selected) selectedColor else Color.Transparent)
                        .semantics {
                            contentDescription = title
                            this.selected = selected
                        }
                        .combinedClickable(
                            onClick = withHaptic { onTab(index) },
                            onLongClickLabel = "家人长按三秒调整播报音量",
                            onLongClick = onSettings,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    NavigationIcon(
                        contacts = index == 1,
                        color = if (selected) KantuColors.OnPrimary else KantuColors.TextPrimary,
                        modifier = Modifier.size(44.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DialerPage(
    speech: VoiceAnnouncer,
    data: ContactDirectory,
    keypadVisible: Boolean,
    onKeypadVisibility: (Boolean) -> Unit,
    number: String,
    onNumberChange: (String) -> Unit,
    onError: (String) -> Unit,
) {
    val history by data.history.collectAsStateWithLifecycle()
    val loading by data.loading.collectAsStateWithLifecycle()
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var selectedAt by remember { mutableLongStateOf(0L) }
    val uptime = LocalUptimeMillis.current
    val context = LocalContext.current
    val gateway = LocalCallGateway.current ?: remember(context) { SystemCallGateway(context) }
    val searchKey = PhonePolicy.contactKey(number)
    val visibleHistory = remember(history, searchKey) {
        if (searchKey.isBlank()) history else history.filter {
            PhonePolicy.contactKey(it.number).contains(searchKey)
        }
    }

    LaunchedEffect(visibleHistory, loading) {
        if (selectedId != null && visibleHistory.none { it.id == selectedId }) selectedId = null
    }

    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        Box(
            Modifier
                .fillMaxWidth()
                .weight(if (keypadVisible) 0.43f else 1f),
        ) {
            if (history.isEmpty()) {
                EmptyMessage(if (loading) "正在读取" else "没有通话记录")
            } else if (visibleHistory.isEmpty()) {
                EmptyMessage("没有匹配的通话记录")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = KantuSpacing.Page, vertical = KantuSpacing.Gap),
                    verticalArrangement = Arrangement.spacedBy(KantuSpacing.Gap),
                ) {
                    items(visibleHistory, key = { it.id }) { item ->
                        CallHistoryRow(
                            name = item.name,
                            number = item.number,
                            photoUri = item.photoUri,
                            date = item.date,
                            kind = item.kind,
                            selected = selectedId == item.id,
                            demo = item.isDemo,
                            onClick = {
                                if (selectedId == item.id) {
                                    if (uptime() - selectedAt < CONFIRM_TAP_MIN_GAP_MS) Unit
                                    else if (item.isDemo) speech.speak("这是演示联系人，不会拨号")
                                    else announceAndCall(context, speech, item.name, item.number, onError, gateway)
                                } else {
                                    selectedId = item.id
                                    selectedAt = uptime()
                                    speech.speak(
                                        if (item.kind == CallKind.MISSED) {
                                            "${describeCallTime(item.date)}，未接电话，${announceName(item.name, item.number)}"
                                        } else {
                                            announceName(item.name, item.number)
                                        },
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
        if (keypadVisible) {
            Keypad(
                modifier = Modifier.fillMaxWidth().weight(0.57f),
                number = number,
                onNumberTap = {
                    speech.speak(if (number.isBlank()) "请输入号码" else "号码是，${spokenNumber(number)}")
                },
                onDigit = { digit ->
                    if (number.length < 20) {
                        speech.speak(digitToSpeech(digit))
                        onNumberChange(number + digit)
                    } else speech.speak("号码太长，请检查")
                },
                onClear = {
                    speech.speak("清除")
                    onNumberChange("")
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
        KeypadToggle(
            visible = keypadVisible,
            onClick = {
                onKeypadVisibility(!keypadVisible)
                speech.speak(if (!keypadVisible) "已显示数字键盘" else "已隐藏数字键盘")
            },
        )
    }
}

@Composable
private fun Keypad(
    modifier: Modifier,
    number: String,
    onNumberTap: () -> Unit,
    onDigit: (String) -> Unit,
    onClear: () -> Unit,
    onCall: () -> Unit,
) {
    Column(
        modifier.padding(horizontal = 8.dp, vertical = KantuSpacing.KeyGap),
        verticalArrangement = Arrangement.spacedBy(KantuSpacing.KeyGap),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .weight(0.72f)
                .shadow(1.dp, CardShape)
                .clip(CardShape)
                .background(KantuColors.Card)
                .border(1.dp, KantuColors.Hairline, CardShape)
                .clickable(onClickLabel = "听号码", onClick = withHaptic(onNumberTap))
                .padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            DialNumberText(number)
        }
        listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9")).forEach { row ->
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(KantuSpacing.KeyGap)) {
                row.forEach { digit -> KeyButton(digit, Modifier.weight(1f).fillMaxHeight()) { onDigit(digit) } }
            }
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(KantuSpacing.KeyGap)) {
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
                container = KantuColors.CallAction,
                content = KantuColors.OnCallAction,
                onClick = onCall,
            )
        }
    }
}

@Composable
private fun KeypadToggle(visible: Boolean, onClick: () -> Unit) {
    val label = if (visible) "隐藏拨号键盘" else "显示拨号键盘"
    Button(
        onClick = withHaptic(onClick),
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .padding(start = KantuSpacing.Page, end = KantuSpacing.Page, bottom = KantuSpacing.Gap)
            .semantics { contentDescription = label },
        shape = CardShape,
        border = BorderStroke(1.dp, KantuColors.Hairline),
        colors = ButtonDefaults.buttonColors(
            containerColor = KantuColors.Card,
            contentColor = KantuColors.Primary,
        ),
        elevation = kantuButtonElevation(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
    ) {
        KeypadToggleIcon(
            expanded = visible,
            color = KantuColors.Primary,
            modifier = Modifier.size(34.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(label, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun DialNumberText(number: String) {
    val placeholder = number.isBlank()
    val text = if (placeholder) "请输入号码" else number
    val scroll = rememberScrollState()
    LaunchedEffect(number, scroll.maxValue) {
        scroll.scrollTo(if (placeholder) 0 else scroll.maxValue)
    }
    Box(Modifier.fillMaxWidth().horizontalScroll(scroll), contentAlignment = Alignment.Center) {
      Text(
        text = text,
        color = if (placeholder) KantuColors.TextSecondary else KantuColors.TextPrimary,
        fontSize = 32.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        textAlign = TextAlign.Center,
      )
    }
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
        onClick = withHaptic(onClick),
        modifier = modifier,
        shape = KeyShape,
        border = if (container == KantuColors.Card) BorderStroke(1.dp, KantuColors.Hairline) else null,
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
        contentPadding = PaddingValues(2.dp),
        elevation = kantuButtonElevation(),
    ) {
        BoxWithConstraints(contentAlignment = Alignment.Center) {
            val scale = LocalDensity.current.fontScale
            val font = minOf(
                if (label.length > 1) 34f else 72f,
                maxHeight.value / scale * 0.86f,
                maxWidth.value / scale * if (label.length > 1) 0.42f else 0.8f,
            ).coerceAtLeast(1f)
            Text(
                label, fontSize = font.sp, lineHeight = (font * 1.15f).sp,
                fontWeight = FontWeight.Bold, color = content, maxLines = 1,
                softWrap = false,
            )
        }
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
    var selectedAt by remember { mutableLongStateOf(0L) }
    val uptime = LocalUptimeMillis.current
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
        Box(Modifier.fillMaxWidth().weight(0.65f)) {
            if (contacts.isEmpty()) {
                EmptyMessage(if (loading) "正在读取" else "通讯录为空")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(horizontal = KantuSpacing.Page, vertical = KantuSpacing.Gap),
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
                                    if (uptime() - selectedAt < CONFIRM_TAP_MIN_GAP_MS) Unit
                                    else if (item.isDemo) speech.speak("这是演示联系人，不会拨号")
                                    else announceAndCall(context, speech, item.name, item.number, onError, gateway)
                                } else {
                                    selectedId = "${item.id}:${item.number}"
                                    selectedAt = uptime()
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
                .weight(0.35f)
                .padding(horizontal = KantuSpacing.Page, vertical = KantuSpacing.Gap)
                .navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(KantuSpacing.Gap),
        ) {
            Button(
                onClick = withHaptic {
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
                border = BorderStroke(1.dp, KantuColors.Hairline),
                colors = ButtonDefaults.buttonColors(
                    containerColor = KantuColors.Card,
                    contentColor = KantuColors.TextPrimary,
                ),
                elevation = kantuButtonElevation(),
            ) {
                Text(
                    "回到\n顶部",
                    fontSize = 25.sp,
                    lineHeight = 32.sp,
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
                    onClick = withHaptic { moveTo(if (selectedIndex < 0) 0 else selectedIndex - 1) },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    shape = CardShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = KantuColors.Primary,
                        contentColor = KantuColors.OnPrimary,
                    ),
                    contentPadding = PaddingValues(8.dp),
                    elevation = kantuButtonElevation(),
                ) { DirectionArrow(up = true, Modifier.fillMaxHeight(0.72f).fillMaxWidth(0.55f)) }
                Button(
                    onClick = withHaptic { moveTo(if (selectedIndex < 0) 0 else selectedIndex + 1) },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    shape = CardShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = KantuColors.Primary,
                        contentColor = KantuColors.OnPrimary,
                    ),
                    contentPadding = PaddingValues(8.dp),
                    elevation = kantuButtonElevation(),
                ) { DirectionArrow(up = false, Modifier.fillMaxHeight(0.72f).fillMaxWidth(0.55f)) }
            }
            Button(
                onClick = withHaptic {
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
                    containerColor = KantuColors.CallAction,
                    contentColor = KantuColors.OnCallAction,
                ),
                elevation = kantuButtonElevation(),
            ) {
                Text(
                    "拨打\n电话",
                    fontSize = 25.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = KantuColors.OnCallAction,
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
private fun CallHistoryRow(
    name: String,
    number: String,
    photoUri: String?,
    date: Long,
    kind: CallKind,
    selected: Boolean,
    demo: Boolean,
    onClick: () -> Unit,
) {
    val primaryColor = if (selected) Color.White else KantuColors.TextPrimary
    val secondaryColor = if (selected) Color.White else KantuColors.TextSecondary
    val statusColor = when {
        kind == CallKind.MISSED -> KantuColors.Missed
        else -> secondaryColor
    }
    val timeText = formatCallDate(date)
    val statusText = when (kind) {
        CallKind.MISSED -> "未接电话"
        CallKind.INCOMING -> "来电"
        CallKind.OUTGOING -> "已拨电话"
        CallKind.OTHER -> ""
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, CardShape)
            .clip(CardShape)
            .background(if (selected) KantuColors.Primary else KantuColors.Card)
            .border(1.dp, if (selected) KantuColors.Primary else KantuColors.Hairline, CardShape)
            .clickable(onClick = withHaptic(onClick)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ContactPhoto(
                photoUri = photoUri,
                name = name,
                number = number,
                size = 60.dp,
                background = KantuColors.Avatar,
                foreground = KantuColors.Primary,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    name.ifBlank { number },
                    color = primaryColor,
                    fontSize = 25.sp,
                    lineHeight = 27.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (name.isNotBlank()) {
                    Text(
                        number,
                        color = secondaryColor,
                        fontSize = 18.sp,
                        lineHeight = 20.sp,
                        maxLines = 1,
                    )
                }
                Text(
                    timeText,
                    color = secondaryColor,
                    fontSize = 17.sp,
                    lineHeight = 20.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (statusText.isNotEmpty()) {
                    Text(
                        statusText,
                        color = statusColor,
                        modifier = if (kind == CallKind.MISSED && selected) {
                            Modifier.background(KantuColors.Card, KeyShape).padding(horizontal = 4.dp)
                        } else Modifier,
                        fontSize = 17.sp,
                        lineHeight = 20.sp,
                        fontWeight = if (kind == CallKind.MISSED) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
            if (demo) Text("演示", color = secondaryColor, fontSize = 16.sp)
        }
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
            .shadow(1.dp, CardShape)
            .clip(CardShape)
            .background(if (selected) KantuColors.Primary else KantuColors.Card)
            .border(1.dp, if (selected) KantuColors.Primary else KantuColors.Hairline, CardShape)
            .clickable(onClick = withHaptic(onClick)),
    ) {
        Row(
            modifier = Modifier.padding(KantuSpacing.CardPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ContactPhoto(photoUri, name, number)
            Spacer(Modifier.width(KantuSpacing.CardPadding))
            Column(Modifier.weight(1f)) {
                Text(
                    name.ifBlank { number },
                    color = nameColor,
                    fontSize = 27.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
private fun ContactPhoto(
    photoUri: String?,
    name: String,
    number: String,
    size: androidx.compose.ui.unit.Dp = 72.dp,
    background: Color = KantuColors.Avatar,
    foreground: Color = KantuColors.Primary,
) {
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
        modifier = Modifier.size(size).clip(CircleShape).background(background),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(bitmap!!.asImageBitmap(), contentDescription = "联系人照片", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Text(
                initial,
                color = foreground,
                fontSize = if (size < 70.dp) 29.sp else 34.sp,
                fontWeight = FontWeight.Bold,
            )
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
            Column(verticalArrangement = Arrangement.spacedBy(KantuSpacing.Gap)) {
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
                    Text("试听拨号声音", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.height(58.dp)) {
                Text("关闭", fontSize = 24.sp, color = KantuColors.Primary, fontWeight = FontWeight.Bold)
            }
        },
        shape = CardShape,
    )
}

/** Adds a short key vibration to a tap, following the system touch-vibration setting. */
@Composable
private fun withHaptic(onClick: () -> Unit): () -> Unit {
    val view = LocalView.current
    return {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        onClick()
    }
}

private fun hasAllPermissions(context: Context): Boolean = RequiredPermissions.all {
    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
}

private fun digitToSpeech(digit: String): String = digit.map(::spokenDigit).joinToString("")

private fun announceName(name: String, number: String): String =
    name.trim().ifEmpty { spokenNumber(number) }

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
