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

package com.androidacy.lsparanoid.plugin

import com.androidacy.lsparanoid.processor.ParanoidProcessor
import org.gradle.api.DefaultTask
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.util.jar.JarOutputStream

/**
 * LSParanoid 字符串混淆 Gradle Task。
 *
 * 该 Task 在 Java/Kotlin 编译完成后执行，扫描目标类中的字符串常量，
 * 使用配置的 [com.androidacy.lsparanoid.StringEncryptor] 进行编译时加密，
 * 并生成运行时解密所需的 Deobfuscator 类（通过 [com.androidacy.lsparanoid.StringDecryptor] 接口）。
 *
 * 支持通过 Gradle 配置自定义加解密处理器、密钥、DEX 显示格式等。
 */
@CacheableTask
abstract class LSParanoidTask : DefaultTask() {

    /** 输入 JAR 文件列表（编译后的类文件） */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val jars: ListProperty<RegularFile>

    /** 输入目录列表（编译后的类文件目录） */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val dirs: ListProperty<Directory>

    /** 输出 JAR 文件（混淆后的类文件 + 生成的 Deobfuscator） */
    @get:OutputFile
    abstract val output: RegularFileProperty

    /** Android SDK boot classpath */
    @get:Classpath
    abstract val bootClasspath: ListProperty<RegularFile>

    /** 项目编译 classpath（用于加载自定义处理器） */
    @get:CompileClasspath
    abstract var classpath: FileCollection

    /** 种子值（用于默认处理器和密钥混淆） */
    @get:Input
    abstract val seed: Property<Int>

    /** 类过滤器 */
    @get:Input
    @get:Optional
    abstract var classFilter: ((className: String) -> Boolean)?

    /** 项目名称（用于生成 Deobfuscator 类名） */
    @get:Input
    abstract val projectName: Property<String>

    // ==================== 自定义处理器配置 ====================

    /**
     * 自定义加解密处理器的全类名。
     * null 表示使用默认处理器。
     */
    @get:Input
    @get:Optional
    abstract val processorClassName: Property<String>

    /**
     * 加密密钥（明文，写入 DEX 时会混淆）。
     * null 表示不向处理器传递密钥。
     */
    @get:Input
    @get:Optional
    abstract val key: Property<String>

    /**
     * 加密数据在 DEX 中的显示格式。
     * 支持的值: "base64", "hex", "bytes"
     */
    @get:Input
    abstract val mode: Property<String>

    @TaskAction
    fun taskAction() {
        val inputs = jars.get() + dirs.get()
        FileOutputStream(output.get().asFile).use { fileOut ->
            BufferedOutputStream(fileOut).use { bufferedOut ->
                JarOutputStream(bufferedOut).use { jarOutput ->
                    ParanoidProcessor(
                        seed = seed.get(),
                        inputs = inputs.map { it.asFile.toPath() },
                        classpath = bootClasspath.get().map { it.asFile.toPath() }
                            .toSet() + classpath.files.map { it.toPath() },
                        output = jarOutput,
                        projectName = projectName.get(),
                        classFilter = classFilter,
                        processorClassName = processorClassName.orNull,
                        key = key.orNull,
                        mode = mode.get()
                    ).process()
                }
            }
        }
    }
}
