package com.example.ritik_2.windowscontrol.network

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * UDP discovery client. Broadcasts "PCAGENT_DISCOVER_V1" to the LAN and
 * emits each agent reply as a [DiscoveredAgent] over a Flow.
 *
 * Mirrors the server protocol implemented in agent_v10.py `discovery_worker`.
 */
class PcLanScanner(private val context: Context) {

    data class DiscoveredAgent(
        val pcName    : String,
        val host      : String,
        val ip        : String,
        val port      : Int,
        val streamPort: Int,
        val os        : String,
        val version   : String,
        val connected : Int
    )

    companion object {
        const val DISCOVERY_PORT = 5002
        const val PROBE_PAYLOAD  = "PCAGENT_DISCOVER_V1"
    }

    /**
     * Broadcasts a probe and listens for [durationMs] milliseconds.
     * Emits each unique agent exactly once (keyed by ip:port).
     */
    fun scan(durationMs: Long = 4_000L): Flow<DiscoveredAgent> = channelFlow {
        val seen = mutableSetOf<String>()
        val multicastLock = try {
            val wifi = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wifi?.createMulticastLock("PcAgentDiscover")?.apply {
                setReferenceCounted(false); acquire()
            }
        } catch (_: Exception) { null }

        val socket = DatagramSocket().apply {
            broadcast = true
            soTimeout = 500
        }

        // Receiver coroutine
        val receiver = launch(Dispatchers.IO) {
            val buf = ByteArray(2048)
            while (isActive) {
                val packet = DatagramPacket(buf, buf.size)
                try {
                    socket.receive(packet)
                    val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    val obj  = runCatching { JSONObject(text) }.getOrNull() ?: continue
                    if (obj.optString("proto") != "PCAGENT") continue
                    val ip   = obj.optString("ip", packet.address?.hostAddress.orEmpty())
                    val port = obj.optInt("port", 5000)
                    val key  = "$ip:$port"
                    if (key in seen) continue
                    seen += key
                    trySend(DiscoveredAgent(
                        pcName     = obj.optString("pc_name", obj.optString("host", ip)),
                        host       = obj.optString("host", ip),
                        ip         = ip,
                        port       = port,
                        streamPort = obj.optInt("stream_port", 5001),
                        os         = obj.optString("os", ""),
                        version    = obj.optString("version", ""),
                        connected  = obj.optInt("connected", 0)
                    ))
                } catch (_: java.net.SocketTimeoutException) {
                    // normal — loop to check isActive
                } catch (_: Exception) {
                    // swallow malformed packets
                }
            }
        }

        // Send a few probes so we survive packet loss
        val payload = PROBE_PAYLOAD.toByteArray(Charsets.UTF_8)
        val targets = broadcastAddresses(context)
        withContext(Dispatchers.IO) {
            repeat(3) {
                for (addr in targets) {
                    runCatching {
                        socket.send(DatagramPacket(
                            payload, payload.size,
                            InetAddress.getByName(addr), DISCOVERY_PORT))
                    }
                }
                delay(400)
            }
        }

        delay(durationMs)
        receiver.cancel()
        runCatching { socket.close() }
        runCatching { multicastLock?.release() }
    }

    /** Derive useful broadcast targets from the current Wi-Fi DHCP info. */
    private fun broadcastAddresses(ctx: Context): List<String> {
        val result = mutableListOf("255.255.255.255")
        try {
            val wifi = ctx.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return result
            @Suppress("DEPRECATION")
            val dhcp = wifi.dhcpInfo ?: return result
            val mask = dhcp.netmask
            val ip   = dhcp.ipAddress
            if (mask != 0 && ip != 0) {
                val broadcast = (ip and mask) or mask.inv()
                val bytes = ByteArray(4) { i -> (broadcast shr (i * 8) and 0xFF).toByte() }
                result.add(bytes.joinToString(".") { (it.toInt() and 0xFF).toString() })
            }
        } catch (_: Exception) { /* ignore */ }
        return result.distinct()
    }
}
