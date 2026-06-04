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

import com.joom.grip.Grip
import com.joom.grip.GripFactory
import com.joom.grip.io.IoFactory
import com.joom.grip.mirrors.getObjectTypeByInternalName
import com.androidacy.lsparanoid.processor.commons.closeQuietly
import com.androidacy.lsparanoid.processor.commons.createFile
import com.androidacy.lsparanoid.processor.logging.getLogger
import com.androidacy.lsparanoid.processor.model.Deobfuscator
import com.androidacy.lsparanoid.DefaultStringProcessor
import com.androidacy.lsparanoid.StringProcessor
import com.androidacy.lsparanoid.ObfuscationMode
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.commons.Method
import java.nio.file.Path
import java.util.jar.JarOutputStream

/**
 * LSParanoid 主处理器。
 *
 * 协调整个字符串混淆流程：
 * 1. 分析输入类文件，找到需要混淆的字符串
 * 2. 使用配置的 [StringProcessor] 进行加密
 * 3. 替换原始字符串为解密方法调用
 * 4. 生成 Deobfuscator 运行时解密类
 *
 * @param seed 种子值
 * @param classpath 类路径（用于类分析和自定义处理器加载）
 * @param inputs 输入类文件路径
 * @param output 输出 JAR
 * @param asmApi ASM API 版本
 * @param projectName 项目名称（用于生成 Deobfuscator 类名）
 * @param classFilter 类过滤器
 * @param processorClassName 自定义 StringProcessor 全类名（null 使用默认）
 * @param key 加密密钥字符串（null 表示不传递密钥）
 * @param mode 混淆数据存储格式名称
 */
class ParanoidProcessor(
    private val seed: Int,
    classpath: Set<Path>,
    private val inputs: List<Path>,
    private val output: JarOutputStream,
    private val asmApi: Int = Opcodes.ASM9,
    private val projectName: String,
    private val classFilter: ((className: String) -> Boolean)?,
    private val processorClassName: String? = null,
    private val key: String? = null,
    private val mode: String = "base64"
) {

    private val logger = getLogger()
    private val sortedInputs = inputs.distinct().sorted()
    private val grip: Grip = GripFactory.newInstance(asmApi).create(classpath + sortedInputs)

    /**
     * 执行混淆处理流程。
     */
    fun process() {
        // 1. 解析配置
        val obfuscationMode = ObfuscationMode.fromString(mode)
        val stringProcessor = loadStringProcessor()
        val keyBytes = key?.toByteArray(Charsets.UTF_8)
        val obfuscatedKey = keyBytes?.let { DeobfuscatorGenerator.obfuscateKey(it, seed) }

        dumpConfiguration(stringProcessor)

        // 编译期警告：如果 StringProcessor 在 classFilter 范围内，会被自动排除
        if (processorClassName != null && classFilter?.invoke(processorClassName) == true) {
            logger.warn(
                "StringProcessor '{}' is within classFilter scope and will be auto-excluded " +
                "from obfuscation to prevent infinite recursion at runtime. " +
                "Consider placing your StringProcessor in a separate library module.",
                processorClassName
            )
        }

        // 2. 创建字符串注册表（传入 mode 决定格式化方式）
        StringRegistryImpl(stringProcessor, keyBytes, obfuscationMode).use { stringRegistry ->

            // 3. 分析输入类（传递 processorClassName 以排除 StringProcessor 类）
            val analysisResult = Analyzer(grip, classFilter, processorClassName).analyze(sortedInputs)
            analysisResult.dump()

            // 4. 创建 Deobfuscator 模型
            val deobfuscator = createDeobfuscator()
            logger.info("Prepare to generate {}", deobfuscator)

            // 5. 获取处理器类名（用于生成代码中的引用）
            val effectiveProcessorClassName = processorClassName
                ?: DefaultStringProcessor::class.java.name

            // 6. 处理和补丁类文件
            val sources = sortedInputs.asSequence().map { input ->
                IoFactory.createFileSource(input)
            }

            try {
                // 替换字符串为解密调用
                Patcher(
                    deobfuscator,
                    stringRegistry,
                    analysisResult,
                    grip.classRegistry,
                    grip.fileRegistry,
                    asmApi,
                    stringProcessor,
                    obfuscationMode
                ).copyAndPatchClasses(sources, output)

                // 生成 Deobfuscator 类
                val deobfuscatorClasses = DeobfuscatorGenerator(
                    deobfuscator,
                    stringRegistry,
                    grip.classRegistry,
                    grip.fileRegistry,
                    effectiveProcessorClassName.replace('.', '/'),
                    obfuscatedKey,
                    seed,
                    obfuscationMode
                ).generateDeobfuscatorClasses()

                // 写入生成的类
                deobfuscatorClasses.forEach { (className, classBytes) ->
                    output.createFile(className, classBytes)
                }
            } finally {
                sources.forEach { source ->
                    source.closeQuietly()
                }
            }
        }
    }

    /**
     * 加载 StringProcessor 实例。
     *
     * 如果配置了自定义处理器类名，通过 ClassLoader 加载。
     * 搜索顺序：当前线程 ClassLoader → URLClassLoader(classpath + inputs)。
     * 否则使用 [DefaultStringProcessor]。
     */
    private fun loadStringProcessor(): StringProcessor {
        if (processorClassName == null) {
            logger.info("Using default StringProcessor (DefaultStringProcessor)")
            return DefaultStringProcessor(seed.toLong())
        }

        logger.info("Loading custom StringProcessor: {}", processorClassName)

        return try {
            val parentLoader = Thread.currentThread().contextClassLoader
                ?: javaClass.classLoader

            // 1. 先尝试用当前线程 ClassLoader 直接加载（classpath 上已有）
            try {
                val loaded = parentLoader.loadClass(processorClassName)
                return loaded.getDeclaredConstructor().newInstance() as StringProcessor
            } catch (_: ClassNotFoundException) {
                logger.info("Not in default classpath, searching inputs...")
            }

            // 2. 用 URLClassLoader 搜索项目编译输出(inputs)，包含用户自定义处理器类
            val urls = inputs.map { it.toUri().toURL() }.toTypedArray()
            val urlClassLoader = java.net.URLClassLoader(urls, parentLoader)
            val loaded = urlClassLoader.loadClass(processorClassName)
            loaded.getDeclaredConstructor().newInstance() as StringProcessor
        } catch (e: ClassNotFoundException) {
            throw RuntimeException(
                "Custom StringProcessor class not found: $processorClassName. " +
                    "Make sure the class is on the compile classpath. " +
                    "Tip: put your processor in a library module and add it as a dependency.",
                e
            )
        } catch (e: Exception) {
            throw RuntimeException(
                "Failed to load custom StringProcessor: $processorClassName. " +
                    "Error: ${e.message}",
                e
            )
        }
    }

    /**
     * 打印配置信息到日志。
     */
    private fun dumpConfiguration(processor: StringProcessor) {
        logger.info("Starting ParanoidProcessor:")
        logger.info("  inputs         = {}", inputs)
        logger.info("  output         = {}", output)
        logger.info("  seed           = {}", seed)
        logger.info("  processor      = {}", processor::class.java.name)
        logger.info("  mode           = {}", mode)
        logger.info("  key configured = {}", key != null)
    }

    /**
     * 打印分析结果到日志。
     */
    private fun AnalysisResult.dump() {
        if (configurationsByType.isEmpty()) {
            logger.info("No classes to obfuscate")
        } else {
            logger.info("Classes to obfuscate:")
            configurationsByType.forEach { (type, configuration) ->
                logger.info("  {}:", type.internalName)
                configuration.constantStringsByFieldName.forEach { (field, string) ->
                    logger.info("    {} = \"{}\"", field, string)
                }
            }
        }
    }

    /**
     * 创建 Deobfuscator 模型。
     * Deobfuscator 类名格式: `com.androidacy.lsparanoid.Deobfuscator$ProjectName`
     */
    private fun createDeobfuscator(): Deobfuscator {
        val deobfuscatorInternalName =
            "com/androidacy/lsparanoid/Deobfuscator${composeDeobfuscatorNameSuffix()}"
        val deobfuscatorType = getObjectTypeByInternalName(deobfuscatorInternalName)
        val deobfuscationMethod =
            Method("getString", Type.getType(String::class.java), arrayOf(Type.INT_TYPE))
        return Deobfuscator(deobfuscatorType, deobfuscationMethod)
    }

    /**
     * 根据 projectName 生成 Deobfuscator 类名后缀。
     */
    private fun composeDeobfuscatorNameSuffix(): String {
        val normalizedProjectName =
            projectName.filter { it.isLetterOrDigit() || it == '_' || it == '$' }
        return if (normalizedProjectName.isEmpty() || normalizedProjectName.startsWith('$')) {
            normalizedProjectName
        } else {
            "$$normalizedProjectName"
        }
    }
}
