/*
 * Copyright 2021 Michael Rozumyanskiy
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

package com.androidacy.lsparanoid.processor

import com.androidacy.lsparanoid.HexHelper
import com.androidacy.lsparanoid.ObfuscationMode
import com.androidacy.lsparanoid.StringEncryptor
import java.io.Closeable

/**
 * 字符串注册条目。
 */
sealed class StringEntry {
    abstract val encryptedData: ByteArray

    /** 在 DEX 字节码中使用的数据：BYTES 模式为 ByteArray, 其余模式为 String */
    abstract val codeData: Any

    /** 用于判断是否内联的大小 */
    abstract val codeSize: Int

    data class Inline(
        override val encryptedData: ByteArray,
        override val codeData: Any,
        override val codeSize: Int
    ) : StringEntry() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Inline) return false
            return encryptedData.contentEquals(other.encryptedData)
        }
        override fun hashCode(): Int = encryptedData.contentHashCode()
    }

    data class Centralized(
        override val encryptedData: ByteArray,
        override val codeData: Any,
        override val codeSize: Int,
        val index: Int
    ) : StringEntry() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Centralized) return false
            return index == other.index && encryptedData.contentEquals(other.encryptedData)
        }
        override fun hashCode(): Int = 31 * index + encryptedData.contentHashCode()
    }
}

interface StringRegistry : Closeable {
    fun registerString(string: String): StringEntry
    fun getCentralizedCount(): Int
    fun getCentralizedEntries(): List<StringEntry.Centralized>
}

/**
 * 默认实现。根据 mode 决定如何编码加密数据 + 选择存储策略。
 */
class StringRegistryImpl @JvmOverloads constructor(
    private val stringProcessor: StringEncryptor,
    private val key: ByteArray?,
    private val mode: ObfuscationMode = ObfuscationMode.BYTES,
    private val inlineThreshold: Int = DEFAULT_INLINE_THRESHOLD
) : StringRegistry {

    private val stringToEntryMap = mutableMapOf<String, StringEntry>()
    private val centralizedEntries = mutableListOf<StringEntry.Centralized>()
    private var nextCentralizedIndex = 0

    override fun registerString(string: String): StringEntry {
        stringToEntryMap[string]?.let { return it }

        val encrypted = stringProcessor.encrypt(string, key)

        // 根据 mode 决定 codeData 和 codeSize
        val (codeData, codeSize) = when (mode) {
            ObfuscationMode.BYTES -> encrypted to encrypted.size
            ObfuscationMode.BASE64 -> {
                val b64 = java.util.Base64.getEncoder().encodeToString(encrypted)
                b64 to b64.length
            }
            ObfuscationMode.HEX -> {
                val hex = HexHelper.encode(encrypted)
                hex to hex.length
            }
            ObfuscationMode.CUSTOM -> {
                val custom = stringProcessor.formatData(encrypted)
                custom to custom.length
            }
        }

        val entry = if (codeSize <= inlineThreshold) {
            StringEntry.Inline(encrypted, codeData, codeSize)
        } else {
            StringEntry.Centralized(encrypted, codeData, codeSize, nextCentralizedIndex++)
                .also { centralizedEntries.add(it) }
        }

        stringToEntryMap[string] = entry
        return entry
    }

    override fun getCentralizedCount() = centralizedEntries.size
    override fun getCentralizedEntries() = centralizedEntries.toList()

    override fun close() {}

    companion object {
        const val DEFAULT_INLINE_THRESHOLD = 64
    }
}
