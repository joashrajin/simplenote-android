package com.automattic.simplenote

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.method.LinkMovementMethod
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import com.automattic.simplenote.compose.AboutLink
import com.automattic.simplenote.compose.AboutScreen
import com.automattic.simplenote.utils.BrowserUtils
import com.automattic.simplenote.utils.HtmlCompat
import com.automattic.simplenote.utils.PrefUtils
import com.automattic.simplenote.utils.SystemBarUtils
import com.automattic.simplenote.utils.ThemeUtils
import com.automattic.simplenote.utils.toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Calendar

open class AboutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTitle(R.string.about)
        // The About screen is blue in both modes, so force light bar icons.
        SystemBarUtils.applyEdgeToEdge(this, false)
        setScreenContent(PrefUtils.versionInfo().toString(), Calendar.getInstance().get(Calendar.YEAR))
    }

    override fun onResume() {
        super.onResume()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    /** Screenshot tests call this again with fixed values, since the version and year drift. */
    @VisibleForTesting
    internal fun setScreenContent(versionInfo: String, year: Int) {
        setContent {
            AboutScreen(
                versionInfo = versionInfo,
                year = year,
                onClose = { onBackPressedDispatcher.onBackPressed() },
                onLinkClick = ::openLink,
                onLogoMaximumSpeed = ::showSpinDialog,
            )
        }
    }

    private fun openLink(link: AboutLink) {
        when (link) {
            AboutLink.BLOG -> openInBrowser(SIMPLENOTE_BLOG_URL)
            AboutLink.TWITTER -> try {
                BrowserUtils.launchBrowserOrShowError(this, TWITTER_APP_URI + SIMPLENOTE_TWITTER_HANDLE)
            } catch (e: Exception) {
                BrowserUtils.launchBrowserOrShowError(this, TWITTER_PROFILE_URL + SIMPLENOTE_TWITTER_HANDLE)
            }
            AboutLink.HELP -> openInBrowser(SIMPLENOTE_HELP_URL)
            AboutLink.STORE -> openStore()
            AboutLink.CONTRIBUTE -> openInBrowser(URL_CONTRIBUTE)
            AboutLink.CAREERS -> openInBrowser(SIMPLENOTE_HIRING_HANDLE)
            AboutLink.PRIVACY -> openInBrowser(URL_PRIVACY)
            AboutLink.TERMS -> openInBrowser(URL_TERMS)
            // The one link that skips the browser check and toasts instead of the error dialog.
            AboutLink.CALIFORNIA -> try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(URL_CALIFORNIA)))
            } catch (e: Exception) {
                toast(R.string.no_browser_available, Toast.LENGTH_LONG)
            }
        }
    }

    private fun openInBrowser(url: String) {
        try {
            BrowserUtils.launchBrowserOrShowError(this, url)
        } catch (e: Exception) {
            toast(R.string.no_browser_available, Toast.LENGTH_LONG)
        }
    }

    private fun openStore() {
        val goToMarket = Intent(Intent.ACTION_VIEW, Uri.parse(PLAY_STORE_URI + packageName)).addFlags(
            Intent.FLAG_ACTIVITY_NO_HISTORY or
                Intent.FLAG_ACTIVITY_NEW_DOCUMENT or
                Intent.FLAG_ACTIVITY_MULTIPLE_TASK
        )
        try {
            startActivity(goToMarket)
        } catch (e: ActivityNotFoundException) {
            BrowserUtils.launchBrowserOrShowError(this, PLAY_STORE_URL + packageName)
        }
    }

    private fun showSpinDialog() {
        val items = resources.getStringArray(R.array.array_about)
        val item = items[(System.currentTimeMillis() % items.size).toInt()]
        val accent = Integer.toHexString(ThemeUtils.getColorFromAttribute(this, R.attr.colorAccent) and 0xffffff)
        // Theme.Simplestyle.About still parents MaterialComponents, so this renders the M2
        // MaterialAlertDialog (like the browser-error dialogs on this screen), not our M3 overlay.
        val dialog = MaterialAlertDialogBuilder(this)
            .setMessage(HtmlCompat.fromHtml(String.format(item, "<span style=\"color:#", accent, "\">", "</span>")))
            .show()
        val handler = Handler(Looper.getMainLooper())
        val dismiss = Runnable {
            if (dialog.isShowing) {
                dialog.dismiss()
            }
        }
        dialog.setOnDismissListener { handler.removeCallbacks(dismiss) }
        handler.postDelayed(dismiss, item.length * 50L)
        dialog.findViewById<TextView>(android.R.id.message)?.movementMethod = LinkMovementMethod.getInstance()
    }

    private companion object {
        const val PLAY_STORE_URL = "https://play.google.com/store/apps/details?id="
        const val PLAY_STORE_URI = "market://details?id="
        const val SIMPLENOTE_BLOG_URL = "https://simplenote.com/blog"
        const val SIMPLENOTE_HELP_URL = "https://simplenote.com/help"
        const val SIMPLENOTE_HIRING_HANDLE = "https://automattic.com/work-with-us/"
        const val SIMPLENOTE_TWITTER_HANDLE = "simplenoteapp"
        const val TWITTER_PROFILE_URL = "https://twitter.com/#!/"
        const val TWITTER_APP_URI = "twitter://user?screen_name="
        const val URL_CALIFORNIA = "https://automattic.com/privacy/#us-privacy-laws"
        const val URL_CONTRIBUTE = "https://github.com/Automattic/simplenote-android"
        const val URL_PRIVACY = "https://automattic.com/privacy"
        const val URL_TERMS = "https://simplenote.com/terms"
    }
}
