package com.example.WiChat.transport

import android.net.wifi.p2p.WifiP2pManager
import android.util.Log
import java.lang.reflect.Method
import java.lang.reflect.Proxy

object ReflectionUtils {
    private const val TAG = "ReflectionUtils"
    fun clearPersistentGroups(manager: WifiP2pManager, channel: WifiP2pManager.Channel) {
        try {
//            get android's hidden methods
            val methods: Array<Method> = WifiP2pManager::class.java.methods
            var deleteMethod: Method? = null
            var requestMethod: Method? = null
            for (method in methods) {
                if (method.name == "deletePersistentGroup") deleteMethod = method
                if (method.name == "requestPersistentGroupInfo") requestMethod = method
            }
            if (deleteMethod == null || requestMethod == null) return
//            proxy to access android class
            val interfaceClass = Class.forName("android.net.wifi.p2p.WifiP2pManager\$PersistentGroupInfoListener")
            val proxy = Proxy.newProxyInstance(
                interfaceClass.classLoader,
                arrayOf(interfaceClass)
            ) { _, method, args ->
                if (method.name == "onPersistentGroupInfoAvailable") {
                    val groups = args[0]
                    val getGroupList = groups?.javaClass?.getMethod("getGroupList")
                    val list = getGroupList?.invoke(groups) as? Collection<*>
                    list?.forEach { group ->
                        try {
                            val netIdField = group!!.javaClass.getField("netId")
                            val netId = netIdField.get(group) as Int
                            deleteMethod.invoke(manager, channel, netId, null)
                            Log.d(TAG, "Deleted persistent group: $netId")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to delete group", e)
                        }
                    }
                }
                null
            }
//            get persistent groups list
            requestMethod.invoke(manager, channel, proxy)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear persistent groups (FAILURE)", e)
        }
    }
}