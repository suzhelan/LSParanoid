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
 * 运行时字符串解密接口。
 *
 * 此接口是 [StringProcessor] 的运行时子集，仅包含解密所需的方法。
 * 生成的 Deobfuscator 类通过此接口调用解密逻辑。
 *
 * ## 设计目的
 *
 * 将加密逻辑（编译时）与解密逻辑（运行时）分离：
 * - 最终 APK 中仅需要此接口及其实现类的 `decrypt`/`parseData` 方法
 * - `encrypt`/`shouldFog`/`formatData` 等编译时方法可被 R8/ProGuard 安全移除
 * - 减少攻击面，逆向者无法从 APK 中获取加密算法
 *
 * ## ProGuard / R8 规则
 *
 * 框架自带的 consumer-rules.pro 会自动保留运行时必需的方法，
 * 同时允许移除编译时方法（encrypt 等）。
 *
 * @see StringEncryptor
 * @see StringProcessor
 */
interface StringDecryptor {

    /**
     * 解密字节数组为原始字符串。在运行时由生成的 Deobfuscator 类调用。
     *
     * @param data 加密后的字节数组，即 [StringEncryptor.encrypt] 的返回值
     * @param key  解密密钥，与加密时传入的 key 相同。
     *             如果用户未在 Gradle 中配置 key，则为 null。
     * @return 原始明文字符串
     */
    fun decrypt(data: ByteArray, key: ByteArray?): String

    /**
     * 将 DEX 中的格式化字符串解析回加密后的 byte[]。
     *
     * 在运行时由生成的 Deobfuscator 类调用。
     * 必须与 [StringEncryptor.formatData] 互为逆操作：`parseData(formatData(x)) == x`。
     * 仅在 [ObfuscationMode.CUSTOM] 模式下被调用。
     *
     * ## 默认行为
     *
     * 默认使用 Base64 解码。
     *
     * @param formatted DEX 中的格式化字符串
     * @return 加密后的字节数组，将传给 [decrypt] 进行解密
     */
    fun parseData(formatted: String): ByteArray {
        return Base64Decoder.decode(formatted)
    }
}
