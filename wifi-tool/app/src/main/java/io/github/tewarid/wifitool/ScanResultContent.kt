package io.github.tewarid.wifitool

import android.net.wifi.ScanResult

object ScanResultContent {

    lateinit var ITEMS: List<ScanResult>

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

val ScanResult.strength: String
    get() = String.format("%d dbM", level)