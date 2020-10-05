package io.github.tewarid.wifitool

import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import androidx.annotation.RequiresApi
import java.lang.StringBuilder

object ScanResultContent {

    private lateinit var ITEMS: List<ScanResult>

    val ITEM_MAP: MutableMap<String, ScanResult> = HashMap()

    var items: List<ScanResult>
        get() = ITEMS
        set(value) {
            ITEMS = value
            ITEM_MAP.clear()
            for (item in ITEMS) {
                ITEM_MAP[item.BSSID] = item
            }
        }
}

val ScanResult.frequencyView: String
    get() = String.format("%1.1f GHz", frequency / 1000.0)

val ScanResult.strengthView: String
    get() = String.format("%d dbM", level)

val ScanResult.wifiStandardView: String
    @RequiresApi(Build.VERSION_CODES.R)
    get() {
        when (wifiStandard)
        {
            ScanResult.WIFI_STANDARD_11AC -> return "WIFI_STANDARD_11AC"
            ScanResult.WIFI_STANDARD_11AX -> return "WIFI_STANDARD_11AX"
            ScanResult.WIFI_STANDARD_11N -> return "WIFI_STANDARD_11N"
            ScanResult.WIFI_STANDARD_LEGACY -> return "WIFI_STANDARD_LEGACY"
        }
        return "WIFI_STANDARD_UNKNOWN"
    }

@ExperimentalUnsignedTypes
val ScanResult.detailView: String
    get() {
        val sb = StringBuilder()
        with(sb) {
            append("SSID:\n\t$SSID\n\n")
            append("BSSID:\n\t$BSSID\n\n")
            append("Capabilities:\n\t${capabilities.replace("][", "]\n\t[")}\n\n")
            append("Frequency:\n\t$frequencyView\n\n")
            append("Strength:\n\t$strengthView\n\n")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            with(sb) {
                append("API Level ${Build.VERSION_CODES.M}\n\n")
                append(String.format("centerFreq0:\n\t%d MHz\n\n", centerFreq0))
                append(String.format("centerFreq1:\n\t%d MHz\n\n", centerFreq1))
                append(String.format("Channel Width:\n\t%d MHz\n\n", channelWidth))
                append("Passpoint operator name:\n\t$operatorFriendlyName\n\n")
                append("Venue name:\n\t$venueName\n\n")
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            with(sb) {
                append("API Level ${Build.VERSION_CODES.R}\n\n")
                append("Wi-Fi Standard:\n\t$wifiStandardView\n\n")
                append("Information Elements:\n")
            }
            for (item in informationElements) {
                with(sb) {
                    append("\n")
                    append(String.format("\tElement ID: %d\n", item.id))
                    append(String.format("\tElement ID Extension: %d\n", item.idExt))
                    append("\tBytes: ${item.bytesHex}\n")
                }
            }
        }

        return sb.toString()
    }

@ExperimentalUnsignedTypes
val ScanResult.InformationElement.bytesHex: String
    @RequiresApi(Build.VERSION_CODES.R)
    get() {
        val data = ByteArray(bytes.remaining())
        bytes.get(data);
        return data.toUByteArray().joinToString(" ") { it.toString(16).padStart(2, '0') }
    }

val ScanResult.isWEP: Boolean
    get() = capabilities.contains("WEP")

val ScanResult.isPSK: Boolean
    get() = capabilities.contains("PSK")

val ScanResult.isEAP: Boolean
    get() = capabilities.contains("EAP")

val ScanResult.isOpen: Boolean
    get() = !(isWEP || isPSK || isEAP)

val ScanResult.isWPA3: Boolean
    get() = capabilities.contains("WPA3")

fun WifiManager.isConnected(item: ScanResult): Boolean {
    return item.BSSID == connectionInfo.bssid
}