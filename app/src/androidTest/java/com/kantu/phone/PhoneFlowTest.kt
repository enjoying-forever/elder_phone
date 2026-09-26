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
        override val history = MutableStateFlow(listOf(
            CallEntry(1, "女儿", "13800138001", date = System.currentTimeMillis(), kind = CallKind.MISSED),
            CallEntry(2, "女儿备用", "13800138002", date = System.currentTimeMillis(), kind = CallKind.OUTGOING),
            CallEntry(3, "老朋友", "13800138003", date = System.currentTimeMillis(), kind = CallKind.INCOMING),
            CallEntry(4, "社区医生", "13900139004", date = System.currentTimeMillis(), kind = CallKind.OUTGOING),
        ))
        override val loading = MutableStateFlow(false)
        override val error = MutableStateFlow<String?>(null)
        override fun permissionsChanged() {}
    }

    @Before fun showHome() {
        compose.setContent {
            KantuTheme {
                CompositionLocalProvider(
                    LocalCallGateway provides object : CallGateway {
                        override fun call(number: String): String? { calls += number; return null }
                    },
                    LocalUptimeMillis provides { compose.mainClock.currentTime },
                ) { PhoneHome(voice, directory) }
            }
        }
    }

    @Test fun originalDialerAndContactListHaveNoConfirmationWindows() {
        compose.onNodeWithContentDescription("显示拨号键盘").performClick()
        compose.onNodeWithText("1", substring = false).assertIsDisplayed()
        saveScreenshot("dialer")
        compose.onNodeWithContentDescription("通讯录").performClick()
        compose.onNodeWithText("女儿", substring = false).performClick()
        compose.onNodeWithText("打给这个人？").assertDoesNotExist()
        compose.onNodeWithText("取消").assertDoesNotExist()
        assertTrue(calls.isEmpty())
        saveScreenshot("contacts")
        pauseLikeAPerson()
        compose.onNodeWithText("女儿", substring = false).performClick()
        compose.runOnIdle { assertTrue(calls.isEmpty()); voice.complete() }
        compose.runOnIdle { assertEquals(listOf("13800138001"), calls) }
    }

    @Test fun emptyHistoryDoesNotInterruptNavigationOrDigitSpeech() {
        compose.runOnIdle { directory.history.value = emptyList() }
        compose.onNodeWithContentDescription("通讯录").performClick()
        compose.onNodeWithContentDescription("拨号").performClick()
        compose.runOnIdle { assertEquals("拨号", voice.lastText) }
        compose.onNodeWithText("没有通话记录").assertIsDisplayed()
        compose.onNodeWithContentDescription("显示拨号键盘").performClick()
        compose.onNodeWithText("1", substring = false).performClick()
        compose.runOnIdle { assertEquals("幺", voice.lastText) }
    }

    @Test fun sameContactWithMultipleNumbersDoesNotAutoCallOtherNumber() {
        compose.onNodeWithContentDescription("通讯录").performClick()
        compose.onNodeWithText("女儿", substring = false).performClick()
        compose.onNodeWithText("女儿备用").performClick()
        compose.runOnIdle { voice.complete(); assertTrue(calls.isEmpty()) }
        pauseLikeAPerson()
        compose.onNodeWithText("女儿备用").performClick()
        compose.runOnIdle { voice.complete(); assertEquals(listOf("13800138002"), calls) }
    }

    @Test fun navigationUsesPicturesButSpeaksPageNames() {
        compose.onNodeWithText("通讯录").assertDoesNotExist()
        compose.onNodeWithText("拨号", substring = false).assertDoesNotExist()
        compose.onNodeWithContentDescription("通讯录").performClick()
        compose.runOnIdle { assertEquals("通讯录", voice.lastText) }
        compose.onNodeWithContentDescription("通讯录").assertIsSelected()
        compose.onNodeWithContentDescription("拨号").performClick()
        compose.runOnIdle { assertEquals("拨号", voice.lastText) }
        compose.onNodeWithContentDescription("拨号").assertIsSelected()
    }

    @Test fun switchingTabsCancelsPendingSpeechCall() {
        compose.onNodeWithText("女儿", substring = false).performClick()
        pauseLikeAPerson()
        compose.onNodeWithText("女儿", substring = false).performClick()
        compose.runOnIdle { assertTrue(voice.lastText.startsWith("正在拨打")) }
        compose.onNodeWithContentDescription("通讯录").performClick()
        compose.runOnIdle { voice.complete(); assertTrue(calls.isEmpty()) }
    }

    @Test fun originalKeypadCallsWithoutExtraWindowAndClearStillClearsAll() {
        compose.onNodeWithContentDescription("显示拨号键盘").performClick()
        listOf("1", "2", "3").forEach { compose.onNodeWithText(it, substring = false).performClick() }
        compose.onNodeWithText("拨打", substring = false).performClick()
        compose.runOnIdle { voice.complete(); assertEquals(listOf("123"), calls) }
        compose.onNodeWithText("清除").performClick()
        compose.onNodeWithText("请输入号码").assertIsDisplayed()
    }

    @Test fun numberInputFiltersHistoryAndClearRestoresIt() {
        compose.onNodeWithContentDescription("显示拨号键盘").performClick()
        listOf("8", "0", "0", "2").forEach {
            compose.onNodeWithText(it, substring = false).performClick()
        }
        compose.onNodeWithText("女儿备用", substring = false).assertIsDisplayed()
        compose.onNodeWithText("老朋友", substring = false).assertDoesNotExist()
        saveScreenshot("dialer-search")
        compose.onNodeWithText("清除").performClick()
        compose.onNodeWithText("老朋友", substring = false).assertExists()
    }

    @Test fun keypadCanBeHiddenAndShownWithoutLosingHistory() {
        compose.onNodeWithContentDescription("显示拨号键盘").performClick()
        compose.onNodeWithContentDescription("隐藏拨号键盘").performClick()
        compose.onNodeWithText("1", substring = false).assertDoesNotExist()
        compose.onNodeWithText("女儿", substring = false).assertIsDisplayed()
        compose.runOnIdle { assertEquals("已隐藏数字键盘", voice.lastText) }
        saveScreenshot("dialer-keypad-hidden")
        compose.onNodeWithContentDescription("显示拨号键盘").performClick()
        compose.onNodeWithText("1", substring = false).assertIsDisplayed()
        compose.runOnIdle { assertEquals("已显示数字键盘", voice.lastText) }
    }

    @Test fun keypadStartsHiddenAndBothStatesSurviveTabSwitches() {
        compose.onNodeWithText("1", substring = false).assertDoesNotExist()
        compose.onNodeWithContentDescription("通讯录").performClick()
        compose.onNodeWithContentDescription("拨号").performClick()
        compose.onNodeWithContentDescription("显示拨号键盘").assertIsDisplayed()
        compose.onNodeWithContentDescription("显示拨号键盘").performClick()
        listOf("1", "2", "3").forEach { compose.onNodeWithText(it, substring = false).performClick() }
        compose.onNodeWithContentDescription("通讯录").performClick()
        compose.onNodeWithContentDescription("拨号").performClick()
        compose.onNodeWithText("123", substring = false).assertIsDisplayed()
        compose.onNodeWithContentDescription("隐藏拨号键盘").performClick()
        compose.onNodeWithContentDescription("通讯录").performClick()
        compose.onNodeWithContentDescription("拨号").performClick()
        compose.onNodeWithText("1", substring = false).assertDoesNotExist()
    }

    @Test fun missedCallIsMarkedAndSpeaksWhenItHappened() {
        compose.onNodeWithText("未接电话", substring = true).assertIsDisplayed()
        compose.onNodeWithText("女儿", substring = false).performClick()
        compose.runOnIdle {
            assertTrue(voice.lastText.contains("今天"))
            assertTrue(voice.lastText.contains("未接电话"))
            assertTrue(voice.lastText.endsWith("女儿"))
        }
        saveScreenshot("missed-selected")
    }

    @Test fun historyUsesNumericDatesAndOnlyMissedCallsSpeakTime() {
        compose.runOnIdle {
            directory.history.value = listOf(directory.history.value.first())
        }
        compose.onNodeWithText(formatCallDate(directory.history.value.first().date)).assertIsDisplayed()
        compose.onNodeWithText("今天", substring = true).assertDoesNotExist()
        compose.runOnIdle {
            directory.history.value = listOf(
                CallEntry(2, "老朋友", "13800138003", date = System.currentTimeMillis(), kind = CallKind.OUTGOING),
            )
        }
        compose.onNodeWithText("老朋友").performClick()
        compose.runOnIdle { assertEquals("老朋友", voice.lastText) }
    }

    @Test fun settingsRequireThreeSecondHoldWithoutPasswordGate() {
        compose.onNodeWithText("⚙").assertDoesNotExist()
        compose.onNodeWithContentDescription("通讯录").performTouchInput { down(center); advanceEventTime(600); up() }
        compose.onNodeWithText("播报音量", substring = false).assertDoesNotExist()
        compose.onNodeWithContentDescription("通讯录").performTouchInput { longClick(durationMillis = 3200) }
        compose.onNodeWithText("播报音量", substring = false).assertIsDisplayed()
        compose.onNodeWithText("家人验证").assertDoesNotExist()
        saveScreenshot("settings")
        compose.onNodeWithText("关闭").performClick()
        compose.onNodeWithText("播报音量", substring = false).assertDoesNotExist()
    }

    @Test fun demoContactsNeverCall() {
        compose.runOnIdle {
            directory.contacts.value = listOf(ContactEntry(-1, "演示家人", "13800138000", isDemo = true))
        }
        compose.onNodeWithContentDescription("通讯录").performClick()
        compose.onNodeWithText("演示家人").performClick()
        pauseLikeAPerson()
        compose.onNodeWithText("演示家人").performClick()
        compose.runOnIdle { assertEquals("这是演示联系人，不会拨号", voice.lastText) }
        compose.runOnIdle { voice.complete(); assertTrue(calls.isEmpty()) }
    }

    @Test fun shakyDoubleTapOnlySelectsAndSecondDeliberateTapCalls() {
        compose.onNodeWithContentDescription("通讯录").performClick()
        compose.onNodeWithText("老朋友").performClick()
        compose.onNodeWithText("老朋友").performClick()
        compose.runOnIdle { assertEquals("老朋友", voice.lastText); voice.complete(); assertTrue(calls.isEmpty()) }
        pauseLikeAPerson()
        compose.onNodeWithText("老朋友").performClick()
        compose.runOnIdle { voice.complete(); assertEquals(listOf("13800138003"), calls) }
    }

    @Test fun shakyDoubleTapInCallHistoryDoesNotCall() {
        compose.onNodeWithText("社区医生").performClick()
        compose.onNodeWithText("社区医生").performClick()
        compose.runOnIdle { voice.complete(); assertTrue(calls.isEmpty()) }
    }

    @Test fun tappingTheNumberReadsItBackInGroups() {
        compose.onNodeWithContentDescription("显示拨号键盘").performClick()
        compose.onNodeWithText("请输入号码").performClick()
        compose.runOnIdle { assertEquals("请输入号码", voice.lastText) }
        "13912345678".forEach { compose.onNodeWithText(it.toString(), substring = false).performClick() }
        compose.onNodeWithText("13912345678", substring = false).performClick()
        compose.runOnIdle {
            assertEquals("号码是，幺三九，幺二三四，五六七八", voice.lastText)
            assertTrue(calls.isEmpty())
        }
    }

    /** Waits longer than the shaky-double-tap window, as a person listening to the name would. */
    private fun pauseLikeAPerson() {
        compose.mainClock.advanceTimeBy(CONFIRM_TAP_MIN_GAP_MS + 300)
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
        var lastText = ""
        override fun speak(text: String, onDone: (() -> Unit)?) { lastText = text; pending = onDone }
        override fun speakForCall(text: String, onDone: (() -> Unit)?) { lastText = text; pending = onDone }
        override fun stop() { pending = null }
        override fun shutdown() = stop()
        fun complete() { val callback = pending; pending = null; callback?.invoke() }
    }
}
