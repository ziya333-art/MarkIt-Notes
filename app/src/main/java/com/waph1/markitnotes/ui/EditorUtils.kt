package com.waph1.markitnotes.ui

import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.viewinterop.AndroidView

private val toggleTaskRegex = Regex("- \\[[ xX]\\]")

/** Toggle a markdown checkbox at [index] to [checked] state. */
fun toggleTask(
    markdown: String,
    index: Int,
    checked: Boolean,
): String {
    var matchIndex = 0
    return toggleTaskRegex.replace(markdown) { matchResult ->
        if (matchIndex++ == index) {
            if (checked) "- [x]" else "- [ ]"
        } else {
            matchResult.value
        }
    }
}

/** Escape a string for safe embedding inside a JS string literal. */
fun String.escapeForJs(): String =
    this
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("`", "\\`")
        .replace("$", "\\$")
        .replace("\n", "\\n")
        .replace("\r", "")

/** WebView-based rendered Markdown preview with interactive checkbox support. */
@Composable
fun PreviewWebView(
    content: String,
    isDark: Boolean,
    onCheckboxToggled: (Int, Boolean) -> Unit,
) {
    val escapedContent = content.escapeForJs()

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(0)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true
                @Suppress("DEPRECATION")
                settings.allowFileAccessFromFileURLs = false
                @Suppress("DEPRECATION")
                settings.allowUniversalAccessFromFileURLs = false

                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun onCheckboxToggled(
                            index: Int,
                            checked: Boolean,
                        ) {
                            Handler(Looper.getMainLooper()).post {
                                onCheckboxToggled(index, checked)
                            }
                        }
                    },
                    "Android",
                )

                webViewClient =
                    object : WebViewClient() {
                        override fun onPageFinished(
                            view: WebView?,
                            url: String?,
                        ) {
                            val jsCode = "updateContent(\"$escapedContent\", $isDark)"
                            evaluateJavascript(jsCode, null)
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?,
                        ): Boolean {
                            val url = request?.url?.toString() ?: return false
                            if (url.startsWith("http://") || url.startsWith("https://")) {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                context.startActivity(intent)
                                return true
                            }
                            return false
                        }
                    }

                loadUrl("file:///android_asset/preview/preview.html?dark=$isDark")
            }
        },
        update = { view ->
            val jsCode = "updateContent(\"$escapedContent\", $isDark)"
            view.evaluateJavascript(jsCode, null)
        },
    )
}
