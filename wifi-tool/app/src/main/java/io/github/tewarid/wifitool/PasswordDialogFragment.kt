package io.github.tewarid.wifitool

import android.app.AlertDialog
import android.app.Dialog
import android.net.wifi.ScanResult
import android.os.Bundle
import android.widget.EditText
import androidx.fragment.app.DialogFragment

class PasswordDialogFragment(private val item: ScanResult) : DialogFragment() {

    interface PasswordDialogListener {
        fun onPasswordDialogResult(ssid: String, password: String)
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
                    (activity as? PasswordDialogListener)?.onPasswordDialogResult(ssid, password)
                }
                .setNegativeButton("Cancel") { _, _ ->
                    dialog?.cancel()
                }
            builder.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }

}