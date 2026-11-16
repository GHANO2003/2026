package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.analysis.CubeLutEngine
import com.example.analysis.ImageAnalysisEngine
import com.example.camera.CameraProfileRegistry
import com.example.core.localization.AppLanguage
import com.example.core.models.CameraModelId
import com.example.core.models.CompatibilityReport
import com.example.core.models.DiagnosticCheckItem
import com.example.core.models.DiagnosticStatus
import com.example.core.models.ExposureToolsConfig
import com.example.core.models.FocusToolsConfig
import com.example.core.models.HybridConnectionState
import com.example.core.models.ScopeMode
import com.example.ui.screens.CameraSelectionScreen
import com.example.ui.screens.CompatibilityScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.theme.ProMonitorTheme
import com.example.uvc.UvcDescriptorParser
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `app_name matches ProMonitor 360`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("ProMonitor 360", context.getString(R.string.app_name))
    }

    @Test
    fun `camera registry enforces honest per-model capabilities`() {
        val d5600 = CameraProfileRegistry.getSpec(CameraModelId.NIKON_D5600)
        val d5500 = CameraProfileRegistry.getSpec(CameraModelId.NIKON_D5500)
        val eos90d = CameraProfileRegistry.getSpec(CameraModelId.CANON_EOS_90D)

        assertFalse(d5600.supportsRemoteVideoToggle)
        assertFalse(d5500.supportsRemoteVideoToggle)
        assertTrue(eos90d.supportsRemoteVideoToggle)
        assertTrue(d5600.supportsCleanHdmi)
        assertTrue(d5500.supportsCleanHdmi)
        assertTrue(eos90d.supportsCleanHdmi)
    }

    @Test
    fun `cube lut parser parses 3D LUT and applies preview transform`() = runTest {
        val cubeText = """
            TITLE "Test Warm"
            LUT_3D_SIZE 2
            0.0 0.0 0.0
            1.0 0.0 0.0
            0.0 1.0 0.0
            1.0 1.0 0.0
            0.0 0.0 1.0
            1.0 0.0 1.0
            0.0 1.0 1.0
            1.0 1.0 1.0
        """.trimIndent()
        val engine = CubeLutEngine()
        val parsed = engine.parseCubeText("test", "Fallback", cubeText)
        assertEquals("Test Warm", parsed.title)
        assertEquals(2, parsed.size)
        assertEquals(24, parsed.table.size)
    }

    @Test
    fun `image analysis engine computes real focus peaking and scopes from test fixture frame`() = runTest {
        val bmp = Bitmap.createBitmap(64, 36, Bitmap.Config.ARGB_8888)
        for (y in 0 until 36) {
            for (x in 0 until 64) {
                bmp.setPixel(x, y, if (x < 32) Color.BLACK else Color.WHITE)
            }
        }
        val engine = ImageAnalysisEngine()
        val res = engine.analyzeFrame(
            frame = bmp,
            focusConfig = FocusToolsConfig(peakingEnabled = true, peakingSensitivity = 0.7f),
            exposureConfig = ExposureToolsConfig(
                scopeMode = ScopeMode.WAVEFORM,
                zebraEnabled = true,
                highlightClippingEnabled = true,
                shadowClippingEnabled = true
            ),
            isFromHardware = true
        )
        assertTrue(res.isFromHardwareFrame)
        assertNotNull(res.peakingOverlay)
        assertNotNull(res.exposureOverlay)
        assertTrue(res.sharpnessScore > 0f)
        assertTrue(res.highlightClippedPercent > 40f)
        assertTrue(res.shadowClippedPercent > 40f)
        assertEquals(64, res.waveformColumns.size)
    }

    @Test
    fun `uvc descriptor parser extracts MJPEG 1080p60 stream config and bulk endpoint`() {
        val descriptors = byteArrayOf(
            // Interface descriptor: UVC Video Streaming (class=14, sub=2, intf=1)
            9, 0x04, 1, 0, 1, 0x0E, 0x02, 0x00, 0,
            // Bulk IN endpoint 0x81, maxPacket=512
            7, 0x05, 0x81.toByte(), 0x02, 0x00, 0x02, 0x00,
            // VS_FORMAT_MJPEG (formatIndex=1)
            11, 0x24, 0x06, 1, 1, 1, 0, 0, 0, 0, 0,
            // VS_FRAME_MJPEG 1920x1080 @ 60fps (interval = 166666 = 0x00028B0A)
            30, 0x24, 0x07, 1, 0,
            0x80.toByte(), 0x07, // width 1920
            0x38, 0x04,          // height 1080
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0x0A, 0x8B.toByte(), 0x02, 0x00, // default interval 166666 (60fps)
            1, // intervalCount = 1
            0x0A, 0x8B.toByte(), 0x02, 0x00
        )
        val parsed = UvcDescriptorParser.parse(descriptors)
        assertEquals(0x81, parsed.bulkEndpointAddress)
        assertEquals(1, parsed.configs.size)
        assertEquals(1920, parsed.configs.first().width)
        assertEquals(1080, parsed.configs.first().height)
        assertEquals("MJPEG", parsed.configs.first().pixelFormat)
        assertTrue(parsed.configs.first().supportedFps.contains(60))
    }

    @Test
    fun `dashboard screen renders core actions`() {
        composeRule.setContent {
            ProMonitorTheme {
                DashboardScreen(
                    hybridState = HybridConnectionState(),
                    savedProfiles = emptyList(),
                    onScanUsbHdmi = {},
                    onOpenMonitor = {},
                    onOpenWizard = {},
                    onOpenCameraSelection = {},
                    onOpenCompatibility = {},
                    onOpenLutManager = {},
                    onOpenSettings = {},
                    onLoadProfile = {},
                    onDeleteProfile = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("dashboard_launch_monitor_btn").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_scan_usb_btn").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_list").performScrollToNode(hasTestTag("tile_wizard"))
        composeRule.onNodeWithTag("tile_wizard").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_list").performScrollToNode(hasTestTag("tile_diagnostics"))
        composeRule.onNodeWithTag("tile_diagnostics").assertIsDisplayed()
    }

    @Test
    fun `camera selection and compatibility screens render all three priority models`() {
        composeRule.setContent {
            ProMonitorTheme {
                CameraSelectionScreen(
                    activeModelId = CameraModelId.CANON_EOS_90D,
                    language = AppLanguage.ENGLISH,
                    onSelectModel = {},
                    onLaunchWizardForModel = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("camera_selection_list").performScrollToNode(hasTestTag("camera_card_NIKON_D5600"))
        composeRule.onNodeWithTag("camera_card_NIKON_D5600").assertIsDisplayed()
        composeRule.onNodeWithTag("camera_selection_list").performScrollToNode(hasTestTag("camera_card_NIKON_D5500"))
        composeRule.onNodeWithTag("camera_card_NIKON_D5500").assertIsDisplayed()
        composeRule.onNodeWithTag("camera_selection_list").performScrollToNode(hasTestTag("camera_card_CANON_EOS_90D"))
        composeRule.onNodeWithTag("camera_card_CANON_EOS_90D").assertIsDisplayed()
    }

    @Test
    fun `compatibility screen renders honest diagnostic statuses`() {
        val report = CompatibilityReport(
            targetModel = CameraModelId.NIKON_D5600,
            checks = listOf(
                DiagnosticCheckItem("usb_host", "USB Host", "USB", DiagnosticStatus.SUPPORTED, "OK", "OK"),
                DiagnosticCheckItem("video", "Video Feed", "Video", DiagnosticStatus.NOT_TESTED, "No cable", "No cable")
            )
        )
        composeRule.setContent {
            ProMonitorTheme {
                CompatibilityScreen(
                    report = report,
                    language = AppLanguage.ENGLISH,
                    onRunDiagnostics = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("diag_run_btn").assertIsDisplayed()
    }
}
