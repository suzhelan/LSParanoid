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
 * 编译时字符串加密接口。
 *
 * 此接口仅在编译时由注解处理器调用，**不需要出现在最终 APK 的运行时**。
 * R8/ProGuard 可以安全地移除实现类中的这些方法。
 *
 * ## 设计目的
 *
 * 将加密逻辑（编译时）与解密逻辑（运行时）分离，使得最终 APK 中
 * 不包含 `encrypt` 方法，增加逆向工程难度：
 * - 逆向者无法通过找到 `encrypt` 方法来轻松构造解密逻辑
 * - 减少最终 APK 的攻击面
 *
 * ## 与 [StringDecryptor] 的关系
 *
 * - [StringEncryptor]：编译时，加密 + 过滤 + 格式化
 * - [StringDecryptor]：运行时，解密 + 解析
 * - [StringProcessor]：同时实现两者（向后兼容）
 *
 * @see StringDecryptor
 * @see StringProcessor
 */
interface StringEncryptor {

    /**
     * 加密字符串。在编译时由注解处理器调用。
     *
     * @param data 明文字符串，不为 null
     * @param key  加密密钥，来自 Gradle 配置。
     *             如果用户未在 Gradle 中配置 key，则为 null，
     *             此时由实现自行管理密钥。
     * @return 加密后的字节数组，不为 null。
     *         该字节数组必须能被同一个实现的 [StringDecryptor.decrypt] 方法正确还原。
     */
    fun encrypt(data: String, key: ByteArray?): ByteArray

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

    /**
     * 将加密后的 byte[] 格式化为 DEX 中显示的字符串。
     *
     * 在编译时调用，返回值会作为字符串常量嵌入到生成的 DEX 代码中。
     * 仅在 [ObfuscationMode.CUSTOM] 模式下被调用。
     *
     * ## 默认行为
     *
     * 默认使用 Base64 编码。
     *
     * @param data 加密后的字节数组（即 [encrypt] 的返回值）
     * @return 格式化后的字符串，将出现在 DEX 代码中
     */
    fun formatData(data: ByteArray): String {
        return java.util.Base64.getEncoder().encodeToString(data)
    }
}
