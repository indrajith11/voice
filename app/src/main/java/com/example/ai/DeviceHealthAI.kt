package com.example.ai

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Environment
import android.util.Log
import java.io.File

/**
 * Device Health AI Subsystem
 * Tracks thermals, charging speeds, storage capacities, RAM utilization,
 * battery degradation, and issues offline optimizing directives.
 */
class DeviceHealthAI(private val context: Context) {

    companion object {
        private const val TAG = "DeviceHealthAI"
    }

    /**
     * Compute a holistic 0-100 score representing smartphone performance, stability, and longevity.
     */
    fun calculateDeviceHealthScore(): Int {
        var score = 100
        val temp = getBatteryTemperature()
        val ramUsage = getRamUsageFraction()
        val storageUsage = getStorageUsageFraction()
        val degradationState = estimateBatteryDegradation()

        // 1. Temperature penalties
        if (temp > 45f) score -= 15
        else if (temp > 38f) score -= 5

        // 2. RAM penalties
        if (ramUsage > 0.9f) score -= 15
        else if (ramUsage > 0.75f) score -= 8

        // 3. Storage penalties
        if (storageUsage > 0.95f) score -= 15
        else if (storageUsage > 0.85f) score -= 5

        // 4. Battery degradation penalty
        if (degradationState.contains("Heavy")) score -= 12
        else if (degradationState.contains("Moderate")) score -= 5

        return score.coerceIn(0, 100)
    }

    /**
     * Estimates battery aging/degradation based on voltage and charge statistics.
     */
    fun estimateBatteryDegradation(): String {
        val register = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return "Normal/Healthy"
        val voltage = register.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) // millivolts
        val level = register.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
        
        return if (level > 90 && voltage > 0 && voltage < 3700) {
            "Heavy Degradation (Low Voltage output under full load)"
        } else if (level > 80 && voltage > 0 && voltage < 3800) {
            "Moderate Degradation"
        } else {
            "Healthy (Sufficient output potential)"
        }
    }

    /**
     * Measures the incoming charger power rate and evaluates wire/charger brick efficiency.
     */
    fun getChargerQualityLevel(): String {
        val register = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return "Unknown"
        val pluggedStatus = register.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
        
        if (pluggedStatus != BatteryManager.BATTERY_PLUGGED_AC && pluggedStatus != BatteryManager.BATTERY_PLUGGED_USB) {
            return "Disconnected (Battery Mode)"
        }

        // On modern Android SDKs, we use BatteryManager to monitor microamperes/current flow
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val microAmps = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0
        
        return when {
            microAmps <= 0 -> "Normal Plugged (Inflow metrics charging standard)"
            microAmps > 2_000_000 -> "Ultra-Fast Charge Charger (Premium brick + robust line)"
            microAmps > 1_000_000 -> "Fast Charging Brick detected (Efficient line)"
            else -> "Slow Charging Source (Low inflow rate recorded. Consider switching bricks)"
        }
    }

    fun getBatteryTemperature(): Float {
        val register = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return 30.0f
        val tempRaw = register.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
        return tempRaw / 10.0f // Convert tenths of Celsius to standard Float
    }

    fun getRamUsageFraction(): Float {
        return try {
            val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            val memoryInfo = android.app.ActivityManager.MemoryInfo()
            actManager?.getMemoryInfo(memoryInfo)
            
            val total = memoryInfo.totalMem.toFloat()
            val available = memoryInfo.availMem.toFloat()
            if (total > 0f) (total - available) / total else 0.5f
        } catch (e: Exception) {
            0.45f
        }
    }

    fun getStorageUsageFraction(): Float {
        return try {
            val path = Environment.getDataDirectory()
            val total = path.totalSpace.toFloat()
            val usable = path.usableSpace.toFloat()
            if (total > 0) (total - usable) / total else 0.5f
        } catch (e: Exception) {
            0.5f
        }
    }

    fun isThermalThrottling(): Boolean {
        return getBatteryTemperature() > 41f
    }

    fun createDailyDeviceReport(): String {
        val temp = getBatteryTemperature()
        val score = calculateDeviceHealthScore()
        val memoryStr = String.format("%.1f%%", getRamUsageFraction() * 100f)
        val storageStr = String.format("%.1f%%", getStorageUsageFraction() * 100f)
        val currentPlugState = getChargerQualityLevel()

        return "Daily Hardware Longevity & Optimal Runtime Report\n" +
               "===============================================\n" +
               "• Overall Hardware Stability Score: $score/100\n" +
               "• Active Battery Temperature Sensor: $temp°C (${if (isThermalThrottling()) "Warning: Thermal limits exceeded!" else "Safe range"})\n" +
               "• Active physical RAM workload Allocation: $memoryStr\n" +
               "• Local physical storage load: $storageStr\n" +
               "• Voltage Integrity Aging Estimation: ${estimateBatteryDegradation()}\n" +
               "• Charger Interface Quality Level: $currentPlugState\n" +
               "• Recommendation: ${if (score < 80) "Run automated cache clearing and lower CPU model usage size parameters." else "Systems are operating within green zones. Offline assistance fully optimization capable."}"
    }
}
