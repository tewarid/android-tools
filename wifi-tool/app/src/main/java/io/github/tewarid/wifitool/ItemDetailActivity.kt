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
        connectView.isEnabled = (item.BSSID != wifiManager.connectionInfo.bssid) && (item.isOpen || item.isPSK)
        connectView.setOnClickListener {
            if (item.isOpen) {
                connect(item)
            } else {
                val dialogFragment = PasswordDialogFragment()
                dialogFragment.show(supportFragmentManager, "password")
            }
        }

        // Show the Up button in the action bar.
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // savedInstanceState is non-null when there is fragment state
        // saved from previous configurations of this activity
        // (e.g. when rotating the screen from portrait to landscape).
        // In this case, the fragment will automatically be re-added
        // to its container so we don"t need to manually add it.
        // For more information, see the Fragments API guide at:
        //
        // http://developer.android.com/guide/components/fragments.html
        //
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

    private fun connect(item: ScanResult, password: String? = null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            requestNetwork(item, password)
        } else {
            joinNetwork(item, password)
        }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private fun joinNetwork(network: ScanResult, password: String? = null) {
        val conf = WifiConfiguration()
        conf.SSID = "\"${network.SSID}\""
        if (password != null) {
            conf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
            conf.preSharedKey = "\"${password}\""
        } else {
            conf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
        }
        wifiManager.addNetwork(conf);
        for (item in wifiManager.configuredNetworks) {
            if (item.SSID == conf.SSID) {
                wifiManager.disconnect()
                if (wifiManager.enableNetwork(item.networkId, true)) {
                    connectView.isEnabled = false
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun requestNetwork(network: ScanResult, password: String? = null) {
        val specifierBuilder = WifiNetworkSpecifier.Builder()
            .setBssid(MacAddress.fromString(network.BSSID))
        if (password != null) {
            specifierBuilder.setWpa2Passphrase(password)
        }
        val specifier = specifierBuilder.build()
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(specifier)
            .build()
        val connectivityManager =
            applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager;
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                connectView.isEnabled = (item.BSSID != wifiManager.connectionInfo.bssid)
            }
        }
        connectivityManager.requestNetwork(request, networkCallback);
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

    override fun onPositiveResult(password: String) {
        connect(item, password)
    }
}

class PasswordDialogFragment : DialogFragment() {

    interface PasswordDialogListener {
        fun onPositiveResult(password: String)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            val builder = AlertDialog.Builder(it)
            val inflater = requireActivity().layoutInflater;
            val customView = inflater.inflate(R.layout.dialog_password, null)
            builder.setView(customView)
                .setPositiveButton("Join") { _, _ ->
                    val passwordView = customView.findViewById<EditText>(R.id.password)
                    val password = passwordView.text.toString()
                    (activity as? ItemDetailActivity)?.onPositiveResult(password)
                }
                .setNegativeButton("Cancel") { _, _ ->
                    dialog?.cancel()
                }
            builder.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }

}