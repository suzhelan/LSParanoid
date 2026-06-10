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
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type
import org.objectweb.asm.commons.GeneratorAdapter
import org.objectweb.asm.commons.Method

/**
 * static final String 字段常量替换。根据模式生成不同初始化代码。
 */
class StringConstantsClassPatcher(
    private val configuration: ClassConfiguration,
    private val stringRegistry: StringRegistry,
    private val stringEncryptor: StringEncryptor,
    private val mode: ObfuscationMode,
    private val deobfuscatorType: com.joom.grip.mirrors.Type.Object,
    asmApi: Int,
    delegate: ClassVisitor,
) : ClassVisitor(asmApi, delegate) {

    private val logger = getLogger()
    private var isClinitPatched = false

    override fun visit(v: Int, a: Int, n: String, sig: String?, sn: String?, ifs: Array<out String>?) {
        super.visit(v, a, n, sig, sn, ifs)
        isClinitPatched = configuration.constantStringsByFieldName.isEmpty()
    }

    override fun visitField(a: Int, n: String, d: String, sig: String?, v: Any?): FieldVisitor? {
        val nv = if (n in configuration.constantStringsByFieldName) null else v
        return super.visitField(a, n, d, sig, nv)
    }

    override fun visitMethod(a: Int, n: String, d: String, sig: String?, ex: Array<out String>?): MethodVisitor {
        val v = super.visitMethod(a, n, d, sig, ex)
        if (n == M_CLINIT.name) return patchClinit(v, a, n, d)
        return v
    }

    override fun visitEnd() {
        if (!isClinitPatched)
            GeneratorAdapter(ACC_PRIVATE or ACC_STATIC, M_CLINIT, null, null, this).apply {
                visitCode(); returnValue(); endMethod()
            }
        super.visitEnd()
    }

    private fun patchClinit(v: MethodVisitor, a: Int, n: String, d: String): MethodVisitor {
        if (isClinitPatched) return v
        isClinitPatched = true
        return object : GeneratorAdapter(api, v, a, n, d) {
            override fun visitCode() {
                logger.info("{}: patching <clinit>", configuration.container.internalName)
                super.visitCode()
                for ((field, value) in configuration.constantStringsByFieldName) {
                    if (!stringEncryptor.shouldFog(value)) {
                        push(value)
                        putStatic(configuration.container.toAsmType(), field, STRING_TYPE)
                        continue
                    }
                    val entry = stringRegistry.registerString(value)
                    when (entry) {
                        is StringEntry.Inline -> doInline(entry)
                        is StringEntry.Centralized -> {
                            push(entry.index)
                            invokeStatic(deobfuscatorType.toAsmType(), M_GET_STRING)
                        }
                    }
                    putStatic(configuration.container.toAsmType(), field, STRING_TYPE)
                }
            }

            private fun doInline(entry: StringEntry.Inline) {
                when (mode) {
                    ObfuscationMode.BYTES -> {
                        val bs = entry.codeData as ByteArray
                        push(bs.size); newArray(Type.BYTE_TYPE)
                        for (i in bs.indices) { dup(); push(i); push(bs[i].toInt()); arrayStore(Type.BYTE_TYPE) }
                        invokeStatic(deobfuscatorType.toAsmType(), M_DECRYPT)
                    }
                    ObfuscationMode.BASE64 -> {
                        push(entry.codeData as String)
                        invokeStatic(deobfuscatorType.toAsmType(), M_DECRYPT_B64)
                    }
                    ObfuscationMode.HEX -> {
                        push(entry.codeData as String)
                        invokeStatic(deobfuscatorType.toAsmType(), M_DECRYPT_HEX)
                    }
                    ObfuscationMode.CUSTOM -> {
                        push(entry.codeData as String)
                        invokeStatic(deobfuscatorType.toAsmType(), M_DECRYPT_FMT)
                    }
                }
            }
        }
    }

    companion object {
        private val M_CLINIT = Method("<clinit>", Type.VOID_TYPE, arrayOf())
        private val STRING_TYPE = Type.getType(String::class.java)
        private val M_DECRYPT = Method("decrypt", "([B)Ljava/lang/String;")
        private val M_DECRYPT_B64 = Method("decryptFromBase64", "(Ljava/lang/String;)Ljava/lang/String;")
        private val M_DECRYPT_HEX = Method("decryptFromHex", "(Ljava/lang/String;)Ljava/lang/String;")
        private val M_DECRYPT_FMT = Method("decryptFormatted", "(Ljava/lang/String;)Ljava/lang/String;")
        private val M_GET_STRING = Method("getString", "(I)Ljava/lang/String;")
    }
}
