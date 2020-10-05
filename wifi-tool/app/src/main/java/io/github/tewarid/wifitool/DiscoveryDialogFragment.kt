package io.github.tewarid.wifitool

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.widget.EditText
import androidx.fragment.app.DialogFragment

class DiscoveryDialogFragment() : DialogFragment() {

    interface DiscoveryDialogListener {
        fun onDiscoveryDialogResult(serviceType: String, protocol: String)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            val builder = AlertDialog.Builder(it)
            val inflater = requireActivity().layoutInflater;
            val customView = inflater.inflate(R.layout.dialog_discovery, null)
            val serviceTypeView = customView.findViewById<EditText>(R.id.serviceType)
            val protocolView = customView.findViewById<EditText>(R.id.protocol)
            builder.setView(customView)
                .setPositiveButton("Start") { _, _ ->
                    val serviceType = serviceTypeView.text.toString()
                    val protocol = protocolView.text.toString()
                    (activity as? DiscoveryDialogListener)?.onDiscoveryDialogResult(serviceType, protocol)
                }
                .setNegativeButton("Cancel") { _, _ ->
                    dialog?.cancel()
                }
            builder.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }

}