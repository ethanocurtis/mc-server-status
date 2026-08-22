package com.mcserverstatus.app.network

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.charset.StandardCharsets
import kotlin.random.Random

/**
 * Implements a RakNet "Unconnected Ping" against a Minecraft Bedrock Edition
 * server (UDP, default port 19132). Bedrock uses RakNet rather than the raw
 * TCP protocol Java Edition uses, so this is a separate, much simpler
 * implementation.
 *
 * Protocol reference: https://wiki.vg/Raknet_Protocol#Unconnected_Ping
 */
object BedrockServerPinger {

    private const val ID_UNCONNECTED_PING = 0x01
    private const val ID_UNCONNECTED_PONG = 0x1C.toByte()

    // Fixed 16-byte "magic" value defined by the RakNet protocol.
    private val RAKNET_MAGIC = byteArrayOf(
        0x00, 0xFF.toByte(), 0xFF.toByte(), 0x00, 0xFE.toByte(), 0xFE.toByte(), 0xFE.toByte(), 0xFE.toByte(),
        0xFD.toByte(), 0xFD.toByte(), 0xFD.toByte(), 0xFD.toByte(), 0x12, 0x34, 0x56, 0x78,
    )

    fun ping(host: String, port: Int, timeoutMs: Int): ServerStatusResult {
        return try {
            DatagramSocket().use { socket ->
                socket.soTimeout = timeoutMs

                val address = InetSocketAddress(host, port)
                val clientGuid = Random.nextLong()
                val start = System.nanoTime()

                val requestBody = ByteArrayOutputStream().apply {
                    write(ID_UNCONNECTED_PING.toInt())
                    writeLong(this, start) // echoed back as a ping timestamp; value is arbitrary
                    write(RAKNET_MAGIC)
                    writeLong(this, clientGuid)
                }.toByteArray()

                socket.send(DatagramPacket(requestBody, requestBody.size, address))

                val buffer = ByteArray(2048)
                val responsePacket = DatagramPacket(buffer, buffer.size)
                socket.receive(responsePacket)
                val latencyMs = (System.nanoTime() - start) / 1_000_000

                parsePong(responsePacket.data, responsePacket.length, latencyMs)
            }
        } catch (e: UnknownHostException) {
            ServerStatusResult.failure("Unknown host")
        } catch (e: SocketTimeoutException) {
            ServerStatusResult.failure("No response (timed out)")
        } catch (e: IOException) {
            ServerStatusResult.failure(e.message ?: "Connection error")
        } catch (e: Exception) {
            ServerStatusResult.failure(e.message ?: "Unknown error")
        }
    }

    private fun parsePong(data: ByteArray, length: Int, latencyMs: Long): ServerStatusResult {
        if (length < 1 || data[0] != ID_UNCONNECTED_PONG) {
            return ServerStatusResult.failure("Unexpected response from server")
        }
        // Layout: id(1) + timestamp(8) + serverGuid(8) + magic(16) + stringLength(2, big-endian) + string
        var offset = 1 + 8 + 8 + 16
        if (offset + 2 > length) return ServerStatusResult.failure("Malformed response from server")
        val strLen = ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
        offset += 2
        if (offset + strLen > length) return ServerStatusResult.failure("Malformed response from server")
        val identifier = String(data, offset, strLen, StandardCharsets.UTF_8)

        // Semicolon-delimited fields, e.g.:
        // MCPE;A Bedrock Server;686;1.21.30;5;20;1234567890;Bedrock level;Survival;1;19132;19133;
        val fields = identifier.split(";")
        val motdLine1 = fields.getOrNull(1)?.let { MotdFormatter.parseLegacyLine(it) } ?: emptyList()
        val protocolVersion = fields.getOrNull(2)?.toIntOrNull()
        val versionName = fields.getOrNull(3)
        val online = fields.getOrNull(4)?.toIntOrNull()
        val max = fields.getOrNull(5)?.toIntOrNull()
        val motdLine2 = fields.getOrNull(7)?.let { MotdFormatter.parseLegacyLine(it) } ?: emptyList()
        val gamemode = fields.getOrNull(8)

        return ServerStatusResult(
            success = true,
            latencyMs = latencyMs,
            motd = motdLine1,
            motdLine2 = motdLine2,
            playersOnline = online,
            playersMax = max,
            versionName = versionName,
            protocolVersion = protocolVersion,
            gamemode = gamemode,
        )
    }

    private fun writeLong(out: ByteArrayOutputStream, value: Long) {
        for (i in 7 downTo 0) {
            out.write(((value ushr (i * 8)) and 0xFF).toInt())
        }
    }
}
