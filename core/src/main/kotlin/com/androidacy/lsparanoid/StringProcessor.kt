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
 * 字符串加解密处理器接口。
 *
 * 用户实现此接口以提供自定义的字符串加解密逻辑。
 * 所有加解密操作统一通过此接口进行，替代原有的 PRNG 加密系统。
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
 */
interface StringProcessor {

    /**
     * 加密字符串。在编译时由注解处理器调用。
     *
     * @param data 明文字符串，不为 null
     * @param key  加密密钥，来自 Gradle 配置。
     *             如果用户未在 Gradle 中配置 key，则为 null，
     *             此时由实现自行管理密钥。
     * @return 加密后的字节数组，不为 null。
     *         该字节数组必须能被同一个实现的 [decrypt] 方法正确还原。
     */
    fun encrypt(data: String, key: ByteArray?): ByteArray

    /**
     * 解密字节数组为原始字符串。在运行时由生成的 Deobfuscator 类调用。
     *
     * @param data 加密后的字节数组，即 [encrypt] 的返回值
     * @param key  解密密钥，与 [encrypt] 时传入的 key 相同。
     *             如果用户未在 Gradle 中配置 key，则为 null。
     * @return 原始明文字符串
     */
    fun decrypt(data: ByteArray, key: ByteArray?): String

    /**
     * 判断字符串是否需要混淆。在编译时分析阶段调用。
     *
     * 建议在此方法中过滤掉不重要的字符串（如空字符串、过长的字符串），
     * 以减少 APK 体积和运行时开销。
     *
     * @param data 待判断的明文字符串
     * @return true 表示需要混淆该字符串，false 表示跳过
     */
    fun shouldFog(data: String): Boolean

    // ==================== 自定义数据格式化 ====================

    /**
     * 将加密后的 byte[] 格式化为 DEX 中显示的字符串。
     *
     * 在编译时调用，返回值会作为字符串常量嵌入到生成的 DEX 代码中。
     * 用户可重写此方法实现任意自定义格式。
     *
     * ## 默认行为
     *
     * 默认使用 Base64 编码。内置实现还支持 Hex 编码，
     * 可通过 [DefaultStringProcessor] 的构造参数选择。
     *
     * ## 自定义示例
     *
     * ```java
     * // 示例：将 byte[] 转为二进制，然后 0→喵, 1→呜, 字节间用~分隔
     * &#64;Override
     * public String formatData(byte[] data) {
     *     StringBuilder sb = new StringBuilder();
     *     for (int i = 0; i < data.length; i++) {
     *         if (i > 0) sb.append('~');
     *         String binary = String.format("%8s",
     *             Integer.toBinaryString(data[i] & 0xFF)).replace(' ', '0');
     *         sb.append(binary.replace('0', '喵').replace('1', '呜'));
     *     }
     *     return sb.toString();
     * }
     * ```
     *
     * @param data 加密后的字节数组（即 [encrypt] 的返回值）
     * @return 格式化后的字符串，将出现在 DEX 代码中
     */
    fun formatData(data: ByteArray): String {
        return java.util.Base64.getEncoder().encodeToString(data)
    }

    /**
     * 将 DEX 中的格式化字符串解析回加密后的 byte[]。
     *
     * 在运行时由生成的 Deobfuscator 类调用。
     * 必须与 [formatData] 互为逆操作：`parseData(formatData(x)) == x`。
     *
     * ## 默认行为
     *
     * 默认使用 Base64 解码。
     *
     * ## 自定义示例
     *
     * ```java
     * // 示例：与上面 formatData 对应的解析
     * &#64;Override
     * public byte[] parseData(String formatted) {
     *     String[] parts = formatted.split("~");
     *     byte[] data = new byte[parts.length];
     *     for (int i = 0; i < parts.length; i++) {
     *         String binary = parts[i].replace('喵', '0').replace('呜', '1');
     *         data[i] = (byte) Integer.parseInt(binary, 2);
     *     }
     *     return data;
     * }
     * ```
     *
     * @param formatted DEX 中的格式化字符串
     * @return 加密后的字节数组，将传给 [decrypt] 进行解密
     */
    fun parseData(formatted: String): ByteArray {
        return Base64Decoder.decode(formatted)
    }
}
