package com.saa.dreamcue.companion.engine

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.UUID

class BleScanController(
    private val context: Context,
    private val onTargetDetected: (ScanResult) -> Unit
) {

    companion object {
        private const val TAG = "BleScanController"
    }

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    private var bleScanner: BluetoothLeScanner? = null
    private var _isScanning = false
    val isScanning: Boolean
        get() = _isScanning

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.let {
                val serviceUuids = it.scanRecord?.serviceUuids?.joinToString { u -> u.toString() } ?: "None"
                Log.i(TAG, "Matched target BLE device: ${it.device.address}, RSSI=${it.rssi}, UUIDs=[$serviceUuids]")
                onTargetDetected(it)
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results?.firstOrNull()?.let {
                Log.i(TAG, "Matched target BLE device (Batch): ${it.device.address}")
                onTargetDetected(it)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE Scan failed with errorCode: $errorCode")
            _isScanning = false
        }
    }

    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    fun hasScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    @Synchronized
    fun startScanning(targetServiceUuidString: String): Boolean {
        if (!isBluetoothEnabled()) {
            Log.w(TAG, "Cannot start scan: Bluetooth is disabled")
            return false
        }

        if (!hasScanPermission()) {
            Log.w(TAG, "Cannot start scan: Missing BLUETOOTH_SCAN / Location permission")
            return false
        }

        val targetUuid = try {
            UUID.fromString(targetServiceUuidString.trim())
        } catch (e: Exception) {
            Log.e(TAG, "Invalid UUID format: $targetServiceUuidString", e)
            return false
        }

        stopScanning()

        val scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) {
            Log.e(TAG, "BluetoothLeScanner is unavailable")
            return false
        }
        bleScanner = scanner

        try {
            val filter = ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(targetUuid))
                .build()

            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setReportDelay(0L) // Immediate callback, no batching
                .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                .setNumOfMatches(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT)
                .apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        setLegacy(true) // Ensure legacy advertisements are captured
                    }
                }
                .build()

            scanner.startScan(listOf(filter), settings, scanCallback)
            _isScanning = true
            Log.i(TAG, "BLE Scan started successfully with Hardware Filter UUID: $targetUuid")
            return true
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException while starting BLE scan", e)
            _isScanning = false
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Exception while starting BLE scan", e)
            _isScanning = false
            return false
        }
    }

    @Synchronized
    fun stopScanning() {
        if (_isScanning) {
            try {
                bleScanner?.stopScan(scanCallback)
                Log.i(TAG, "BLE Scan stopped")
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping BLE scan", e)
            }
            _isScanning = false
        }
        bleScanner = null
    }
}
