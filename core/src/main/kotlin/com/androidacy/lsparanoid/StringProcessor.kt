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
 * 字符串加解密处理器组合接口。
 *
 * 同时继承编译时接口 [StringEncryptor] 和运行时接口 [StringDecryptor]，
 * 提供完整的加解密能力。用户通常实现此接口来定义自定义的加解密逻辑。
 *
 * ## 接口分离架构
 *
 * ```
 * StringEncryptor (编译时)     StringDecryptor (运行时)
 *   ├─ encrypt()                 ├─ decrypt()
 *   ├─ shouldFog()               └─ parseData()
 *   └─ formatData()
 *           ╲                       ╱
 *            StringProcessor (组合)
 *              用户实现此类
 * ```
 *
 * - **编译时**：Gradle Task 中调用 [StringEncryptor] 的方法加密字符串
 * - **运行时**：Deobfuscator 类通过 [StringDecryptor] 接口调用解密方法
 * - **最终 APK**：`encrypt`/`shouldFog`/`formatData` 可被 R8/ProGuard 安全移除
 *
 * ## 使用方式
 *
 * 1. 在项目中创建一个类实现 `StringProcessor` 接口
 * 2. 在 Gradle 中配置 `lsparanoid { processor = "com.example.MyProcessor" }`
 * 3. 确保该类在编译时和运行时都可用（如放在独立库模块中）
 *
 * ## 实现要求
 *
 * - 必须具有无参构造函数（或为 Kotlin object 单例）
 * - 必须线程安全（解密方法可能被多线程同时调用）
 * - 加密后的 `ByteArray` 必须能被同实现的 `decrypt` 方法正确还原
 * - 类必须在编译类路径和运行时类路径上都可用
 *
 * ## 重要：防止无限递归
 *
 * 如果你的 StringProcessor 类位于 `classFilter` 的范围内，框架会**自动排除**它，
 * 不对其字符串进行混淆。这是为了避免运行时的无限递归：
 *
 * ```
 * Deobfuscator.getString() → decryptFormatted() → PROCESSOR.parseData()
 *   → parseData() 内部的字符串被混淆，需要 Deobfuscator 解密
 *     → Deobfuscator.decryptFormatted() → PROCESSOR.parseData() → 无限递归 → StackOverflow!
 * ```
 *
 * **建议：** 将 StringProcessor 放在独立的库模块（library module）中，
 * 不在 `classFilter` 范围内，这样就不会触发此限制。
 * 处理器内部的字符串（分隔符、默认密钥等）是格式标记而非敏感数据，
 * 不混淆不会影响应用整体的安全性。
 *
 * ## 密钥管理
 *
 * - 如果在 Gradle 中配置了 `key`，它会作为参数传入 `encrypt`/`decrypt`
 * - 如果未配置 `key`，参数为 `null`，由实现自行管理密钥
 * - Gradle 中的 `key` 会在写入 DEX 时进行混淆处理，不会以明文出现
 *
 * ## 示例
 *
 * ```kotlin
 * class MyProcessor : StringProcessor {
 *     override fun encrypt(data: String, key: ByteArray?): ByteArray {
 *         val bytes = data.toByteArray(Charsets.UTF_8)
 *         val k = key ?: "default-key".toByteArray()
 *         return ByteArray(bytes.size) { i ->
 *             (bytes[i].toInt() xor k[i % k.size].toInt()).toByte()
 *         }
 *     }
 *
 *     override fun decrypt(data: ByteArray, key: ByteArray?): String {
 *         val k = key ?: "default-key".toByteArray()
 *         val bytes = ByteArray(data.size) { i ->
 *             (data[i].toInt() xor k[i % k.size].toInt()).toByte()
 *         }
 *         return String(bytes, Charsets.UTF_8)
 *     }
 *
 *     override fun shouldFog(data: String): Boolean {
 *         return data.isNotEmpty() && data.length < 1000
 *     }
 * }
 * ```
 *
 * @see StringEncryptor
 * @see StringDecryptor
 */
interface StringProcessor : StringEncryptor, StringDecryptor
