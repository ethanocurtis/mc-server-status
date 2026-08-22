package com.mcserverstatus.app.network

import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.random.Random

/** Result of a successful SRV lookup: where to actually connect. */
data class SrvRecord(
    val target: String,
    val port: Int,
    val priority: Int,
    val weight: Int,
)

/**
 * Minimal DNS client for resolving `_minecraft._tcp.<host>` SRV records over
 * raw UDP, matching what the official Minecraft launcher does when a player
 * types a bare hostname with no port - servers like large networks commonly
 * publish an SRV record to route the base domain to a non-default port
 * without requiring players to type one.
 *
 * There's no portable way to read the device's configured DNS resolver from
 * plain `java.net`/Android APIs without a `Context`, so this queries a
 * couple of well-known public resolvers directly (same approach most mobile
 * Minecraft status checkers use). If every attempt fails or no record
 * exists, callers should fall back to the standard default port.
 */
object DnsSrvResolver {

    private val PUBLIC_RESOLVERS = listOf("8.8.8.8", "1.1.1.1")
    private const val DNS_PORT = 53
    private const val TYPE_SRV = 33
    private const val CLASS_IN = 1

    fun resolve(host: String, perAttemptTimeoutMs: Int = 1_500): SrvRecord? {
        if (isLiteralIpAddress(host)) return null
        val qname = "_minecraft._tcp.$host"
        for (resolver in PUBLIC_RESOLVERS) {
            val record = runCatching { queryOnce(resolver, qname, perAttemptTimeoutMs) }.getOrNull()
            if (record != null) return record
        }
        return null
    }

    private fun queryOnce(resolverIp: String, qname: String, timeoutMs: Int): SrvRecord? {
        DatagramSocket().use { socket ->
            socket.soTimeout = timeoutMs
            val id = Random.nextInt(0, 0x10000)
            val query = buildQuery(id, qname)
            val address = InetAddress.getByName(resolverIp)
            socket.send(DatagramPacket(query, query.size, address, DNS_PORT))

            val buffer = ByteArray(1500)
            val packet = DatagramPacket(buffer, buffer.size)
            socket.receive(packet)
            return parseSrvResponse(packet.data, packet.length, id)
        }
    }

    internal fun buildQuery(id: Int, qname: String): ByteArray {
        val out = ByteArrayOutputStream()
        writeUShort(out, id)
        writeUShort(out, 0x0100) // standard query, recursion desired
        writeUShort(out, 1) // QDCOUNT
        writeUShort(out, 0) // ANCOUNT
        writeUShort(out, 0) // NSCOUNT
        writeUShort(out, 0) // ARCOUNT
        for (label in qname.split(".")) {
            val bytes = label.toByteArray(Charsets.US_ASCII)
            out.write(bytes.size)
            out.write(bytes)
        }
        out.write(0) // root label
        writeUShort(out, TYPE_SRV)
        writeUShort(out, CLASS_IN)
        return out.toByteArray()
    }

    /** Parses a DNS response, returning the lowest-priority (ties broken by highest-weight) SRV answer, if any. */
    internal fun parseSrvResponse(data: ByteArray, length: Int, expectedId: Int): SrvRecord? {
        if (length < 12) return null
        if (readUShort(data, 0) != expectedId) return null
        val flags = readUShort(data, 2)
        if ((flags and 0x000F) != 0) return null // non-zero RCODE (e.g. NXDOMAIN): no record
        val qdCount = readUShort(data, 4)
        val anCount = readUShort(data, 6)
        if (anCount == 0) return null

        var offset = 12
        repeat(qdCount) {
            if (offset >= length) return null
            offset = skipName(data, offset)
            offset += 4 // QTYPE + QCLASS
        }

        var best: SrvRecord? = null
        var i = 0
        while (i < anCount && offset < length) {
            offset = skipName(data, offset)
            if (offset + 10 > length) break
            val type = readUShort(data, offset); offset += 2
            val cls = readUShort(data, offset); offset += 2
            offset += 4 // TTL
            val rdLength = readUShort(data, offset); offset += 2
            val rdStart = offset
            if (rdStart + rdLength > length) break

            if (type == TYPE_SRV && cls == CLASS_IN && rdLength >= 6) {
                val priority = readUShort(data, rdStart)
                val weight = readUShort(data, rdStart + 2)
                val port = readUShort(data, rdStart + 4)
                val target = readName(data, rdStart + 6).first
                val candidate = SrvRecord(target, port, priority, weight)
                val current = best
                if (current == null ||
                    candidate.priority < current.priority ||
                    (candidate.priority == current.priority && candidate.weight > current.weight)
                ) {
                    best = candidate
                }
            }
            offset = rdStart + rdLength
            i++
        }
        return best
    }

    private fun skipName(data: ByteArray, startOffset: Int): Int = readName(data, startOffset).second

    /**
     * Reads a (possibly compressed) DNS name starting at [startOffset].
     * Returns the decoded name and the offset immediately after it *in the
     * original stream* (i.e. after a 2-byte pointer, not after wherever the
     * pointer jumped to).
     */
    private fun readName(data: ByteArray, startOffset: Int): Pair<String, Int> {
        val labels = mutableListOf<String>()
        var offset = startOffset
        var returnOffset = -1
        var jumps = 0

        while (offset < data.size) {
            val len = data[offset].toInt() and 0xFF
            when {
                len == 0 -> {
                    if (returnOffset < 0) returnOffset = offset + 1
                    break
                }
                (len and 0xC0) == 0xC0 -> {
                    if (offset + 1 >= data.size) break
                    if (returnOffset < 0) returnOffset = offset + 2
                    jumps++
                    if (jumps > 20) break // guard against pointer loops in a malicious/corrupt response
                    offset = ((len and 0x3F) shl 8) or (data[offset + 1].toInt() and 0xFF)
                }
                else -> {
                    offset += 1
                    if (offset + len > data.size) break
                    labels += String(data, offset, len, Charsets.US_ASCII)
                    offset += len
                }
            }
        }
        return labels.joinToString(".") to (if (returnOffset >= 0) returnOffset else offset)
    }

    private fun readUShort(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)

    private fun writeUShort(out: ByteArrayOutputStream, value: Int) {
        out.write((value ushr 8) and 0xFF)
        out.write(value and 0xFF)
    }

    private fun isLiteralIpAddress(host: String): Boolean {
        if (host.contains(":")) return true // IPv6 literal
        return Regex("^\\d{1,3}(\\.\\d{1,3}){3}$").matches(host)
    }
}
