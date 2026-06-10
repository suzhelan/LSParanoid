# LSParanoid Consumer Rules
# ========================================
# These rules are automatically applied to projects that depend on the core module.
# They ensure that runtime reflection and generated code work correctly,
# while allowing R8/ProGuard to strip compile-time-only methods (encrypt, shouldFog, formatData).

# ========================================
# StringDecryptor interface (runtime)
# ========================================
# The generated Deobfuscator class calls decrypt/parseData via this interface.
-keep,allowobfuscation interface com.androidacy.lsparanoid.StringDecryptor {
    public java.lang.String decrypt(byte[], byte[]);
    public byte[] parseData(java.lang.String);
}

# Keep decrypt/parseData implementations (called via interface dispatch by Deobfuscator)
-keepclassmembers class * implements com.androidacy.lsparanoid.StringDecryptor {
    public java.lang.String decrypt(byte[], byte[]);
    public byte[] parseData(java.lang.String);
}

# NOTE: encrypt, shouldFog, formatData are compile-time-only (StringEncryptor interface).
# They are NEVER called at runtime, so R8/ProGuard can safely remove them.
# No keep rules are needed for StringEncryptor or its methods.

# ========================================
# Generated Deobfuscator classes
# ========================================
-keep,allowobfuscation class **.Deobfuscator** {
    # decrypt methods - called from obfuscated code
    public static java.lang.String decrypt(byte[]);
    public static java.lang.String decryptFromBase64(java.lang.String);
    public static java.lang.String decryptFromHex(java.lang.String);
    public static java.lang.String decryptFormatted(java.lang.String);
    public static java.lang.String getString(int);

    # key recovery
    static byte[] _getKey();
    static byte[][] _DATA;
    static java.lang.String[] _DATA;
}

# ========================================
# Runtime helpers used by generated Deobfuscator
# ========================================

# Base64Decoder - used in BASE64 mode
-keep,allowobfuscation class com.androidacy.lsparanoid.Base64Decoder {
    public static byte[] decode(java.lang.String);
}

# HexHelper - used in HEX mode
-keep,allowobfuscation class com.androidacy.lsparanoid.HexHelper {
    public static byte[] decode(java.lang.String);
}
