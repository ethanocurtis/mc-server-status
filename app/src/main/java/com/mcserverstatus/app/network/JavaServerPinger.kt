package com.mcserverstatus.app.network

import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.random.Random

/**
 * Implements the Minecraft Java Edition "Server List Ping" protocol
 * (the modern, post-1.7 handshake-based version) directly over a raw TCP
 * socket - the same mechanism the vanilla multiplayer server list uses.
 *
 * Protocol reference: https://wiki.vg/Server_List_Ping
 */
object JavaServerPinger {

    // Any recent protocol number works here: for a status request the server
    // ignores it and always reports its own real version/protocol.
    private const val HANDSHAKE_PROTOCOL_VERSION = 767
    private const val NEXT_STATE_STATUS = 1
    private const val MAX_RESPONSE_BYTES = 2_000_000

    fun ping(host: String, port: Int, timeoutMs: Int): ServerStatusResult {
        return try {
            Socket().use { socket ->
                socket.tcpNoDelay = true
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                socket.soTimeout = timeoutMs

                val out = socket.getOutputStream()
                val input = socket.getInputStream()

                sendHandshake(out, host, port)
                sendStatusRequest(out)

                val json = readStatusResponse(input)
                val latency = measurePing(out, input) ?: -1L

                parseStatusJson(json, latency.takeIf { it >= 0 })
            }
        } catch (e: UnknownHostException) {
            ServerStatusResult.failure("Unknown host")
        } catch (e: SocketTimeoutException) {
            ServerStatusResult.failure("Connection timed out")
        } catch (e: ConnectException) {
            ServerStatusResult.failure("Connection refused")
        } catch (e: IOException) {
            ServerStatusResult.failure(e.message ?: "Connection error")
        } catch (e: Exception) {
            ServerStatusResult.failure(e.message ?: "Unknown error")
        }
    }

    private fun sendHandshake(out: OutputStream, host: String, port: Int) {
        val body = ByteArrayOutputStream().apply {
            VarInt.write(this, 0x00)
            VarInt.write(this, HANDSHAKE_PROTOCOL_VERSION)
            VarInt.writeString(this, host)
            write((port ushr 8) and 0xFF)
            write(port and 0xFF)
            VarInt.write(this, NEXT_STATE_STATUS)
        }.toByteArray()
        writeFramedPacket(out, body)
    }

    private fun sendStatusRequest(out: OutputStream) {
        val body = ByteArrayOutputStream().apply { VarInt.write(this, 0x00) }.toByteArray()
        writeFramedPacket(out, body)
        out.flush()
    }

    private fun readStatusResponse(input: InputStream): String {
        val length = VarInt.read(input)
        if (length <= 0 || length > MAX_RESPONSE_BYTES) {
            throw IOException("Server sent an implausible response size ($length bytes)")
        }
        val packetBytes = VarInt.readFully(input, length)
        val packetStream = ByteArrayInputStream(packetBytes)
        val packetId = VarInt.read(packetStream)
        if (packetId != 0x00) throw IOException("Unexpected packet id in status response: $packetId")
        return VarInt.readString(packetStream, maxLength = MAX_RESPONSE_BYTES)
    }

    /**
     * Sends the optional follow-up "ping" packet and times the round trip for
     * an accurate latency reading. This is best-effort: some reverse proxies
     * close the connection right after the status response, so a failure here
     * simply falls back to no latency reading from this step (the caller
     * still has a coarse fallback available from the outer connection timer
     * if desired; we keep this focused and just report null on failure).
     */
    private fun measurePing(out: OutputStream, input: InputStream): Long? {
        return try {
            val payload = Random.nextLong()
            val start = System.nanoTime()
            val body = ByteArrayOutputStream().apply {
                VarInt.write(this, 0x01)
                writeLong(this, payload)
            }.toByteArray()
            writeFramedPacket(out, body)
            out.flush()

            val length = VarInt.read(input)
            if (length <= 0 || length > 32) return null
            val packetBytes = VarInt.readFully(input, length)
            val packetStream = ByteArrayInputStream(packetBytes)
            val packetId = VarInt.read(packetStream)
            if (packetId != 0x01) return null
            val echoed = readLong(packetStream)
            val elapsedMs = (System.nanoTime() - start) / 1_000_000
            if (echoed == payload) elapsedMs else null
        } catch (e: Exception) {
            null
        }
    }

    private fun parseStatusJson(json: String, latencyMs: Long?): ServerStatusResult {
        return try {
            val obj = JSONObject(json)

            val motd = MotdFormatter.parseDescription(if (obj.has("description")) obj.get("description") else null)

            val playersObj = obj.optJSONObject("players")
            val online = playersObj?.let { if (it.has("online")) it.optInt("online") else null }
            val max = playersObj?.let { if (it.has("max")) it.optInt("max") else null }
            val sample = mutableListOf<String>()
            playersObj?.optJSONArray("sample")?.let { array ->
                for (i in 0 until array.length()) {
                    val name = array.optJSONObject(i)?.optString("name")
                    if (!name.isNullOrBlank()) sample += name
                }
            }

            val versionObj = obj.optJSONObject("version")
            val versionName = versionObj?.optString("name")?.takeIf { it.isNotBlank() }
            val protocol = versionObj?.let { if (it.has("protocol")) it.optInt("protocol") else null }

            val favicon = obj.optString("favicon", "").takeIf { it.startsWith("data:image") }

            ServerStatusResult(
                success = true,
                latencyMs = latencyMs,
                motd = motd,
                playersOnline = online,
                playersMax = max,
                playerSample = sample,
                versionName = versionName,
                protocolVersion = protocol,
                faviconBase64 = favicon,
            )
        } catch (e: Exception) {
            ServerStatusResult.failure("Malformed response from server")
        }
    }

    private fun writeFramedPacket(out: OutputStream, body: ByteArray) {
        VarInt.write(out, body.size)
        out.write(body)
    }

    private fun writeLong(out: OutputStream, value: Long) {
        for (i in 7 downTo 0) {
            out.write(((value ushr (i * 8)) and 0xFF).toInt())
        }
    }

    private fun readLong(input: InputStream): Long {
        var result = 0L
        repeat(8) {
            val b = input.read()
            if (b == -1) throw IOException("Stream ended while reading long")
            result = (result shl 8) or (b.toLong() and 0xFF)
        }
        return result
    }
}
