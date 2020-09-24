package io.github.tewarid.wifitool

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton


/**
 * An activity representing a list of Wi-Fi networks. This activity
 * has different presentations for handset and tablet-size devices. On
 * handsets, the activity presents a list of items, which when touched,
 * lead to a [ItemDetailActivity] representing
 * item details. On tablets, the activity presents the list of items and
 * item details side-by-side using two vertical panes.
 */
@RequiresApi(Build.VERSION_CODES.M)
class ItemListActivity : AppCompatActivity() {

    private lateinit var wifiManager: WifiManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_item_list)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.title = title

        findViewById<FloatingActionButton>(R.id.fab).setOnClickListener {
            showScanResults()
        }

        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            val myIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + BuildConfig.APPLICATION_ID))
            startActivity(myIntent)
            return
        }

        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiScanReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
                if (success) {
                    showScanResults()
                }
            }
        }
        val intentFilter = IntentFilter()
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        applicationContext.registerReceiver(wifiScanReceiver, intentFilter)
        startScan()
    }

    private fun startScan() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            wifiManager.startScan()
        } else {
            showScanResults()
        }
    }

    private fun showScanResults() {
        val results = wifiManager?.scanResults
        ScanResultContent.items = results;
        val recyclerView: RecyclerView = findViewById(R.id.item_list)
        recyclerView.adapter = SimpleItemRecyclerViewAdapter(this, results)
    }

    class SimpleItemRecyclerViewAdapter(
        private val parentActivity: ItemListActivity,
        private val values: List<ScanResult>
    ) : RecyclerView.Adapter<SimpleItemRecyclerViewAdapter.ViewHolder>() {

        private val onClickListener: View.OnClickListener

        init {
            onClickListener = View.OnClickListener { v ->
                val item = v.tag as ScanResult
                val intent = Intent(v.context, ItemDetailActivity::class.java).apply {
                    putExtra(ItemDetailFragment.ARG_ITEM_ID, item.BSSID)
                }
                v.context.startActivity(intent)
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_list_content, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = values[position]
            holder.idView.text = item.SSID
            holder.frequencyView.text = item.frequencyView
            holder.strengthView.text = item.strengthView
            holder.securityView.visibility = if (item.isOpen) View.VISIBLE else View.GONE
            val info = parentActivity.wifiManager.connectionInfo
            holder.connectedView.visibility = if (item.BSSID == info?.bssid) View.VISIBLE else View.GONE

            with(holder.itemView) {
                tag = item
                setOnClickListener(onClickListener)
            }
        }

        override fun getItemCount() = values.size

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val idView: TextView = view.findViewById(R.id.id_text)
            val frequencyView: TextView = view.findViewById(R.id.frequency)
            val strengthView: TextView = view.findViewById(R.id.strength)
            val securityView: TextView = view.findViewById(R.id.security)
            val connectedView: TextView = view.findViewById(R.id.connected)
        }
    }
}