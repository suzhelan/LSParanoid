/*
 * Copyright 2023 LSPosed
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.androidacy.lsparanoid

/**
 * 十六进制编解码工具类。
 *
 * 用于 [ObfuscationMode.HEX] 模式下，将加密后的 byte[] 编码为十六进制字符串
 * （编译时）以及从十六进制字符串解码回 byte[]（运行时）。
 *
 * 不依赖外部库，适合在 Android 运行时使用。
 */
object HexHelper {

    private val HEX_CHARS = "0123456789abcdef".toCharArray()

    /**
     * 将字节数组编码为小写十六进制字符串。
     *
     * 每个字节编码为两个十六进制字符。
     *
     * @param data 字节数组
     * @return 十六进制编码的字符串
     */
    @JvmStatic
    fun encode(data: ByteArray): String {
        val sb = StringBuilder(data.size * 2)
        for (b in data) {
            val v = b.toInt() and 0xFF
            sb.append(HEX_CHARS[v ushr 4])
            sb.append(HEX_CHARS[v and 0x0F])
        }
        return sb.toString()
    }

    /**
     * 将十六进制字符串解码为字节数组。
     *
     * 输入字符串长度必须为偶数，且只包含合法的十六进制字符（0-9, a-f, A-F）。
     *
     * @param hex 十六进制编码的字符串
     * @return 解码后的字节数组
     * @throws IllegalArgumentException 如果字符串长度为奇数或包含非法字符
     */
    @JvmStatic
    fun decode(hex: String): ByteArray {
        val len = hex.length
        require(len % 2 == 0) { "Hex string must have even length, got: $len" }

        val data = ByteArray(len / 2)
        for (i in data.indices) {
            val high = hexValue(hex[i * 2])
            val low = hexValue(hex[i * 2 + 1])
            data[i] = ((high shl 4) or low).toByte()
        }
        return data
    }

        /**
     * 将单个十六进制字符转换为对应的数值。
     */
        private fun hexValue(c: Char): Int {
            return when (c) {
                in '0'..'9' -> c - '0'
                in 'a'..'f' -> c - 'a' + 10
                in 'A'..'F' -> c - 'A' + 10
                else -> throw IllegalArgumentException("Invalid hex character: '$c'")
            }
        }
}
