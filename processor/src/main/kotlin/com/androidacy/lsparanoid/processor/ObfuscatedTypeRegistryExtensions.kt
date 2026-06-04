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

import com.joom.grip.ClassRegistry
import com.joom.grip.Grip
import com.joom.grip.mirrors.Type
import com.joom.grip.mirrors.Typed
import com.joom.grip.objectType

fun newObfuscatedTypeRegistry(classRegistry: ClassRegistry): ObfuscatedTypeRegistry {
    return ObfuscatedTypeRegistryImpl(classRegistry)
}

fun ObfuscatedTypeRegistry.withCache(): ObfuscatedTypeRegistry {
    return this as? CachedObfuscatedTypeRegistry ?: CachedObfuscatedTypeRegistry(this)
}

/**
 * 判断一个类是否需要进行字符串混淆。
 *
 * 排除规则（防止运行时无限递归）：
 * 1. Deobfuscator 类 — 由框架生成，不能混淆自身
 * 2. StringProcessor 实现类 — 运行时被 Deobfuscator 调用进行解密，
 *    如果其内部字符串也被混淆，解密时会再次调用 Deobfuscator，导致循环调用
 *
 * @param classFilter 用户配置的类过滤器
 * @param processorClassName 自定义 StringProcessor 的全类名（null 表示使用默认处理器）
 */
fun ObfuscatedTypeRegistry.shouldObfuscate(
    classFilter: ((className: String) -> Boolean)?,
    processorClassName: String? = null
): (Grip, Typed<Type.Object>) -> Boolean {
    return objectType { grip, type ->
        grip.fileRegistry.findPathForType(type) != null &&
        // 排除 Deobfuscator 类（框架生成的解密器）
        !type.className.startsWith("com.androidacy.lsparanoid.Deobfuscator") &&
        // 排除 StringProcessor 实现类，防止运行时无限递归。
        // 原因：Deobfuscator.decryptFormatted() 会调用 PROCESSOR.parseData()，
        // 如果 parseData() 内部的字符串字面量被混淆，就会触发 Deobfuscator 解密，
        // 而 Deobfuscator 又调用 parseData()，形成无限递归 → StackOverflowError。
        processorClassName != type.className &&
        ((classFilter?.invoke(type.className) ?: false) || shouldObfuscate(type))
    }
}
