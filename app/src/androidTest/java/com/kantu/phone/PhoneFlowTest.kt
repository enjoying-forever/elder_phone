package com.kantu.phone

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/** All calls/voice/data are fake. No test can place an actual telephone call. */
class PhoneFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val voice = FakeVoice()
    private val calls = mutableListOf<String>()
    private val directory = object : ContactDirectory {
        override val contacts = MutableStateFlow(listOf(
            ContactEntry(1, "女儿", "13800138001"),
            ContactEntry(1, "女儿备用", "13800138002"),
            ContactEntry(2, "老朋友", "13800138003"),
        ))
        override val history = MutableStateFlow(listOf(CallEntry(1, "女儿", "13800138001")))
        override val loading = MutableStateFlow(false)
        override val error = MutableStateFlow<String?>(null)
        override fun permissionsChanged() {}
    }

    @Before fun showHome() {
        compose.setContent {
            KantuTheme {
                CompositionLocalProvider(LocalCallGateway provides object : CallGateway {
                    override fun call(number: String): String? { calls += number; return null }
                }) { PhoneHome(voice, directory) }
            }
        }
    }

    @Test fun originalDialerAndContactListHaveNoConfirmationWindows() {
        compose.onNodeWithText("1", substring = false).assertIsDisplayed()
        saveScreenshot("dialer")
        compose.onNodeWithText("通讯录").performClick()
        compose.onNodeWithText("女儿", substring = false).performClick()
        compose.onNodeWithText("打给这个人？").assertDoesNotExist()
        compose.onNodeWithText("取消").assertDoesNotExist()
        assertTrue(calls.isEmpty())
        saveScreenshot("contacts")
        compose.onNodeWithText("女儿", substring = false).performClick()
        compose.runOnIdle { assertTrue(calls.isEmpty()); voice.complete() }
        compose.runOnIdle { assertEquals(listOf("13800138001"), calls) }
    }

    @Test fun sameContactWithMultipleNumbersDoesNotAutoCallOtherNumber() {
        compose.onNodeWithText("通讯录").performClick()
        compose.onNodeWithText("女儿", substring = false).performClick()
        compose.onNodeWithText("女儿备用").performClick()
        compose.runOnIdle { voice.complete(); assertTrue(calls.isEmpty()) }
        compose.onNodeWithText("女儿备用").performClick()
        compose.runOnIdle { voice.complete(); assertEquals(listOf("13800138002"), calls) }
    }

    @Test fun switchingTabsCancelsPendingSpeechCall() {
        compose.onNodeWithText("女儿", substring = false).performClick()
        compose.onNodeWithText("女儿", substring = false).performClick()
        compose.onNodeWithText("通讯录").performClick()
        compose.runOnIdle { voice.complete(); assertTrue(calls.isEmpty()) }
    }

    @Test fun originalKeypadCallsWithoutExtraWindowAndClearStillClearsAll() {
        listOf("1", "2", "3").forEach { compose.onNodeWithText(it, substring = false).performClick() }
        compose.onNodeWithText("拨打", substring = false).performClick()
        compose.runOnIdle { voice.complete(); assertEquals(listOf("123"), calls) }
        compose.onNodeWithText("清除").performClick()
        compose.onNodeWithText("请输入号码").assertIsDisplayed()
    }

    @Test fun settingsRequireThreeSecondHoldWithoutPasswordGate() {
        compose.onNodeWithText("⚙").assertDoesNotExist()
        compose.onNodeWithText("通讯录").performTouchInput { down(center); advanceEventTime(600); up() }
        compose.onNodeWithText("播报音量", substring = false).assertDoesNotExist()
        compose.onNodeWithText("通讯录").performTouchInput { longClick(durationMillis = 3200) }
        compose.onNodeWithText("播报音量", substring = false).assertIsDisplayed()
        compose.onNodeWithText("家人验证").assertDoesNotExist()
        compose.onNodeWithText("关闭").performClick()
        compose.onNodeWithText("播报音量", substring = false).assertDoesNotExist()
    }

    @Test fun demoContactsNeverCall() {
        compose.runOnIdle {
            directory.contacts.value = listOf(ContactEntry(-1, "演示家人", "13800138000", isDemo = true))
        }
        compose.onNodeWithText("通讯录").performClick()
        repeat(2) { compose.onNodeWithText("演示家人").performClick() }
        compose.runOnIdle { voice.complete(); assertTrue(calls.isEmpty()) }
    }

    private fun saveScreenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.waitForIdle(500, 5_000)
        Thread.sleep(400)
        val folder = File(instrumentation.targetContext.getExternalFilesDir(null), "qa").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private class FakeVoice : VoiceAnnouncer {
        override val chineseUnavailable = MutableStateFlow(false)
        override val volume = 1f
        override fun setVolume(v: Float) {}
        private var pending: (() -> Unit)? = null
        override fun speak(text: String, onDone: (() -> Unit)?) { pending = onDone }
        override fun speakForCall(text: String, onDone: (() -> Unit)?) { pending = onDone }
        override fun stop() { pending = null }
        override fun shutdown() = stop()
        fun complete() { val callback = pending; pending = null; callback?.invoke() }
    }
}
