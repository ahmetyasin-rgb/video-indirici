package com.example.videodownloader

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Genel amaçlı video yakalayıcı:
 *  - Yerleşik tarayıcıda sayfayı açarsınız
 *  - Sayfa bir .mp4/.webm videosu yüklediğinde link altta listelenir
 *  - Butona dokununca indirme başlar (Downloads klasörüne)
 *
 * Not: .m3u8 (parçalı yayın) linkleri listelenir ama bu sürümde indirilemez
 *      (ek kütüphane gerekir). DRM korumalı videolar indirilemez.
 * Sadece indirme hakkınız olan içerikler için kullanın.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private lateinit var list: LinearLayout
    private val found = LinkedHashSet<String>()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val urlBar = EditText(this).apply {
            hint = "Adres yazın (https://...)"
            setSingleLine()
            imeOptions = EditorInfo.IME_ACTION_GO
            inputType = InputType.TYPE_TEXT_VARIATION_URI
        }

        web = WebView(this)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(list) }

        root.addView(urlBar, LinearLayout.LayoutParams(-1, -2))
        root.addView(web, LinearLayout.LayoutParams(-1, 0, 3f))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.mediaPlaybackRequiresUserGesture = true
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)

        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView, request: WebResourceRequest
            ): WebResourceResponse? {
                val url = request.url.toString()
                val path = url.substringBefore("?").lowercase()
                if (path.endsWith(".mp4") || path.endsWith(".webm") || path.endsWith(".m3u8")) {
                    runOnUiThread { videoEkle(url) }
                }
                return null
            }

            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                urlBar.setText(url)
            }
        }

        // Sayfa doğrudan dosya indirmesi başlatırsa
        web.setDownloadListener { url, _, _, _, _ -> videoEkle(url) }

        urlBar.setOnEditorActionListener { v, _, _ ->
            var adres = v.text.toString().trim()
            if (!adres.startsWith("http")) adres = "https://$adres"
            web.loadUrl(adres)
            true
        }

        web.loadUrl("https://www.google.com")
    }

    private fun videoEkle(url: String) {
        if (!found.add(url)) return
        val m3u8 = url.substringBefore("?").lowercase().endsWith(".m3u8")
        val etiket = (if (m3u8) "[m3u8 - desteklenmiyor] " else "⬇ İndir: ") + url.takeLast(60)
        val btn = Button(this).apply {
            text = etiket
            isAllCaps = false
            setOnClickListener {
                if (m3u8) {
                    Toast.makeText(context, "m3u8 bu sürümde desteklenmiyor", Toast.LENGTH_SHORT).show()
                } else {
                    indir(url)
                }
            }
        }
        list.addView(btn, 0)
    }

    private fun indir(url: String) {
        val ad = "video_${System.currentTimeMillis()}.mp4"
        val istek = DownloadManager.Request(Uri.parse(url))
            .setTitle(ad)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, ad)
            .addRequestHeader("User-Agent", web.settings.userAgentString)
            .addRequestHeader("Referer", web.url ?: "")
        CookieManager.getInstance().getCookie(url)?.let { istek.addRequestHeader("Cookie", it) }

        val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.enqueue(istek)
        Toast.makeText(this, "İndirme başladı: $ad", Toast.LENGTH_SHORT).show()
    }

    @Deprecated("Geri tuşu önce tarayıcıda geri gider")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }
}
