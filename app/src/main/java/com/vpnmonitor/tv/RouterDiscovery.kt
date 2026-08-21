package com.vpnmonitor.tv

import android.content.Context
import android.net.wifi.WifiManager

/**
 * Авто-поиск роутера: берёт IP шлюза Wi-Fi, к которому подключён ТВ.
 * Роутер (OpenWrt) сам является шлюзом сети — сервер мониторинга
 * слушает на нём порт 17463.
 */
object RouterDiscovery {
    private const val PORT = 17463

    /**
     * Возвращает base URL роутера вида http://<шлюз>:17463
     * или null, если шлюз не определён.
     */
    fun discover(context: Context): String? {
        return try {
            val wifi = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as? WifiManager
                ?: return null
            val dhcp = wifi.dhcpInfo ?: return null
            val gateway = dhcp.gateway
            if (gateway == 0) null
            else {
                val ip = String.format(
                    "%d.%d.%d.%d",
                    gateway and 0xff,
                    (gateway shr 8) and 0xff,
                    (gateway shr 16) and 0xff,
                    (gateway shr 24) and 0xff
                )
                "http://$ip:$PORT"
            }
        } catch (e: Exception) {
            null
        }
    }
}
