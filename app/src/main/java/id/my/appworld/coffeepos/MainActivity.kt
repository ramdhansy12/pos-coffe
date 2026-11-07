package id.my.appworld.coffeepos

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Cangkang aplikasi untuk Coffee POS: membuka web aplikasi layar penuh (tanpa Chrome)
 * dan menyediakan jembatan Bluetooth ke printer thermal lewat objek JavaScript "AndroidPrinter".
 */
class MainActivity : Activity() {

    private lateinit var web: WebView
    private lateinit var bridge: PrinterBridge
    private var fileCallback: ValueCallback<Array<Uri>>? = null

    companion object {
        private const val REQ_FILE = 1
        private const val REQ_BT = 2
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)   // layar kasir tidak mati sendiri

        val home = getString(R.string.home_url)
        val host = Uri.parse(home).host

        web = WebView(this)
        setContentView(web)

        val s = web.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.allowFileAccess = false
        s.setSupportZoom(false)
        s.builtInZoomControls = false
        s.userAgentString = s.userAgentString + " CoffeePOSApp/1.0"

        CookieManager.getInstance().setAcceptCookie(true)

        bridge = PrinterBridge(this, web)
        web.addJavascriptInterface(bridge, "AndroidPrinter")

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val u = request.url
                if (u.scheme == "https" && u.host == host) return false      // halaman aplikasi
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, u))              // link luar dibuka di browser
                } catch (ignored: Exception) {
                }
                return true
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    view.loadDataWithBaseURL(null, errorHtml(home), "text/html", "utf-8", null)
                }
            }
        }

        // Unggah foto menu (tombol pilih file di halaman web)
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                return try {
                    startActivityForResult(params.createIntent(), REQ_FILE)
                    true
                } catch (e: Exception) {
                    fileCallback = null
                    false
                }
            }
        }

        if (savedInstanceState == null) web.loadUrl(home) else web.restoreState(savedInstanceState)
    }

    /** Dipanggil jembatan printer (Android 12+ butuh izin BLUETOOTH_CONNECT saat dijalankan). */
    fun requestBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runOnUiThread {
                requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), REQ_BT)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQ_FILE) {
            fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data))
            fileCallback = null
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()      // simpan sesi login
    }

    override fun onDestroy() {
        bridge.close()
        super.onDestroy()
    }

    private fun errorHtml(home: String): String =
        "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'></head>" +
            "<body style='font-family:sans-serif;text-align:center;padding:15% 8%;color:#1d2b27'>" +
            "<h2>Tidak bisa terhubung</h2>" +
            "<p>Periksa koneksi internet tablet, lalu coba lagi.</p>" +
            "<p><a href='$home' style='display:inline-block;padding:12px 28px;background:#2f7560;color:#fff;" +
            "border-radius:8px;text-decoration:none'>Coba lagi</a></p></body></html>"
}
