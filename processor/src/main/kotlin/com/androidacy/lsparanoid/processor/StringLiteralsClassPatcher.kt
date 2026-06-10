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
import com.androidacy.lsparanoid.StringEncryptor
import com.joom.grip.mirrors.toAsmType
import com.androidacy.lsparanoid.processor.logging.getLogger
import com.androidacy.lsparanoid.processor.model.Deobfuscator
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.INVOKESTATIC
import org.objectweb.asm.Type
import org.objectweb.asm.commons.GeneratorAdapter
import org.objectweb.asm.commons.Method

/**
 * 字符串字面量替换。根据 [ObfuscationMode] 生成不同的字节码。
 */
class StringLiteralsClassPatcher(
    private val deobfuscator: Deobfuscator,
    private val stringRegistry: StringRegistry,
    private val stringEncryptor: StringEncryptor,
    private val mode: ObfuscationMode,
    asmApi: Int,
    delegate: ClassVisitor,
) : ClassVisitor(asmApi, delegate) {

    private val logger = getLogger()
    private var className = ""

    override fun visit(v: Int, a: Int, n: String, sig: String?, sn: String?, ifs: Array<out String>?) {
        super.visit(v, a, n, sig, sn, ifs)
        className = n
    }

    override fun visitMethod(a: Int, n: String, d: String, sig: String?, ex: Array<out String>?): MethodVisitor {
        val mv = super.visitMethod(a, n, d, sig, ex)

        // 根据 mode 决定调用哪个 Deobfuscator 方法
        val (inlineMethod, getStringMethod) = modeMethods()

        return object : GeneratorAdapter(api, mv, a, n, d) {
            override fun visitLdcInsn(c: Any) {
                if (c is String && stringEncryptor.shouldFog(c)) {
                    logger.info("{}.{}{}: \"{}\"", className, n, d, c)
                    val entry = stringRegistry.registerString(c)
                    when (entry) {
                        is StringEntry.Inline -> {
                            pushCodeData(entry.codeData, entry.codeData is ByteArray)
                            visitMethodInsn(INVOKESTATIC,
                                deobfuscator.type.internalName, inlineMethod.name, inlineMethod.descriptor, false)
                        }
                        is StringEntry.Centralized -> {
                            push(entry.index)
                            visitMethodInsn(INVOKESTATIC,
                                deobfuscator.type.internalName, getStringMethod.name, getStringMethod.descriptor, false)
                        }
                    }
                } else {
                    super.visitLdcInsn(c)
                }
            }

            /** 推入调用数据：ByteArray 用字节码构建，String 用 const-string */
            private fun pushCodeData(data: Any, isBytes: Boolean) {
                if (isBytes) {
                    val bytes = data as ByteArray
                    push(bytes.size); newArray(Type.BYTE_TYPE)
                    for (i in bytes.indices) { dup(); push(i); push(bytes[i].toInt()); arrayStore(Type.BYTE_TYPE) }
                } else {
                    push(data as String)
                }
            }
        }
    }

    /** 根据 mode 返回 (内联解密方法, 索引获取方法) */
    private fun modeMethods(): Pair<Method, Method> = when (mode) {
        ObfuscationMode.BYTES -> M_DECRYPT to M_GET_STRING
        ObfuscationMode.BASE64 -> M_DECRYPT_B64 to M_GET_STRING
        ObfuscationMode.HEX -> M_DECRYPT_HEX to M_GET_STRING
        ObfuscationMode.CUSTOM -> M_DECRYPT_FMT to M_GET_STRING
    }

    companion object {
        private val M_DECRYPT = Method("decrypt", "([B)Ljava/lang/String;")
        private val M_DECRYPT_B64 = Method("decryptFromBase64", "(Ljava/lang/String;)Ljava/lang/String;")
        private val M_DECRYPT_HEX = Method("decryptFromHex", "(Ljava/lang/String;)Ljava/lang/String;")
        private val M_DECRYPT_FMT = Method("decryptFormatted", "(Ljava/lang/String;)Ljava/lang/String;")
        private val M_GET_STRING = Method("getString", "(I)Ljava/lang/String;")
    }
}
