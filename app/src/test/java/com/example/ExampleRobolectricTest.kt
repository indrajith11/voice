package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ai.DeviceHealthAI
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("VisionVoice", appName)
  }

  @Test
  fun `test device health score calculations`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val deviceHealth = DeviceHealthAI(context)
    
    // Test base calculations
    val score = deviceHealth.calculateDeviceHealthScore()
    assertTrue("Default comfort score must reside between 0 and 100", score in 0..100)
    
    // Test thermal limit detection
    val standardThrottling = deviceHealth.isThermalThrottling()
    assertFalse("Default thermal index should be normal", standardThrottling)
  }

  @Test
  fun `test device report content structure`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val deviceHealth = DeviceHealthAI(context)
    
    val report = deviceHealth.createDailyDeviceReport()
    assertNotNull(report)
    assertTrue("Report should contain health stability metrics", report.contains("Stability Score"))
    assertTrue("Report should contain Temperature levels", report.contains("Temperature"))
    assertTrue("Report should contain RAM details", report.contains("RAM"))
  }
}
