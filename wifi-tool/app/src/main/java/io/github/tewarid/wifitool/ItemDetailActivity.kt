package io.github.tewarid.wifitool

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.*
import android.net.wifi.ScanResult
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.widget.Button
import android.widget.EditText
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar


/**
 * An activity representing a single Item detail screen. This
 * activity is only used on narrow width devices. On tablet-size devices,
 * item details are presented side-by-side with a list of items
 * in a [ItemListActivity].
 */
class ItemDetailActivity : AppCompatActivity(), PasswordDialogFragment.PasswordDialogListener {

    private lateinit var item: ScanResult
    private lateinit var wifiManager: WifiManager
    private lateinit var connectView: Button
    @Suppress("DEPRECATION")
    private var wifiConfig: WifiConfiguration? = null
    private lateinit var connectivityManager: ConnectivityManager
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    @ExperimentalUnsignedTypes
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_item_detail)
        setSupportActionBar(findViewById(R.id.detail_toolbar))

        item = ScanResultContent.ITEM_MAP[intent.getStringExtra(ItemDetailFragment.ARG_ITEM_ID)]
            ?: return

        findViewById<CollapsingToolbarLayout>(R.id.toolbar_layout)?.title = item.SSID

        findViewById<FloatingActionButton>(R.id.fab).setOnClickListener { view ->
            val clipboard: ClipboardManager =
                getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("", item.detailView)
            clipboard.setPrimaryClip(clip)
            Snackbar.make(view, "Text copied to clipboard", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()
        }

        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        connectView = findViewById(R.id.connect)
        connectView.isEnabled = !wifiManager.isConnected(item) && (item.isOpen || item.isPSK)
        connectView.setOnClickListener {
            if (item.isOpen && item.SSID != "") {
                connect(item)
            } else {
                val dialogFragment = PasswordDialogFragment(item)
                dialogFragment.show(supportFragmentManager, "password")
            }
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        if (savedInstanceState == null) {
            // Create the detail fragment and add it to the activity
            // using a fragment transaction.
            val fragment = ItemDetailFragment().apply {
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
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onDestroy() {
        super.onDestroy()
        if (networkCallback != null) {
            connectivityManager.unregisterNetworkCallback(networkCallback as ConnectivityManager.NetworkCallback)
        } else if (wifiConfig != null) {
            @Suppress("DEPRECATION")
            (wifiConfig?.networkId?.let { wifiManager.removeNetwork(it) })
        }
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
        wifiConfig = WifiConfiguration()
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
        var id = findNetworkId(network, ssid)
        if (id == -1) {
            if (wifiManager.addNetwork(wifiConfig) == -1) {
                return
            }
            id = findNetworkId(network, ssid)
        } else {
            wifiConfig = null
        }
        wifiManager.disconnect()
        if (wifiManager.enableNetwork(id, true)) {
            connectView.isEnabled = false
            wifiConfig?.networkId = id
        } else {
            wifiConfig = null
        }
    }

    @SuppressLint("MissingPermission")
    private fun findNetworkId(network: ScanResult, ssid: String?): Int {
        for (item in wifiManager.configuredNetworks) {
            if (item.SSID == wifiConfig?.SSID) {
                return item.networkId
            }
        }
        return -1
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun requestNetwork(network: ScanResult, ssid: String?, password: String?) {
        val specifierBuilder = WifiNetworkSpecifier.Builder()
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
                if (wifiManager.isConnected(item)) {
                    connectView.isEnabled = false
                } else {
                    networkCallback = null
                }
            }
        }
        connectivityManager.requestNetwork(request,
            networkCallback as ConnectivityManager.NetworkCallback
        );
    }

    override fun onOptionsItemSelected(item: MenuItem) =
        when (item.itemId) {
            android.R.id.home -> {

                // This ID represents the Home or Up button. In the case of this
                // activity, the Up button is shown. For
                // more details, see the Navigation pattern on Android Design:
                //
                // http://developer.android.com/design/patterns/navigation.html#up-vs-back

                navigateUpTo(Intent(this, ItemListActivity::class.java))

                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    override fun onPositiveResult(ssid: String, password: String) {
        connect(item, ssid, password)
    }
}

class PasswordDialogFragment(private val item: ScanResult) : DialogFragment() {

    interface PasswordDialogListener {
        fun onPositiveResult(ssid: String, password: String)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            val builder = AlertDialog.Builder(it)
            val inflater = requireActivity().layoutInflater;
            val customView = inflater.inflate(R.layout.dialog_password, null)
            val ssidView = customView.findViewById<EditText>(R.id.ssid)
            ssidView.setText(item.SSID)
            val passwordView = customView.findViewById<EditText>(R.id.password)
            builder.setView(customView)
                .setPositiveButton("Join") { _, _ ->
                    val ssid = ssidView.text.toString()
                    val password = passwordView.text.toString()
                    (activity as? ItemDetailActivity)?.onPositiveResult(ssid, password)
                }
                .setNegativeButton("Cancel") { _, _ ->
                    dialog?.cancel()
                }
            builder.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }

}