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
import java.nio.ByteBuffer
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

    private var currentTargetUuid: UUID? = null
    private var targetBytesBigEndian: ByteArray? = null
    private var targetBytesLittleEndian: ByteArray? = null

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.let { scanResult ->
                if (isTargetMatch(scanResult)) {
                    val deviceAddress = scanResult.device?.address ?: "Unknown"
                    Log.i(TAG, "SUCCESS: Matched target BLE device: $deviceAddress, RSSI=${scanResult.rssi}")
                    onTargetDetected(scanResult)
                }
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results?.forEach { scanResult ->
                if (isTargetMatch(scanResult)) {
                    Log.i(TAG, "SUCCESS: Matched target BLE device (Batch): ${scanResult.device.address}")
                    onTargetDetected(scanResult)
                    return@forEach
                }
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
        val hasBtScan = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val hasLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasBtScan
        } else {
            hasLocation
        }
    }

    private fun isTargetMatch(result: ScanResult): Boolean {
        val target = currentTargetUuid ?: return false
        val scanRecord = result.scanRecord

        // 1. Check parsed Service UUIDs (Standard 0x06 / 0x07)
        scanRecord?.serviceUuids?.forEach { parcelUuid ->
            if (parcelUuid.uuid == target) {
                return true
            }
        }

        // 2. Check parsed Service Data (0x16 / 0x21)
        scanRecord?.serviceData?.keys?.forEach { parcelUuid ->
            if (parcelUuid.uuid == target) {
                return true
            }
        }

        // 3. Deep Payload Inspection (Bypasses all chip parser bugs, Scan Response fragmentation, etc.)
        val rawBytes = scanRecord?.bytes
        if (rawBytes != null) {
            val big = targetBytesBigEndian
            if (big != null && containsSubarray(rawBytes, big)) {
                return true
            }
            val little = targetBytesLittleEndian
            if (little != null && containsSubarray(rawBytes, little)) {
                return true
            }
        }

        return false
    }

    private fun containsSubarray(haystack: ByteArray, needle: ByteArray): Boolean {
        if (haystack.size < needle.size) return false
        val maxIdx = haystack.size - needle.size
        for (i in 0..maxIdx) {
            var match = true
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) {
                    match = false
                    break
                }
            }
            if (match) return true
        }
        return false
    }

    @Synchronized
    fun startScanning(targetServiceUuidString: String): Boolean {
        if (!isBluetoothEnabled()) {
            Log.w(TAG, "Cannot start scan: Bluetooth is disabled")
            return false
        }

        if (!hasScanPermission()) {
            Log.w(TAG, "Cannot start scan: Missing BLUETOOTH_SCAN or Location permission")
            return false
        }

        val targetUuid = try {
            UUID.fromString(targetServiceUuidString.trim())
        } catch (e: Exception) {
            Log.e(TAG, "Invalid UUID format: $targetServiceUuidString", e)
            return false
        }

        currentTargetUuid = targetUuid

        // Precompute Big-Endian (MSB first)
        val byteBuffer = ByteBuffer.allocate(16)
        byteBuffer.putLong(targetUuid.mostSignificantBits)
        byteBuffer.putLong(targetUuid.leastSignificantBits)
        targetBytesBigEndian = byteBuffer.array().clone()

        // Precompute Little-Endian (LSB first / raw BLE GAP AdvData)
        targetBytesLittleEndian = targetBytesBigEndian!!.reversedArray()

        stopScanning()

        val scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) {
            Log.e(TAG, "BluetoothLeScanner is unavailable")
            return false
        }
        bleScanner = scanner

        try {
            // Using an empty ScanFilter satisfies Android's background execution requirement
            // while completely circumventing manufacturer Bluetooth chip firmware bugs with 128-bit hardware masks!
            val filter = ScanFilter.Builder().build()

            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setReportDelay(0L) // Immediate callback
                .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                .setNumOfMatches(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT)
                .build()

            scanner.startScan(listOf(filter), settings, scanCallback)
            _isScanning = true
            Log.i(TAG, "BLE Scanner started with nRF-Connect level deep payload matching for UUID: $targetUuid")
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
