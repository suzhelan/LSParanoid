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

package com.androidacy.lsparanoid.plugin

import com.android.build.api.variant.Variant

/**
 * LSParanoid Gradle 扩展配置。
 *
 * 在 `build.gradle.kts` 中通过 `lsparanoid { }` 块进行配置。
 *
 * ## 使用示例
 *
 * ```kotlin
 * lsparanoid {
 *     // 使用自定义加解密处理器
 *     processor = "com.example.MyStringProcessor"
 *
 *     // 加密密钥（写入 DEX 时会混淆处理，不以明文出现）
 *     // 如果不设置，则由处理器自行管理密钥
 *     key = "my-secret-key"
 *
 *     // 加密数据在 DEX 中的显示格式: "base64"(默认), "hex", "bytes"
 *     mode = "base64"
 *
 *     // 种子值（用于默认处理器和密钥混淆）
 *     seed = 12345
 *
 *     // 类过滤器：控制哪些类需要混淆
 *     // null = 仅处理 @Obfuscate 注解的类
 *     // { true } = 混淆所有类
 *     classFilter = { it.startsWith("com.example.") }
 *
 *     // 是否混淆依赖库中的类
 *     includeDependencies = false
 *
 *     // 变体过滤器：控制哪些构建变体需要混淆
 *     variantFilter = { variant -> variant.buildType == "release" }
 * }
 * ```
 */
open class LSParanoidExtension {
    /**
     * 种子值，用于默认加解密处理器的密钥生成。
     *
     * - 设置后确保可重现构建（相同种子 → 相同混淆结果）
     * - 未设置时使用随机种子（每次构建结果不同）
     * - 同时用于对 Gradle 中配置的 `key` 进行混淆存储
     */
    var seed: Int? = null

    /**
     * 类过滤器：控制哪些类需要进行字符串混淆。
     *
     * - `null`（默认）：仅处理带有 `@Obfuscate` 注解的类
     * - `{ true }`：混淆所有类的字符串
     * - `{ it.startsWith("com.example.") }`：仅混淆指定包名下的类
     */
    var classFilter: ((className: String) -> Boolean)? = null

    /**
     * 是否混淆依赖库中的类。默认为 false。
     */
    var includeDependencies: Boolean = false

    /**
     * 构建变体过滤器。默认对所有变体启用混淆。
     *
     * 返回 `true` 表示对该变体启用混淆，`false` 表示跳过。
     */
    var variantFilter: (Variant) -> Boolean = { true }

    // ==================== 自定义处理器配置 ====================

    /**
     * 自定义加解密处理器的全类名。
     *
     * 该类必须实现 [com.androidacy.lsparanoid.StringProcessor] 接口，
     * 且具有无参构造函数。
     *
     * - 未设置（null）：使用内置的 [com.androidacy.lsparanoid.DefaultStringProcessor]
     * - 设置后：处理器类必须在编译类路径和运行时类路径上都可用
     *
     * 推荐将处理器放在独立的库模块中，然后在 app 模块中添加依赖。
     */
    var processor: String? = null

    /**
     * 加密密钥。
     *
     * - 未设置（null）：不向处理器传递密钥（处理器自行管理密钥）
     * - 设置后：密钥会通过参数传给 [com.androidacy.lsparanoid.StringProcessor.encrypt] 和
     *   [com.androidacy.lsparanoid.StringProcessor.decrypt] 方法
     *
     * **安全说明：** 密钥在写入 DEX 时会进行混淆处理，不会以明文字符串形式出现。
     * 但此混淆不是密码学安全的，仅增加逆向工程难度。
     */
    var key: String? = null

    /**
     * 加密数据在 DEX 中的显示/存储格式。
     *
     * 支持的值：
     * - `"bytes"`（默认）：加密后的 byte[] 以原始字节数组字面量存储，无编码开销
     * - `"base64"`：加密后的 byte[] 以 Base64 字符串形式存储
     * - `"hex"`：加密后的 byte[] 以十六进制字符串形式存储
     * - `"custom"`：使用 StringProcessor.formatData/parseData 进行自定义格式化
     */
    var mode: String = "bytes"
}
