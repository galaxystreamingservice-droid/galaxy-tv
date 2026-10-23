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

    /** Returns true when connected AND authorized. Blocks — call off the main thread. */
    fun connect(ctx: Context): Boolean {
        val mgr = AdbConnectionManager.get(ctx)
        if (mgr.isConnected) return true
        return try { mgr.connect(HOST, PORT) } catch (e: Exception) { false }
    }

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
