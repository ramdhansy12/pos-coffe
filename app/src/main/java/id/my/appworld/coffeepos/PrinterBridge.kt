package id.my.appworld.coffeepos

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Jembatan JavaScript -> printer Bluetooth (SPP/RFCOMM, jenis yang dipakai printer thermal 58 mm biasa).
 * Koneksi dibuka sekali dan dipakai ulang sampai aplikasi ditutup atau pengguna memutuskan.
 * Semua operasi Bluetooth berjalan di satu thread latar belakang agar halaman web tidak macet.
 */
@SuppressLint("MissingPermission")
class PrinterBridge(private val activity: MainActivity, private val web: WebView) {

    private val adapter: BluetoothAdapter? =
        (activity.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    private val io = Executors.newSingleThreadExecutor()
    private val sockets = ConcurrentHashMap<String, BluetoothSocket>()
    private val spp: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    // ---------------- dipanggil dari JavaScript ----------------

    @JavascriptInterface
    fun isAvailable(): Boolean = adapter != null

    /** Daftar perangkat Bluetooth yang sudah dipasangkan (JSON). */
    @JavascriptInterface
    fun listPrinters(): String {
        val a = adapter ?: return error("NO_BLUETOOTH", "Tablet ini tidak punya Bluetooth.")
        if (!hasPermission()) {
            activity.requestBluetoothPermission()
            return error("PERMISSION", "Izinkan akses Bluetooth pada tablet, lalu klik Muat ulang.")
        }
        if (!a.isEnabled) return error("OFF", "Bluetooth mati. Nyalakan Bluetooth lalu klik Muat ulang.")
        val arr = JSONArray()
        for (d in a.bondedDevices ?: emptySet()) {
            arr.put(JSONObject().put("name", d.name ?: d.address).put("address", d.address))
        }
        return JSONObject().put("ok", true).put("devices", arr).toString()
    }

    @JavascriptInterface
    fun status(address: String): String =
        if (sockets[address]?.isConnected == true) "connected" else "disconnected"

    /** Sambungkan ke printer; hasil dikirim balik ke window.__printerResult(cb, ok, pesan). */
    @JavascriptInterface
    fun connect(address: String, cb: String) {
        io.execute { reply(cb) { ensureSocket(address) } }
    }

    /** Kirim byte ESC/POS (base64) ke printer. */
    @JavascriptInterface
    fun print(address: String, base64: String, cb: String) {
        io.execute {
            reply(cb) {
                val data = Base64.decode(base64, Base64.DEFAULT)
                try {
                    write(address, data)
                } catch (e: IOException) {
                    closeQuietly(address)       // koneksi lama mati (printer sempat off): coba sekali lagi dengan koneksi baru
                    write(address, data)
                }
            }
        }
    }

    @JavascriptInterface
    fun disconnect(address: String) {
        io.execute { closeQuietly(address) }
    }

    @JavascriptInterface
    fun openBluetoothSettings() {
        activity.runOnUiThread {
            try {
                activity.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            } catch (ignored: Exception) {
            }
        }
    }

    // ---------------- internal ----------------

    fun close() {
        for (k in sockets.keys.toList()) closeQuietly(k)
        io.shutdown()
    }

    private fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            activity.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun error(code: String, message: String): String =
        JSONObject().put("ok", false).put("error", code).put("message", message).toString()

    private fun ensureSocket(address: String): BluetoothSocket {
        val existing = sockets[address]
        if (existing != null) {
            if (existing.isConnected) return existing
            closeQuietly(address)
        }
        if (!hasPermission()) {
            activity.requestBluetoothPermission()
            throw IOException("Izin Bluetooth belum diberikan. Izinkan, lalu coba lagi.")
        }
        val a = adapter ?: throw IOException("Tablet ini tidak punya Bluetooth.")
        if (!a.isEnabled) throw IOException("Bluetooth mati. Nyalakan Bluetooth lalu coba lagi.")

        val device = a.getRemoteDevice(address)
        a.cancelDiscovery()
        var socket = device.createRfcommSocketToServiceRecord(spp)
        try {
            socket.connect()
        } catch (first: IOException) {
            try {
                socket.close()
            } catch (ignored: IOException) {
            }
            try {
                // cadangan untuk printer murah yang tidak menjawab UUID SPP standar
                socket = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                    .invoke(device, 1) as BluetoothSocket
                socket.connect()
            } catch (second: Exception) {
                try {
                    socket.close()
                } catch (ignored: Exception) {
                }
                throw first
            }
        }
        sockets[address] = socket
        return socket
    }

    private fun write(address: String, data: ByteArray) {
        val out = ensureSocket(address).outputStream
        var i = 0
        while (i < data.size) {
            val n = minOf(512, data.size - i)
            out.write(data, i, n)
            i += n
            Thread.sleep(15)            // beri waktu buffer printer kecil
        }
        out.flush()
    }

    private fun closeQuietly(address: String) {
        val s = sockets.remove(address) ?: return
        try {
            s.close()
        } catch (ignored: IOException) {
        }
    }

    private fun friendly(e: Exception): String {
        val m = e.message ?: ""
        return when {
            e is IOException && (m.contains("read failed", true) || m.contains("closed", true) ||
                m.contains("Service discovery", true) || m.contains("timeout", true) || m.contains("refused", true)) ->
                "Tidak bisa terhubung ke printer. Pastikan printer menyala, dekat dengan tablet, dan tidak sedang dipakai perangkat lain."
            m.isNotBlank() -> m
            else -> "Gagal mencetak."
        }
    }

    /** Jalankan [block] lalu kabari JavaScript hasilnya. */
    private fun reply(cb: String, block: () -> Unit) {
        var ok = true
        var msg = ""
        try {
            block()
        } catch (e: Exception) {
            ok = false
            msg = friendly(e)
        }
        val js = "window.__printerResult && window.__printerResult(" +
            JSONObject.quote(cb) + ", " + ok + ", " + JSONObject.quote(msg) + ")"
        web.post { web.evaluateJavascript(js, null) }
    }
}
