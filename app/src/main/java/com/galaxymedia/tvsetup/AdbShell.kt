package com.galaxymedia.tvsetup

import android.content.Context
import android.os.Build
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Date

/**
 * Talks to THIS device's own ADB daemon over loopback (127.0.0.1:5555).
 * Requires "ADB Debugging" ON in Developer Options. The first connect pops the
 * "Allow USB debugging?" dialog on the TV — the user ticks "Always allow" once.
 */
class AdbConnectionManager private constructor(ctx: Context) : AbsAdbConnectionManager() {

    private val privateKey: PrivateKey
    private val cert: Certificate

    init {
        setApi(Build.VERSION.SDK_INT)
        val dir = File(ctx.filesDir, "adbkeys").apply { mkdirs() }
        val privFile = File(dir, "private.pkcs8")
        val pubFile = File(dir, "public.x509")
        val certFile = File(dir, "cert.der")

        if (privFile.exists() && pubFile.exists() && certFile.exists()) {
            val kf = KeyFactory.getInstance("RSA")
            privateKey = kf.generatePrivate(PKCS8EncodedKeySpec(privFile.readBytes()))
            cert = CertificateFactory.getInstance("X.509").generateCertificate(certFile.inputStream())
        } else {
            val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            privateKey = kp.private
            cert = selfSign(kp)
            privFile.writeBytes(kp.private.encoded)
            pubFile.writeBytes(kp.public.encoded)
            certFile.writeBytes(cert.encoded)
        }
    }

    private fun selfSign(kp: KeyPair): Certificate {
        val bc = BouncyCastleProvider()
        val name = X500Name("CN=GalaxyTVSetup")
        val notBefore = Date()
        val notAfter = Date(notBefore.time + 10L * 365 * 24 * 60 * 60 * 1000)
        val builder = JcaX509v3CertificateBuilder(
            name, BigInteger.valueOf(System.currentTimeMillis()), notBefore, notAfter, name, kp.public
        )
        val signer = JcaContentSignerBuilder("SHA256withRSA").setProvider(bc).build(kp.private)
        return JcaX509CertificateConverter().setProvider(bc).getCertificate(builder.build(signer))
    }

    override fun getPrivateKey(): PrivateKey = privateKey
    override fun getCertificate(): Certificate = cert
    override fun getDeviceName(): String = "GalaxyTVSetup"

    companion object {
        @Volatile private var instance: AdbConnectionManager? = null
        fun get(ctx: Context): AdbConnectionManager =
            instance ?: synchronized(this) { instance ?: AdbConnectionManager(ctx.applicationContext).also { instance = it } }
    }
}

object AdbShell {
    private const val HOST = "127.0.0.1"
    private const val PORT = 5555

    /** The device's own non-loopback IPv4 (Wi-Fi/Ethernet), or null. */
    fun localLanIp(): String? = try {
        java.net.NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is java.net.Inet4Address && !it.isLoopbackAddress }
            ?.hostAddress
    } catch (e: Exception) { null }

    /** Hosts to try, in order: loopback, then our own LAN address. */
    fun candidateHosts(): List<String> = listOfNotNull(HOST, localLanIp()).distinct()

    data class ConnectResult(val ok: Boolean, val detail: String)

    /**
     * Connect with a clear diagnosis of what went wrong. Blocks — call off the main thread.
     * Step 1: raw TCP probe (is anything listening on 5555 at all?).
     * Step 2: real ADB handshake (this is what triggers the on-screen Allow dialog).
     */
    fun connectDetailed(ctx: Context): ConnectResult {
        val mgr = AdbConnectionManager.get(ctx)
        if (mgr.isConnected) return ConnectResult(true, "already connected")

        val notes = StringBuilder()
        for (host in candidateHosts()) {
            // Step 1 — is port 5555 open on this address?
            try {
                java.net.Socket().use { it.connect(java.net.InetSocketAddress(host, PORT), 3000) }
            } catch (e: Exception) {
                notes.append("[$host] PORT CLOSED (${e.javaClass.simpleName})\n"); continue
            }
            // Step 2 — ADB handshake + RSA auth (dialog should appear here)
            try {
                if (mgr.connect(host, PORT)) return ConnectResult(true, "[$host] handshake OK, authorized")
                notes.append("[$host] connect() returned false — dialog pending, dismissed, or denied\n")
            } catch (e: Exception) {
                notes.append("[$host] handshake FAILED: ").append(describe(e)).append("\n")
            }
        }
        return ConnectResult(false, notes.toString().trimEnd())
    }

    /** Exception + every nested cause + first few stack frames, so nothing is hidden. */
    private fun describe(e: Throwable): String {
        val sb = StringBuilder()
        var t: Throwable? = e
        var depth = 0
        while (t != null && depth < 5) {
            if (depth > 0) sb.append("\n   caused by: ")
            sb.append(t.javaClass.simpleName).append(": ").append(t.message)
            t.stackTrace.take(3).forEach { sb.append("\n      at ").append(it.className.substringAfterLast('.')).append('.').append(it.methodName).append(':').append(it.lineNumber) }
            t = t.cause; depth++
        }
        return sb.toString()
    }

    /**
     * Talks the raw ADB wire protocol with NO library: sends CNXN, reports what adbd answers.
     * Tells us whether the daemon itself is healthy on loopback, independent of libadb.
     */
    fun rawProbe(host: String = HOST): String {
        val A_CNXN = 0x4e584e43; val A_AUTH = 0x48545541; val A_CLSE = 0x45534c43
        fun name(c: Int) = when (c) { A_CNXN -> "CNXN"; A_AUTH -> "AUTH"; A_CLSE -> "CLSE"; else -> "0x%08x".format(c) }
        return try {
            java.net.Socket().use { sock ->
                sock.connect(java.net.InetSocketAddress(host, PORT), 3000)
                sock.soTimeout = 5000
                val payload = "host::\u0000".toByteArray(Charsets.US_ASCII)
                val hdr = java.nio.ByteBuffer.allocate(24).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                hdr.putInt(A_CNXN).putInt(0x01000001).putInt(1024 * 1024)
                    .putInt(payload.size).putInt(payload.sumOf { it.toInt() and 0xff }).putInt(A_CNXN.inv())
                sock.getOutputStream().apply { write(hdr.array()); write(payload); flush() }

                val inp = java.io.DataInputStream(sock.getInputStream())
                val resp = ByteArray(24); inp.readFully(resp)
                val rb = java.nio.ByteBuffer.wrap(resp).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                val cmd = rb.getInt(); val arg0 = rb.getInt(); rb.getInt(); val len = rb.getInt()
                val body = ByteArray(len); if (len > 0) inp.readFully(body)
                when (cmd) {
                    A_AUTH -> "adbd replied AUTH (type=$arg0, ${len}B token) — daemon healthy, wants RSA auth"
                    A_CNXN -> "adbd replied CNXN immediately — no auth required (!)"
                    else   -> "adbd replied ${name(cmd)} arg0=$arg0 len=$len"
                }
            }
        } catch (e: java.io.EOFException) { "adbd CLOSED the connection without replying"
        } catch (e: java.net.SocketTimeoutException) { "adbd accepted TCP but never replied (5s)"
        } catch (e: Exception) { "probe error: ${e.javaClass.simpleName}: ${e.message}" }
    }

    /** Returns true when connected AND authorized. Blocks — call off the main thread. */
    fun connect(ctx: Context): Boolean = connectDetailed(ctx).ok

    /** Run one shell command and return its combined output. */
    fun exec(ctx: Context, cmd: String): String {
        val mgr = AdbConnectionManager.get(ctx)
        if (!mgr.isConnected && !connect(ctx)) return "ERROR: not connected"
        val stream = mgr.openStream("shell:$cmd")
        return try {
            stream.openInputStream().bufferedReader().readText()
        } finally {
            stream.close()
        }
    }
}
