package dev.ujhhgtg.via.downloads

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Only the installation metadata in a binary AndroidManifest.xml; no resource loading or code decompilation. */
internal data class ApkManifest(
    val packageName: String,
    val versionCode: Long,
    val splitName: String?,
    val configForSplit: String?,
    val dependencies: Set<String>,
    val minSdk: Int,
    val splitRequired: Boolean,
) {
    companion object {
        fun read(bytes: ByteArray): ApkManifest {
            try { return parse(bytes) }
            catch (error: Exception) { throw IOException("Invalid AndroidManifest.xml", error) }
        }

        private fun parse(bytes: ByteArray): ApkManifest {
            val data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            fun u16(at: Int) = data.getShort(at).toInt() and 0xffff
            fun int(at: Int) = data.getInt(at)
            require(bytes.size >= 8 && u16(0) == 3)
            val end = int(4)
            require(end in 8..bytes.size)
            var strings = emptyList<String>()
            var root: Map<String, String>? = null
            var minSdk = 1
            var splitRequired = false
            val dependencies = linkedSetOf<String>()
            var offset = u16(2)
            while (offset < end) {
                require(offset + 8 <= end)
                val header = u16(offset + 2)
                val size = int(offset + 4)
                require(header in 8..size && size <= end - offset)
                when (u16(offset)) {
                    1 -> {
                        require(header >= 28)
                        val count = int(offset + 8)
                        val utf8 = int(offset + 16) and 0x100 != 0
                        val start = int(offset + 20)
                        require(count >= 0 && count <= (size - header) / 4 && start in header..size)
                        strings = List(count) { index ->
                            var at = offset + start + int(offset + header + index * 4)
                            fun length8(): Int {
                                val first = bytes[at++].toInt() and 0xff
                                return if (first and 0x80 == 0) first else ((first and 0x7f) shl 8) or (bytes[at++].toInt() and 0xff)
                            }
                            fun length16(): Int {
                                val first = u16(at); at += 2
                                return if (first and 0x8000 == 0) first else {
                                    val value = ((first and 0x7fff) shl 16) or u16(at); at += 2; value
                                }
                            }
                            val length = if (utf8) { length8(); length8() } else Math.multiplyExact(length16(), 2)
                            require(at >= offset + start && length >= 0 && at.toLong() + length <= offset.toLong() + size)
                            String(bytes, at, length, if (utf8) Charsets.UTF_8 else Charsets.UTF_16LE)
                        }
                    }
                    0x102 -> {
                        val tag = offset + header
                        require(header >= 16 && tag + 20 <= offset + size)
                        val name = strings[int(tag + 4)]
                        val attributeStart = u16(tag + 8)
                        val attributeSize = u16(tag + 10)
                        val count = u16(tag + 12)
                        require(attributeStart >= 20 && attributeSize >= 20)
                        require(tag.toLong() + attributeStart + count.toLong() * attributeSize <= offset.toLong() + size)
                        val attributes = buildMap {
                            repeat(count) { index ->
                                val at = tag + attributeStart + index * attributeSize
                                val key = strings[int(at + 4)]
                                val raw = int(at + 8)
                                val type = bytes[at + 15].toInt() and 0xff
                                val value = int(at + 16)
                                val text = when {
                                    raw >= 0 -> strings[raw]
                                    type == 3 -> strings[value]
                                    type == 0x12 -> (value != 0).toString()
                                    type == 0x10 || type == 0x11 -> value.toUInt().toString()
                                    else -> ""
                                }
                                put(key, text)
                            }
                        }
                        when (name) {
                            "manifest" -> { require(root == null); root = attributes }
                            "uses-sdk" -> minSdk = attributes["minSdkVersion"]?.toIntOrNull() ?: 1
                            "uses-split" -> attributes["name"]?.takeIf(String::isNotEmpty)?.let(dependencies::add)
                            "application" -> splitRequired = attributes["isSplitRequired"] == "true"
                        }
                    }
                }
                offset += size
            }
            val manifest = requireNotNull(root)
            val packageName = requireNotNull(manifest["package"])
            require(packageName.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")))
            val low = manifest["versionCode"]?.toLongOrNull() ?: 0
            val high = manifest["versionCodeMajor"]?.toLongOrNull() ?: 0
            return ApkManifest(packageName, (high shl 32) or (low and 0xffffffffL),
                manifest["split"]?.takeIf(String::isNotEmpty), manifest["configForSplit"]?.takeIf { it.isNotEmpty() && it != "base" },
                dependencies, minSdk, splitRequired)
        }
    }
}
