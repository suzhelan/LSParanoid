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

import com.androidacy.lsparanoid.ObfuscationMode
import com.joom.grip.ClassRegistry
import com.joom.grip.FileRegistry
import com.joom.grip.mirrors.toAsmType
import com.androidacy.lsparanoid.processor.model.Deobfuscator
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type
import org.objectweb.asm.commons.GeneratorAdapter
import org.objectweb.asm.commons.Method

/**
 * Deobfuscator 类生成器。
 *
 * 根据 [ObfuscationMode] 生成不同的运行时结构：
 * - BYTES: byte[][] 数据 + decrypt(byte[]) 方法
 * - BASE64: String[] 数据 + decryptFromBase64(String) 方法
 * - HEX: String[] 数据 + decryptFromHex(String) 方法
 * - CUSTOM: String[] 数据 + decryptFormatted(String) → PROCESSOR.parseData() 方法
 *
 * 生成的 Deobfuscator 类中 PROCESSOR 字段类型为 [StringDecryptor][com.androidacy.lsparanoid.StringDecryptor]，
 * 仅引用运行时解密接口，不引用编译时加密接口 [StringEncryptor][com.androidacy.lsparanoid.StringEncryptor]。
 * 这使得 R8/ProGuard 可以安全地从最终 APK 中移除 `encrypt`、`shouldFog`、`formatData` 等编译时方法。
 */
class DeobfuscatorGenerator(
    private val deobfuscator: Deobfuscator,
    private val stringRegistry: StringRegistry,
    private val classRegistry: ClassRegistry,
    private val fileRegistry: FileRegistry,
    private val processorClassName: String,
    private val obfuscatedKey: ByteArray?,
    private val keySeed: Int,
    private val mode: ObfuscationMode
) {

    fun generateDeobfuscatorClasses(): Map<String, ByteArray> {
        return mapOf("${deobfuscator.type.internalName}.class" to generateMainClass())
    }

    private fun generateMainClass(): ByteArray {
        val writer = StandaloneClassWriter(
            ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES, classRegistry, fileRegistry
        )
        writer.visit(V1_8, ACC_PUBLIC or ACC_SUPER, deobfuscator.type.internalName,
            null, OBJECT_TYPE.internalName, null)

        writer.generateFields()
        writer.generateStaticInitializer()
        writer.generateDefaultConstructor()
        if (obfuscatedKey != null) writer.generateGetKeyMethod()
        writer.generateDecryptMethod()
        writer.generateGetStringByIndexMethod()

        writer.visitEnd()
        return writer.toByteArray()
    }

    // ==================== 字段 ====================

    private fun ClassVisitor.generateFields() {
        // PROCESSOR
        visitField(ACC_PRIVATE or ACC_STATIC or ACC_FINAL,
            "PROCESSOR", STRING_DECRYPTOR_TYPE.descriptor, null, null).visitEnd()

        // 密钥字段
        if (obfuscatedKey != null) {
            visitField(ACC_PRIVATE or ACC_STATIC or ACC_FINAL,
                "_KEY_DATA", BYTE_ARRAY_TYPE.descriptor, null, null).visitEnd()
            visitField(ACC_PRIVATE or ACC_STATIC,
                "_cachedKey", BYTE_ARRAY_TYPE.descriptor, null, null).visitEnd()
        }

        // 集中存储数据
        val entries = stringRegistry.getCentralizedEntries()
        if (entries.isNotEmpty()) {
            when (mode) {
                ObfuscationMode.BYTES -> {
                    visitField(ACC_PRIVATE or ACC_STATIC or ACC_FINAL,
                        "_DATA", BYTE_ARRAY_ARRAY_TYPE.descriptor, null, null).visitEnd()
                }
                else -> {
                    visitField(ACC_PRIVATE or ACC_STATIC or ACC_FINAL,
                        "_DATA", STRING_ARRAY_TYPE.descriptor, null, null).visitEnd()
                }
            }
        }
    }

    // ==================== <clinit> ====================

    private fun ClassVisitor.generateStaticInitializer() {
        newMethod(ACC_STATIC, METHOD_CLINIT) {
            // PROCESSOR = new Processor()
            val procType = Type.getObjectType(processorClassName)
            newInstance(procType); dup(); invokeConstructor(procType, METHOD_INIT)
            putStatic(deobfuscator.type.toAsmType(), "PROCESSOR", STRING_DECRYPTOR_TYPE)

            // _KEY_DATA
            if (obfuscatedKey != null) {
                push(obfuscatedKey.size); newArray(Type.BYTE_TYPE)
                for (i in obfuscatedKey.indices) {
                    dup(); push(i); push(obfuscatedKey[i].toInt()); arrayStore(Type.BYTE_TYPE)
                }
                putStatic(deobfuscator.type.toAsmType(), "_KEY_DATA", BYTE_ARRAY_TYPE)
            }

            // _DATA
            val entries = stringRegistry.getCentralizedEntries()
            if (entries.isNotEmpty()) {
                when (mode) {
                    ObfuscationMode.BYTES -> {
                        push(entries.size); newArray(BYTE_ARRAY_TYPE)
                        for ((i, e) in entries.withIndex()) {
                            val bytes = e.codeData as ByteArray
                            dup(); push(i)
                            push(bytes.size); newArray(Type.BYTE_TYPE)
                            for (j in bytes.indices) {
                                dup(); push(j); push(bytes[j].toInt()); arrayStore(Type.BYTE_TYPE)
                            }
                            arrayStore(BYTE_ARRAY_TYPE)
                        }
                        putStatic(deobfuscator.type.toAsmType(), "_DATA", BYTE_ARRAY_ARRAY_TYPE)
                    }
                    else -> {
                        push(entries.size); newArray(STRING_TYPE)
                        for ((i, e) in entries.withIndex()) {
                            dup(); push(i); push(e.codeData as String); arrayStore(STRING_TYPE)
                        }
                        putStatic(deobfuscator.type.toAsmType(), "_DATA", STRING_ARRAY_TYPE)
                    }
                }
            }
        }
    }

    // ==================== 方法 ====================

    private fun ClassVisitor.generateDefaultConstructor() {
        newMethod(ACC_PUBLIC, METHOD_INIT) {
            loadThis(); invokeConstructor(OBJECT_TYPE, METHOD_INIT)
        }
    }

    private fun ClassVisitor.generateGetKeyMethod() {
        newMethod(ACC_PRIVATE or ACC_STATIC, Method("_getKey", "()[B")) {
            getStatic(deobfuscator.type.toAsmType(), "_cachedKey", BYTE_ARRAY_TYPE)
            val ready = newLabel(); ifNonNull(ready)

            getStatic(deobfuscator.type.toAsmType(), "_KEY_DATA", BYTE_ARRAY_TYPE)
            arrayLength(); newArray(Type.BYTE_TYPE)
            val newKey = newLocal(BYTE_ARRAY_TYPE); storeLocal(newKey)

            val i = newLocal(Type.INT_TYPE); push(0); storeLocal(i)
            val loop = mark()
            loadLocal(i)
            getStatic(deobfuscator.type.toAsmType(), "_KEY_DATA", BYTE_ARRAY_TYPE); arrayLength()
            val end = newLabel(); ifICmp(GeneratorAdapter.GE, end)

            loadLocal(newKey); loadLocal(i)
            getStatic(deobfuscator.type.toAsmType(), "_KEY_DATA", BYTE_ARRAY_TYPE)
            loadLocal(i); arrayLoad(Type.BYTE_TYPE)
            push(keySeed); loadLocal(i); push(4)
            math(GeneratorAdapter.REM, Type.INT_TYPE); push(8)
            math(GeneratorAdapter.MUL, Type.INT_TYPE)
            math(GeneratorAdapter.SHR, Type.INT_TYPE)
            push(0xFF); math(GeneratorAdapter.AND, Type.INT_TYPE)
            math(GeneratorAdapter.XOR, Type.INT_TYPE)
            cast(Type.INT_TYPE, Type.BYTE_TYPE)
            arrayStore(Type.BYTE_TYPE)

            iinc(i, 1); goTo(loop); mark(end)
            loadLocal(newKey)
            putStatic(deobfuscator.type.toAsmType(), "_cachedKey", BYTE_ARRAY_TYPE)
            mark(ready)
            getStatic(deobfuscator.type.toAsmType(), "_cachedKey", BYTE_ARRAY_TYPE)
        }
    }

    /** 生成核心解密方法 */
    private fun ClassVisitor.generateDecryptMethod() {
        when (mode) {
            ObfuscationMode.BYTES -> generateDecryptBytes()
            ObfuscationMode.BASE64 -> generateDecryptBase64()
            ObfuscationMode.HEX -> generateDecryptHex()
            ObfuscationMode.CUSTOM -> generateDecryptFormatted()
        }
    }

    private fun ClassVisitor.generateDecryptBytes() {
        newMethod(ACC_PUBLIC or ACC_STATIC, Method("decrypt", STRING_TYPE, arrayOf(BYTE_ARRAY_TYPE))) {
            getStatic(deobfuscator.type.toAsmType(), "PROCESSOR", STRING_DECRYPTOR_TYPE)
            loadArg(0)
            callGetKey()
            invokeInterface(STRING_DECRYPTOR_TYPE, METHOD_DECRYPT)
            checkCast(STRING_TYPE)
        }
    }

    private fun ClassVisitor.generateDecryptBase64() {
        newMethod(ACC_PUBLIC or ACC_STATIC, Method("decryptFromBase64", STRING_TYPE, arrayOf(STRING_TYPE))) {
            getStatic(deobfuscator.type.toAsmType(), "PROCESSOR", STRING_DECRYPTOR_TYPE)
            loadArg(0)
            invokeStatic(BASE64_DECODER_TYPE, Method("decode", "(Ljava/lang/String;)[B"))
            callGetKey()
            invokeInterface(STRING_DECRYPTOR_TYPE, METHOD_DECRYPT)
            checkCast(STRING_TYPE)
        }
    }

    private fun ClassVisitor.generateDecryptHex() {
        newMethod(ACC_PUBLIC or ACC_STATIC, Method("decryptFromHex", STRING_TYPE, arrayOf(STRING_TYPE))) {
            getStatic(deobfuscator.type.toAsmType(), "PROCESSOR", STRING_DECRYPTOR_TYPE)
            loadArg(0)
            invokeStatic(HEX_HELPER_TYPE, Method("decode", "(Ljava/lang/String;)[B"))
            callGetKey()
            invokeInterface(STRING_DECRYPTOR_TYPE, METHOD_DECRYPT)
            checkCast(STRING_TYPE)
        }
    }

    private fun ClassVisitor.generateDecryptFormatted() {
        newMethod(ACC_PUBLIC or ACC_STATIC, Method("decryptFormatted", STRING_TYPE, arrayOf(STRING_TYPE))) {
            getStatic(deobfuscator.type.toAsmType(), "PROCESSOR", STRING_DECRYPTOR_TYPE)
            getStatic(deobfuscator.type.toAsmType(), "PROCESSOR", STRING_DECRYPTOR_TYPE)
            loadArg(0)
            invokeInterface(STRING_DECRYPTOR_TYPE, Method("parseData", "(Ljava/lang/String;)[B"))
            callGetKey()
            invokeInterface(STRING_DECRYPTOR_TYPE, METHOD_DECRYPT)
            checkCast(STRING_TYPE)
        }
    }

    // 辅助：推入密钥（或 null）
    private fun GeneratorAdapter.callGetKey() {
        if (obfuscatedKey != null) {
            invokeStatic(deobfuscator.type.toAsmType(), Method("_getKey", "()[B"))
        } else {
            push(null as String?)
        }
    }

    // 通过索引获取集中存储数据
    private fun ClassVisitor.generateGetStringByIndexMethod() {
        newMethod(ACC_PUBLIC or ACC_STATIC, Method("getString", STRING_TYPE, arrayOf(Type.INT_TYPE))) {
            val entries = stringRegistry.getCentralizedEntries()
            if (entries.isEmpty()) { push(""); return@newMethod }

            when (mode) {
                ObfuscationMode.BYTES -> {
                    getStatic(deobfuscator.type.toAsmType(), "_DATA", BYTE_ARRAY_ARRAY_TYPE)
                    loadArg(0); arrayLoad(BYTE_ARRAY_TYPE)
                    invokeStatic(deobfuscator.type.toAsmType(), Method("decrypt", "([B)Ljava/lang/String;"))
                }
                ObfuscationMode.BASE64 -> {
                    getStatic(deobfuscator.type.toAsmType(), "_DATA", STRING_ARRAY_TYPE)
                    loadArg(0); arrayLoad(STRING_TYPE)
                    invokeStatic(deobfuscator.type.toAsmType(), Method("decryptFromBase64", "(Ljava/lang/String;)Ljava/lang/String;"))
                }
                ObfuscationMode.HEX -> {
                    getStatic(deobfuscator.type.toAsmType(), "_DATA", STRING_ARRAY_TYPE)
                    loadArg(0); arrayLoad(STRING_TYPE)
                    invokeStatic(deobfuscator.type.toAsmType(), Method("decryptFromHex", "(Ljava/lang/String;)Ljava/lang/String;"))
                }
                ObfuscationMode.CUSTOM -> {
                    getStatic(deobfuscator.type.toAsmType(), "_DATA", STRING_ARRAY_TYPE)
                    loadArg(0); arrayLoad(STRING_TYPE)
                    invokeStatic(deobfuscator.type.toAsmType(), Method("decryptFormatted", "(Ljava/lang/String;)Ljava/lang/String;"))
                }
            }
        }
    }

    companion object {
        @JvmStatic fun obfuscateKey(key: ByteArray, seed: Int) =
            ByteArray(key.size) { i -> (key[i].toInt() xor (seed ushr ((i % 4) * 8) and 0xFF)).toByte() }

        private val OBJECT_TYPE = Type.getObjectType("java/lang/Object")
        private val STRING_TYPE = Type.getType(String::class.java)
        private val STRING_ARRAY_TYPE = Type.getType(Array<String>::class.java)
        private val BYTE_ARRAY_TYPE = Type.getType(ByteArray::class.java)
        private val BYTE_ARRAY_ARRAY_TYPE = Type.getType(Array<ByteArray>::class.java)
        private val STRING_DECRYPTOR_TYPE = Type.getObjectType("com/androidacy/lsparanoid/StringDecryptor")
        private val BASE64_DECODER_TYPE = Type.getObjectType("com/androidacy/lsparanoid/Base64Decoder")
        private val HEX_HELPER_TYPE = Type.getObjectType("com/androidacy/lsparanoid/HexHelper")

        private val METHOD_CLINIT = Method("<clinit>", "()V")
        private val METHOD_INIT = Method("<init>", "()V")
        private val METHOD_DECRYPT = Method("decrypt", "([B[B)Ljava/lang/String;")
    }
}
