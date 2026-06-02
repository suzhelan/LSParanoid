package com.androidacy.lsparanoid.testapp

import com.androidacy.lsparanoid.Obfuscate

/**
 * Utility class with obfuscated strings for testing.
 */
@Obfuscate
object StringUtils {
    const val UTILITY_TAG: String = "StringUtils"
    const val TEST_MESSAGE_1: String = "First test message"
    const val TEST_MESSAGE_2: String = "Second test message"
    const val TEST_MESSAGE_3: String = "Third test message with special chars: !@#$%^&*()"

    // Test various string types
    const val URL_STRING: String = "https://api.example.com/v1/users?page=1&limit=10"
    const val JSON_STRING: String = "{\"name\":\"test\",\"value\":42,\"enabled\":true}"
    const val SQL_STRING: String = "SELECT * FROM users WHERE id = ? AND status = 'active'"
    const val ERROR_MESSAGE: String =
        "Error: Failed to connect to database. Please check your connection."
    const val MULTILINE_STRING: String =
        "This is a multi-line string.\nIt contains multiple lines.\nAnd should be properly obfuscated."

    // Unicode and international characters
    const val UNICODE_STRING: String = "Unicode test: \u2764\ufe0f \ud83d\ude80 \ud83c\udf89"
    const val CHINESE_STRING: String = "\u4f60\u597d\u4e16\u754c" // "Hello World" in Chinese
    const val EMOJI_STRING: String =
        "Emoji test: \ud83d\ude00 \ud83d\ude02 \ud83d\ude0d \ud83d\udc4d"
    const val ARABIC_STRING: String =
        "\u0645\u0631\u062d\u0628\u0627 \u0628\u0643" // "Welcome" in Arabic

    // Long strings
    const val LONG_STRING: String =
        "This is a very long string that should test the chunk loading mechanism of LSParanoid. " +
                "It contains multiple sentences and should be split across multiple chunks. " +
                "The obfuscation processor should handle this correctly and the runtime should be able to " +
                "reassemble the string properly from its obfuscated chunks. " +
                "This is important for real-world applications that may have lengthy strings."

    // Path and file strings
    const val FILE_PATH: String = "/data/data/com.androidacy.lsparanoid.testapp/files/config.json"
    const val PACKAGE_NAME: String = "com.androidacy.lsparanoid.testapp"

    // HTML/XML strings
    const val HTML_STRING: String = "<html><body><h1>Test</h1><p>Content</p></body></html>"
    const val XML_STRING: String = "<?xml version=\"1.0\"?><root><item id=\"1\">Value</item></root>"

    // Regex patterns
    const val EMAIL_REGEX: String = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$"
    const val PHONE_REGEX: String = "^\\+?[1-9]\\d{1,14}$"

    @JvmStatic
    fun concatenate(a: String?, b: String?): String {
        return "$a $b"
    }

    @JvmStatic
    fun getFormattedMessage(name: String): String {
        return "Hello, $name! This string is obfuscated."
    }

    @JvmStatic
    val allMessages: Array<String>
        get() = arrayOf(
            TEST_MESSAGE_1,
            TEST_MESSAGE_2,
            TEST_MESSAGE_3
        )

    @JvmStatic
    fun getUrlWithParam(param: String?): String {
        return "$URL_STRING&custom=$param"
    }

    @JvmStatic
    fun validateEmail(email: String?): Boolean {
        return email != null && email.matches(EMAIL_REGEX.toRegex())
    }
}
