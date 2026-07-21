package com.example.sendmessageprototype

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pManager
import android.widget.Toast
import androidx.annotation.RequiresPermission

class WifiP2pBroadcastReceiver(
    private val manager: WifiP2pManager?,
    private val channel: WifiP2pManager.Channel?,
    private val viewModel: WifiP2pViewModel,
    private val activity: MainActivity
) : BroadcastReceiver() {

    // Anadir estos permisos al manifes? Estos son para el peer discovery
    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.NEARBY_WIFI_DEVICES])
    override fun onReceive(context: Context, intent: Intent) {
        val action: String? = intent.action
        when (action) {
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
//                        Toast.makeText(context, "Wifi P2P esta desactivado", Toast.LENGTH_LONG).show()
                    }
                }
            }

            WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                manager?.requestPeers(channel) { peers: WifiP2pDeviceList? ->
                    peers?.let {
                        viewModel.updatePeerList(it.deviceList.toList())
//                        activity.updatePeerList(it.deviceList.toList())
                        // Muestra los nombres de los dispositivos encontrados
                        // cambiar esto para que actualice la lista de peers en la UI
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
        }
    }
}