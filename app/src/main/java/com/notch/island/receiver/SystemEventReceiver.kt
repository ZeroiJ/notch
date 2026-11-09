package com.notch.island.receiver

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.BatteryManager
import com.notch.island.core.IslandEventManager

/**
 * BroadcastReceiver monitoring real-time hardware status changes:
 * Charging connected, Silent/Ringer switch flips, Low Battery, and Bluetooth audio accessories.
 */
class SystemEventReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        when (intent.action) {
            Intent.ACTION_POWER_CONNECTED -> {
                val level = getBatteryPercentage(context)
                IslandEventManager.postPeek(
                    title = "Charging",
                    subtitle = "$level% • Fast Charge",
                    trailingText = "$level%",
                    durationMs = 3500L
                )
            }

            Intent.ACTION_POWER_DISCONNECTED -> {
                val level = getBatteryPercentage(context)
                IslandEventManager.postPeek(
                    title = "Charger Disconnected",
                    subtitle = "$level% battery remaining",
                    trailingText = "$level%",
                    durationMs = 2800L
                )
            }

            Intent.ACTION_BATTERY_LOW -> {
                val level = getBatteryPercentage(context)
                IslandEventManager.postPeek(
                    title = "Low Battery",
                    subtitle = "Connect charger soon ($level%)",
                    trailingText = "$level%",
                    durationMs = 4500L
                )
            }

            AudioManager.RINGER_MODE_CHANGED_ACTION -> {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                when (audioManager.ringerMode) {
                    AudioManager.RINGER_MODE_SILENT, AudioManager.RINGER_MODE_VIBRATE -> {
                        IslandEventManager.postPeek(
                            title = "Silent",
                            subtitle = "Mute switch flipped",
                            trailingText = "ON",
                            durationMs = 2500L
                        )
                    }
                    AudioManager.RINGER_MODE_NORMAL -> {
                        IslandEventManager.postPeek(
                            title = "Ring",
                            subtitle = "Sound turned on",
                            trailingText = "OFF",
                            durationMs = 2500L
                        )
                    }
                }
            }

            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                val device: BluetoothDevice? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }
                val deviceName = try {
                    device?.name ?: "Bluetooth Device"
                } catch (e: SecurityException) {
                    "Audio Accessory"
                }
                IslandEventManager.postPeek(
                    title = "$deviceName Connected",
                    subtitle = "Spatial Audio Active",
                    trailingText = "BT",
                    durationMs = 3200L
                )
            }

            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                val device: BluetoothDevice? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }
                val deviceName = try {
                    device?.name ?: "Bluetooth Device"
                } catch (e: SecurityException) {
                    "Audio Accessory"
                }
                IslandEventManager.postPeek(
                    title = "$deviceName Disconnected",
                    subtitle = "Audio routed to phone speaker",
                    durationMs = 2800L
                )
            }
        }
    }

    private fun getBatteryPercentage(context: Context): Int {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
            context.registerReceiver(null, filter)
        }
        val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) {
            (level * 100 / scale.toFloat()).toInt()
        } else {
            100
        }
    }

    companion object {
        fun register(context: Context): SystemEventReceiver {
            val receiver = SystemEventReceiver()
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
                addAction(Intent.ACTION_BATTERY_LOW)
                addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            }
            context.registerReceiver(receiver, filter)
            return receiver
        }

        fun unregister(context: Context, receiver: SystemEventReceiver?) {
            receiver?.let {
                try {
                    context.unregisterReceiver(it)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
