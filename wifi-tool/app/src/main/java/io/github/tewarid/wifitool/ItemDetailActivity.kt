package io.github.tewarid.wifitool

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.*
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.ScanResult
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import java.lang.StringBuilder


/**
 * An activity representing a single Item detail screen. This
 * activity is only used on narrow width devices. On tablet-size devices,
 * item details are presented side-by-side with a list of items
 * in a [ItemListActivity].
 */
class ItemDetailActivity : AppCompatActivity(), PasswordDialogFragment.PasswordDialogListener, DiscoveryDialogFragment.DiscoveryDialogListener {

    private lateinit var scanResult: ScanResult
    private lateinit var wifiManager: WifiManager
    @Suppress("DEPRECATION")
    private var wifiConfig: WifiConfiguration? = null
    private lateinit var connectivityManager: ConnectivityManager
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private lateinit var fragment: ItemDetailFragment
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private lateinit var nsdManager: NsdManager
    private val SERVICE_MAP: MutableMap<String, NsdServiceInfo> = HashMap()
    private var multicastLock: WifiManager.MulticastLock? = null

    @ExperimentalUnsignedTypes
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_item_detail)
        setSupportActionBar(findViewById(R.id.detail_toolbar))

        scanResult = ScanResultContent.ITEM_MAP[intent.getStringExtra(ItemDetailFragment.ARG_ITEM_ID)]
            ?: return

        this.title = scanResult.SSID

        findViewById<FloatingActionButton>(R.id.fab).setOnClickListener { view ->
            val clipboard: ClipboardManager =
                getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("", fragment.getDetails())
            clipboard.setPrimaryClip(clip)
            Snackbar.make(view, "Text copied to clipboard", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()
        }

        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        if (savedInstanceState == null) {
            fragment = ItemDetailFragment().apply {
                arguments = Bundle().apply {
                    putString(
                        ItemDetailFragment.ARG_ITEM_ID,
                        intent.getStringExtra(ItemDetailFragment.ARG_ITEM_ID)
                    )
                }
            }

            supportFragmentManager.beginTransaction()
                    .add(R.id.item_detail_container, fragment)
                    .commit()
        } else {
            fragment = supportFragmentManager.findFragmentById(R.id.item_detail_container) as ItemDetailFragment
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onDestroy() {
        super.onDestroy()
        leaveNetwork()
        stopServiceDiscovery()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.detail_menu, menu)
        return true
    }

    private fun connect(item: ScanResult, ssid: String? = null, password: String? = null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            requestNetwork(item, ssid, password)
        } else {
            joinNetwork(item, ssid, password)
        }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private fun joinNetwork(network: ScanResult, ssid: String?, password: String?) {
        if (wifiConfig != null) return
        wifiConfig = WifiConfiguration()
        wifiConfig?.BSSID = network.BSSID
        if (network.SSID == "") {
            wifiConfig?.SSID = "\"${ssid}\""
            wifiConfig?.hiddenSSID = true
        } else {
            wifiConfig?.SSID = "\"${network.SSID}\""
        }
        if (password != null) {
            wifiConfig?.allowedKeyManagement?.set(WifiConfiguration.KeyMgmt.WPA_PSK)
            wifiConfig?.preSharedKey = "\"${password}\""
        } else {
            wifiConfig?.allowedKeyManagement?.set(WifiConfiguration.KeyMgmt.NONE)
        }
        var id = findNetworkId()
        if (id == -1) {
            if (wifiManager.addNetwork(wifiConfig) == -1) {
                return
            }
            id = findNetworkId()
        } else {
            wifiConfig = null
        }
        wifiManager.disconnect()
        if (wifiManager.enableNetwork(id, true)) {
            invalidateOptionsMenu()
            wifiConfig?.networkId = id
        } else {
            wifiConfig = null
        }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private fun findNetworkId(): Int {
        for (item in wifiManager.configuredNetworks) {
            if (item.SSID == wifiConfig?.SSID) {
                return item.networkId
            }
        }
        return -1
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun requestNetwork(network: ScanResult, ssid: String?, password: String?) {
        if (networkCallback != null) return
        val specifierBuilder = WifiNetworkSpecifier.Builder()
        specifierBuilder.setBssid(MacAddress.fromString(network.BSSID))
        if (network.SSID == "") {
            if (ssid != null && ssid != "") {
                specifierBuilder.setSsid(ssid.toString())
                specifierBuilder.setIsHiddenSsid(true)
            } else {
                return
            }
        } else {
            specifierBuilder.setSsid(network.SSID)
        }
        if (password != null && password != "") {
            if (network.isWPA3) {
                specifierBuilder.setWpa3Passphrase(password)
            } else {
                specifierBuilder.setWpa2Passphrase(password)
            }
        }
        val specifier = specifierBuilder.build()
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(specifier)
            .build()
        connectivityManager = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager;
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (wifiManager.isConnected(scanResult)) {
                    invalidateOptionsMenu()
                } else {
                    networkCallback = null
                }
            }
        }
        connectivityManager.requestNetwork(request, networkCallback as ConnectivityManager.NetworkCallback);
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun leaveNetwork() {
        if (networkCallback != null) {
            connectivityManager.unregisterNetworkCallback(networkCallback as ConnectivityManager.NetworkCallback)
        } else if (wifiConfig != null) {
            @Suppress("DEPRECATION")
            (wifiConfig?.networkId?.let { wifiManager.removeNetwork(it) })
        }
    }

    override fun onOptionsItemSelected(item: MenuItem) =
        when (item.itemId) {
            android.R.id.home -> {
                navigateUpTo(Intent(this, ItemListActivity::class.java))
                true
            }
            R.id.connect -> {
                if (scanResult.isOpen && scanResult.SSID != "") {
                    connect(scanResult)
                } else {
                    val dialogFragment = PasswordDialogFragment(scanResult)
                    dialogFragment.show(supportFragmentManager, "password")
                }
                true
            }
            R.id.discover -> {
                val dialogFragment = DiscoveryDialogFragment()
                dialogFragment.show(supportFragmentManager, "discovery")
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        super.onPrepareOptionsMenu(menu)
        menu?.findItem(R.id.connect)?.isEnabled = !wifiManager.isConnected(scanResult) && (scanResult.isOpen || scanResult.isPSK)
        menu?.findItem(R.id.discover)?.isEnabled = wifiManager.isConnected(scanResult) && discoveryListener == null
        return true
    }

    override fun onPasswordDialogResult(ssid: String, password: String) {
        connect(scanResult, ssid, password)
    }

    override fun onDiscoveryDialogResult(securityType: String, protocol: String) {
        startServiceDiscovery(securityType, protocol)
        invalidateOptionsMenu()
    }

    private fun startServiceDiscovery(serviceType: String, protocol: String) {
        multicastLock = wifiManager.createMulticastLock("WIFI_TOOL")
        multicastLock?.setReferenceCounted(true)
        multicastLock?.acquire()
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(service: NsdServiceInfo) {
                val resolveListener = object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) { }
                    override fun onServiceResolved(serviceInfo: NsdServiceInfo?) {
                        if (serviceInfo != null) {
                            addService(serviceInfo)
                        }
                    }
                }
                nsdManager.resolveService(service, resolveListener)
            }
            override fun onServiceLost(service: NsdServiceInfo) {
                removeService(service)
            }
            override fun onDiscoveryStarted(serviceType: String?) { }
            override fun onDiscoveryStopped(serviceType: String) { }
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                discoveryListener = null
            }
            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) { }
        }
        nsdManager = getSystemService(Context.NSD_SERVICE) as NsdManager
        nsdManager.discoverServices("${serviceType}.${protocol}", NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    private fun addService(serviceInfo: NsdServiceInfo) {
        if (SERVICE_MAP.containsKey(serviceInfo.serviceName)) {
            SERVICE_MAP.remove(serviceInfo.serviceName)
        }
        SERVICE_MAP[serviceInfo.serviceName] = serviceInfo
        refreshDetails()
    }

    private fun removeService(serviceInfo: NsdServiceInfo) {
        if (SERVICE_MAP.remove(serviceInfo.serviceName) != null) {
            refreshDetails()
        }
    }

    @ExperimentalUnsignedTypes
    private fun refreshDetails() {
        val sb = StringBuilder()
            .append("\nDiscovered Network Service(s):\n")
        for (item in SERVICE_MAP.values) {
            with(sb) {
                append("\n\tService: ${item.serviceType}\n")
                append("\tName: ${item.serviceName}\n")
                append("\tHost: ${item.host}\n")
                append("\tPort: ${item.port}\n")
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
                    return
                append("\tAttributes:\n")
                for (key in item.attributes.keys) {
                    val bytesHex = item.attributes[key]?.toUByteArray()?.joinToString(" ") { it.toString(16).padStart(2, '0') }
                    append("\t\t$key: ${bytesHex}\n")
                }
            }
        }
        fragment.setDetails(scanResult.detailView + sb.toString())
    }

    private fun stopServiceDiscovery() {
        if (discoveryListener != null) {
            nsdManager.stopServiceDiscovery(discoveryListener)
            multicastLock?.release()
            multicastLock = null
        }
        SERVICE_MAP.clear()
    }
}