package com.androidacy.lsparanoid.testapp

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.androidacy.lsparanoid.Obfuscate
import kotlin.concurrent.Volatile

/**
 * Simple test activity with obfuscated strings to verify ProGuard rules work correctly.
 */
@Obfuscate
class MainActivity : Activity() {
    @Volatile
    var isReady: Boolean = false
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        KtSimpleClass().test()
        val rootLayout: ViewGroup = FrameLayout(this)
        val textView = TextView(this)
        textView.setText("This is a test ")
        rootLayout.addView(textView)
        setContentView(rootLayout)
        try {
            Log.d(TAG, MESSAGE_CREATED)
            Log.v(TAG, PRIVATE_MESSAGE)
            Log.i(TAG, LIFECYCLE_INFO)

            // Test accessing StringUtils obfuscated strings during onCreate
            val utilTag = StringUtils.UTILITY_TAG
            Log.d(TAG, "Accessed utility tag: " + utilTag)

            // Test various string types
            validateStrings()

            this.isReady = true
        } catch (e: Exception) {
            Log.e(TAG, ERROR_MESSAGE, e)
            throw RuntimeException("Failed to create activity with obfuscated strings", e)
        }
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, MESSAGE_STARTED)
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, MESSAGE_RESUMED)
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, MESSAGE_PAUSED)
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, MESSAGE_STOPPED)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, MESSAGE_DESTROYED)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(BUNDLE_KEY, "Test data: " + TAG)
    }

    val testString: String
        get() = "Test string with obfuscation: " + TAG

    /**
     * Validates that all obfuscated strings are accessible and correct.
     * Throws RuntimeException if any string is null or incorrect.
     */
    private fun validateStrings() {
        if (TAG == null || TAG != "MainActivity") {
            throw RuntimeException("TAG string validation failed")
        }
        if (MESSAGE_CREATED == null || MESSAGE_CREATED.isEmpty()) {
            throw RuntimeException("MESSAGE_CREATED validation failed")
        }
        if (API_ENDPOINT == null || !API_ENDPOINT.startsWith("https://")) {
            throw RuntimeException("API_ENDPOINT validation failed")
        }
        Log.d(TAG, "All obfuscated strings validated successfully")
    }

    companion object {
        // These strings will be obfuscated by LSParanoid
        const val TAG: String = "MainActivity"
        const val MESSAGE_CREATED: String = "Activity created successfully"
        const val MESSAGE_STARTED: String = "Activity started"
        const val MESSAGE_RESUMED: String = "Activity resumed"
        const val MESSAGE_PAUSED: String = "Activity paused"
        const val MESSAGE_STOPPED: String = "Activity stopped"
        const val MESSAGE_DESTROYED: String = "Activity destroyed"

        private const val PRIVATE_MESSAGE = "This is a private obfuscated string"
        private const val ERROR_MESSAGE = "An error occurred during activity lifecycle"
        private const val BUNDLE_KEY = "saved_state_data"

        // Additional test strings
        const val LIFECYCLE_INFO: String = "Activity lifecycle events are being tracked"
        const val API_ENDPOINT: String = "https://api.testapp.com/v2/data"
        const val USER_AGENT: String = "LSParanoid-TestApp/1.0 (Android)"
    }
}
