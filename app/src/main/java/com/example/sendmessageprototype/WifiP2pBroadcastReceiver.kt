package com.example.sendmessageprototype

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pManager
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.core.content.IntentCompat

class WifiP2pBroadcastReceiver(
    private val manager: WifiP2pManager?,
    private val channel: WifiP2pManager.Channel?,
    private val viewModel: WifiP2pViewModel,
    private val activity: MainActivity
) : BroadcastReceiver() {

    @RequiresPermission(anyOf = [
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.NEARBY_WIFI_DEVICES
    ])
    override fun onReceive(context: Context, intent: Intent) {
        val action: String? = intent.action
        when (action) {
//            State changed
            WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
//                Este when y condicional estan para asegurar que los permisos  estan concedidos
                when (state) {
                    WifiP2pManager.WIFI_P2P_STATE_ENABLED -> {
//                        Toast.makeText(context, "Wifi P2P esta activado", Toast.LENGTH_LONG).show()
//                        Actualiza el viewModel con la lista de peers
//                        Deberia filtrar aqui los devices que no son WiChat? (NO, mejor en otro componente que se encargue de ello)
                        manager?.requestPeers(channel) { peerList ->
                            peerList?.deviceList?.let { devices ->
                                viewModel.updatePeerList(devices.toList())
                            }
                        }
                    }
                    WifiP2pManager.WIFI_P2P_STATE_DISABLED -> {
                        Toast.makeText(context, "Wifi P2P is disabled", Toast.LENGTH_LONG).show()
                    }
                }
            }

//            Peers changed
            WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                manager?.requestPeers(channel) { peers: WifiP2pDeviceList? ->
                    peers?.let {
                        viewModel.updatePeerList(it.deviceList.toList())
                        // Muestra los nombres de los dispositivos encontrados. Borrar tras debuging
                        val deviceNames = it.deviceList.joinToString(", ") { device -> device.deviceName }
                        val message = if (deviceNames.isNotEmpty()) {
                            "Peers found: $deviceNames"
                        } else {
                            "No peers found"
                        }
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
//            Connection changed: NOW controlled in MainActivity. Delete after checking all work.
//            WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
//                val networkInfo = IntentCompat.getParcelableExtra(intent, WifiP2pManager.EXTRA_NETWORK_INFO, NetworkInfo::class.java)
//                if (networkInfo?.isConnected == true) {
//                    manager?.requestConnectionInfo(channel) { info ->
//                        viewModel.updateConnectionInfo(info)
//                    }
//                } else {
//                    viewModel.updateConnectionInfo(null)
//                }
//            }
        }
    }
}