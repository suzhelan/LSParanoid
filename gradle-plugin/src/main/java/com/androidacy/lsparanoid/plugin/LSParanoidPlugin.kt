/*
 * Copyright 2020 Michael Rozumyanskiy
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

import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.ScopedArtifacts.Scope
import com.android.build.gradle.api.AndroidBasePlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.compile.JavaCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import java.security.SecureRandom

/**
 * LSParanoid Gradle 插件。
 *
 * 该插件在 Android 构建过程中自动集成字符串混淆功能。
 * 支持自定义加解密处理器、密钥配置、多种 DEX 显示格式。
 *
 * ## 接口分离架构
 *
 * 框架将加解密接口分离为编译时和运行时两部分：
 * - [StringEncryptor][com.androidacy.lsparanoid.StringEncryptor]：编译时加密接口
 * - [StringDecryptor][com.androidacy.lsparanoid.StringDecryptor]：运行时解密接口
 * - [StringProcessor][com.androidacy.lsparanoid.StringProcessor]：组合接口（向后兼容）
 *
 * 最终 APK 中 `encrypt`/`shouldFog`/`formatData` 等编译时方法可被 R8/ProGuard 安全移除，
 * 减少逆向工程的攻击面。
 *
 * ## 插件 ID
 *
 * `com.androidacy.lsparanoid`
 *
 * ## 基本使用
 *
 * ```kotlin
 * plugins {
 *     id("com.androidacy.lsparanoid")
 * }
 *
 * lsparanoid {
 *     // 基本配置
 *     variantFilter = { it.buildType == "release" }
 *
 *     // 自定义处理器配置
 *     processor = "com.example.MyStringProcessor"
 *     key = "my-secret-key"
 *     mode = "base64"
 * }
 * ```
 */
class LSParanoidPlugin : Plugin<Project> {
    @Suppress("UnstableApiUsage")
    override fun apply(project: Project) {
        val extension = project.extensions.create("lsparanoid", LSParanoidExtension::class.java)

        // 添加核心库依赖
        project.addDependencies()

        project.plugins.withType(AndroidBasePlugin::class.java) { _ ->
            val components = project.extensions.getByType(AndroidComponentsExtension::class.java)
            components.onVariants { variant ->
                if (!extension.variantFilter(variant)) return@onVariants

                val task = project.tasks.register(
                    "lsparanoid${variant.name.replaceFirstChar { it.uppercase() }}",
                    LSParanoidTask::class.java
                ) {
                    // 基本配置
                    it.bootClasspath.set(components.sdkComponents.bootClasspath)
                    it.classpath = variant.compileClasspath
                    it.seed.set(extension.seed ?: SecureRandom().nextInt())
                    it.classFilter = extension.classFilter
                    it.projectName.set("${project.rootProject.name}\$${project.path}")

                    // 自定义处理器配置
                    it.processorClassName.set(extension.processor)
                    it.key.set(extension.key)
                    it.mode.set(extension.mode)
                }

                variant.artifacts.forScope(if (extension.includeDependencies) Scope.ALL else Scope.PROJECT)
                    .use(task).toTransform(
                        ScopedArtifact.CLASSES,
                        LSParanoidTask::jars,
                        LSParanoidTask::dirs,
                        LSParanoidTask::output,
                    )

                // 连接编译任务
                task.configure { taskObj ->
                    val javaCompileTask = variant.sources.java?.let {
                        project.tasks.named("compile${variant.name.replaceFirstChar { c -> c.uppercase() }}JavaWithJavac")
                    }
                    if (javaCompileTask != null) {
                        taskObj.dependsOn(javaCompileTask)
                    }

                    val variantCapitalized = variant.name.replaceFirstChar { c -> c.uppercase() }
                    val kotlinTaskName = "compile${variantCapitalized}Kotlin"
                    project.tasks.matching { it is KotlinCompilationTask<*> && it.name == kotlinTaskName }
                        .forEach {
                            taskObj.dependsOn(it)
                            taskObj.mustRunAfter(it)
                        }
                }
            }
        }

        // 配置 Java 和 Kotlin 编译器选项（优化字符串拼接以便更好的混淆）
        project.tasks.withType(JavaCompile::class.java) {
            it.options.compilerArgs.add("-XDstringConcat=inline")
        }

        project.tasks.withType(KotlinCompilationTask::class.java) {
            it.compilerOptions {
                freeCompilerArgs.add("-Xstring-concat=inline")
            }
        }
    }

    /**
     * 添加核心库依赖。
     * 自动将 core 模块添加到项目的 implementation 配置中。
     */
    private fun Project.addDependencies() {
        val version = Build.VERSION
        val configurationName = JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME
        dependencies.add(configurationName, "com.androidacy.lsparanoid:core:$version")
    }
}
