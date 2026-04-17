package com.example.ritik_2.windowscontrol.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

/**
 * Wake-on-LAN magic-packet sender. Pure UDP — no agent involvement required.
 * MAC is tolerated in any common form (`AA:BB:CC:DD:EE:FF`, `aa-bb-cc-dd-ee-ff`, `AABBCCDDEEFF`).
 * If [broadcast] is null we pick the active IPv4 interface's broadcast address, falling back to
 * `255.255.255.255` (which many home routers silently drop — prefer the subnet broadcast).
 */
object WakeOnLan {

    private const val TAG = "WakeOnLan"
    private val MAC_RE = Regex("^[0-9A-Fa-f]{2}([:-]?[0-9A-Fa-f]{2}){5}$")

    fun isValidMac(mac: String?): Boolean = mac != null && MAC_RE.matches(mac)

    suspend fun wake(mac: String, broadcast: String? = null, port: Int = 9): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val macBytes = parseMac(mac)
                val packet = ByteArray(6 + 16 * 6).apply {
                    repeat(6) { this[it] = 0xFF.toByte() }
                    for (i in 0 until 16) System.arraycopy(macBytes, 0, this, 6 + i * 6, 6)
                }
                val target = broadcast?.takeIf { it.isNotBlank() }
                    ?.let { runCatching { InetAddress.getByName(it) }.getOrNull() }
                    ?: pickBroadcast()
                    ?: InetAddress.getByName("255.255.255.255")

                DatagramSocket().use { sock ->
                    sock.broadcast = true
                    sock.send(DatagramPacket(packet, packet.size, target, port))
                }
                Log.d(TAG, "sent magic packet to $mac via $target:$port")
                Unit
            }
        }

    private fun parseMac(mac: String): ByteArray {
        val hex = mac.replace(":", "").replace("-", "")
        require(hex.length == 12) { "MAC must be 12 hex chars, got '$mac'" }
        return ByteArray(6) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }

    private fun pickBroadcast(): InetAddress? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList().asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.interfaceAddresses.asSequence() }
            .firstOrNull { it.address is Inet4Address && it.broadcast != null }
            ?.broadcast
    }.getOrNull()
}
