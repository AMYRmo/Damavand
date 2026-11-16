package com.amyrmo.damavand.lite.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.amyrmo.damavand.lite.AppLog
import com.amyrmo.damavand.lite.MainActivity
import com.amyrmo.damavand.lite.ProfileRepository
import com.amyrmo.damavand.lite.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLDecoder

/**
 * Deep links (3.2.11, PattNG ui/UrlSchemeActivity): the app opens for
 * shared text and for damavandlite:// links.
 *
 * - ACTION_SEND with text/plain shares a config link (or a subscription
 *   URL) into the app.
 * - damavandlite://install-config?url=<link> installs one config;
 *   damavandlite://install-sub?url=<sub-url> adds a subscription.
 * The text is URL-decoded, the fragment is appended when the link has
 * none of its own, and the import reports success or failure with a
 * toast before the main screen opens.
 *
 * Lite adaptations: the import goes through ProfileRepository —
 * addSingleLinks for configs (a "{" body is wrapped as custom://,
 * the way the file import does it) and addSubscription for subs —
 * and the activity is a plain ComponentActivity, not a Compose one.
 */
class UrlSchemeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            // The import runs first and the main screen opens after it:
            // finishing here would cancel the import with the activity.
            // The translucent theme keeps this activity invisible meanwhile.
            val (text, isSub) = intent.let {
                when {
                    it.action == Intent.ACTION_SEND && "text/plain" == it.type ->
                        it.getStringExtra(Intent.EXTRA_TEXT) to false
                    it.action == Intent.ACTION_VIEW && it.data?.host == "install-config" ->
                        it.data?.getQueryParameter("url") to false
                    it.action == Intent.ACTION_VIEW && it.data?.host == "install-sub" ->
                        it.data?.getQueryParameter("url") to true
                    else -> null to false
                }
            }
            val fragment = intent.data?.fragment
            if (text.isNullOrEmpty()) {
                toastFailure()
                openMain()
                return
            }
            parseUri(text, fragment, isSub)
        } catch (e: Exception) {
            AppLog.w("UrlScheme", "error processing URL scheme: ${e.message}")
            openMain()
        }
    }

    private fun openMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun parseUri(uriString: String?, fragment: String?, isSub: Boolean = false) {
        if (uriString.isNullOrEmpty()) {
            return
        }
        AppLog.i("UrlScheme", uriString)

        // getQueryParameter() has already decoded a deep link's url, and
        // shared text is not URL-encoded. Decoding again turned every '+'
        // of a base64 link (vmess://, ss://) into a space and threw on a
        // literal '%' — so only decode text that is still fully encoded
        // ("vless%3A%2F%2F…").
        var decodedUrl = if ("://" in uriString) uriString else URLDecoder.decode(uriString, "UTF-8")
        val uri = Uri.parse(decodedUrl)
        if (uri != null) {
            if (uri.fragment.isNullOrEmpty() && !fragment.isNullOrEmpty()) {
                decodedUrl += "#${fragment}"
            }
            AppLog.i("UrlScheme", decodedUrl)
            lifecycleScope.launch(Dispatchers.IO) {
                val repo = ProfileRepository(applicationContext)
                val ok = if (isSub) {
                    repo.addSubscription(decodedUrl) is ProfileRepository.FetchResult.Ok
                } else {
                    val normalized = normalize(decodedUrl)
                    when (repo.addSingleLinks(normalized)) {
                        is ProfileRepository.FetchResult.Ok -> true
                        else -> {
                            // A shared subscription URL arrives as plain text too.
                            val trimmed = decodedUrl.trim()
                            (trimmed.startsWith("http://") || trimmed.startsWith("https://")) &&
                                repo.addSubscription(trimmed) is ProfileRepository.FetchResult.Ok
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@UrlSchemeActivity,
                        getString(
                            if (ok) R.string.import_subscription_success
                            else R.string.import_subscription_failure
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                    openMain()
                }
            }
        }
    }

    /** A "{" body is a raw custom config: wrap it as custom://, the way the file import does. */
    private fun normalize(text: String): String {
        val trimmed = text.trim()
        if (!trimmed.startsWith("{")) return trimmed
        val b64 = android.util.Base64.encodeToString(
            trimmed.toByteArray(Charsets.UTF_8),
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
        )
        return "custom://$b64"
    }

    private fun toastFailure() {
        Toast.makeText(this, getString(R.string.import_subscription_failure), Toast.LENGTH_SHORT).show()
    }
}
