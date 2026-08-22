package com.mcserverstatus.app.network

import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets

/**
 * Helpers for the variable-length integer encoding used throughout the
 * Minecraft Java Edition "Server List Ping" protocol, plus the small set of
 * primitive read/write helpers built on top of it.
 *
 * See: https://wiki.vg/Server_List_Ping
 */
internal object VarInt {

    private const val MAX_VARINT_BYTES = 5

    fun write(out: OutputStream, valueIn: Int) {
        var value = valueIn
        while (true) {
            if ((value and 0x7F.inv()) == 0) {
                out.write(value)
                return
            }
            out.write((value and 0x7F) or 0x80)
            value = value ushr 7
        }
    }

    fun read(input: InputStream): Int {
        var numRead = 0
        var result = 0
        while (true) {
            val readByte = input.read()
            if (readByte == -1) throw EOFException("Stream ended while reading VarInt")
            val value = readByte and 0x7F
            result = result or (value shl (7 * numRead))
            numRead++
            if (numRead > MAX_VARINT_BYTES) throw IOException("VarInt is too big")
            if ((readByte and 0x80) == 0) break
        }
        return result
    }

    fun writeString(out: OutputStream, value: String) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        write(out, bytes.size)
        out.write(bytes)
    }

    fun readString(input: InputStream, maxLength: Int = 32767 * 4): String {
        val length = read(input)
        if (length < 0 || length > maxLength) {
            throw IOException("String length out of bounds: $length")
        }
        val bytes = readFully(input, length)
        return String(bytes, StandardCharsets.UTF_8)
    }

    /** Reads exactly [length] bytes, looping over partial reads (a single read() call is not guaranteed to fill the buffer). */
    fun readFully(input: InputStream, length: Int): ByteArray {
        val buffer = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val read = input.read(buffer, offset, length - offset)
            if (read == -1) throw EOFException("Stream ended after reading $offset of $length bytes")
            offset += read
        }
        return buffer
    }
}
