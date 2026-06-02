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
 * 混淆数据在 DEX 文件中的存储/显示格式。
 *
 * 所有的模式都围绕用户 [StringProcessor.encrypt] 返回的 `ByteArray` 进行格式化。
 * 不同的模式决定了加密后的字节数组以什么形式出现在反编译后的 DEX 代码中。
 *
 * ## 使用示例
 *
 * ```groovy
 * // build.gradle.kts
 * lsparanoid {
 *     mode = "base64"  // 或 "hex" 或 "bytes"
 * }
 * ```
 */
enum class ObfuscationMode {

    /**
     * Base64 编码模式。
     *
     * 加密后的 byte[] 以 Base64 字符串形式存储在 DEX 中。
     * 在反编译后的代码中看到的是 Base64 编码的字符串常量。
     *
     * **DEX 中的外观示例:** `"SGVsbG8gV29ybGQ="`
     *
     * **优点:** 字节码紧凑（仅一个 const-string 指令），适合大多数场景。
     */
    BASE64,

    /**
     * 十六进制编码模式。
     *
     * 加密后的 byte[] 以十六进制字符串形式存储在 DEX 中。
     * 在反编译后的代码中看到的是十六进制格式的字符串常量。
     *
     * **DEX 中的外观示例:** `"48656c6c6f20576f726c64"`
     *
     * **优点:** 字节码紧凑，格式可读性比 Base64 更低，增加逆向难度。
     */
    HEX,

    /**
     * 原始 byte[] 模式（默认）。
     *
     * 加密后的 byte[] 以原始字节数组字面量形式直接嵌入代码中。
     * 在反编译后的代码中看到的是 byte 数组初始化。
     *
     * **DEX 中的外观示例:** `new byte[]{0x48, 0x65, 0x6C, 0x6C, 0x6F}`
     *
     * **优点:** 无需额外的编码/解码步骤，运行时效率最高，不依赖任何编码库。
     * **缺点:** 字节码较长，不适合加密后数据很长的字符串。
     */
    BYTES,

    /**
     * 自定义格式化模式。
     *
     * 加密后的 byte[] 由用户定义的 [StringProcessor.formatData] 格式化为字符串，
     * 运行时由 [StringProcessor.parseData] 解析回 byte[]。
     *
     * **DEX 中的外观示例:** 由用户决定（如 "喵呜喵呜~"）
     *
     * **使用方式:** 在实现 StringProcessor 时重写 formatData/parseData 方法。
     * 只有此模式下 formatData/parseData 才会被调用。
     */
    CUSTOM;

    companion object {
        /**
         * 从字符串名称解析 ObfuscationMode。
         *
         * @param name 模式名称，不区分大小写（"base64", "hex", "bytes"）
         * @return 对应的 ObfuscationMode
         * @throws IllegalArgumentException 如果名称不匹配任何模式
         */
        @JvmStatic
        fun fromString(name: String): ObfuscationMode {
            return when (name.uppercase()) {
                "BASE64" -> BASE64
                "HEX" -> HEX
                "BYTES" -> BYTES
                "CUSTOM" -> CUSTOM
                else -> throw IllegalArgumentException(
                    "Unknown obfuscation mode: '$name'. Supported modes: bytes, base64, hex, custom"
                )
            }
        }
    }
}
