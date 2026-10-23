package com.galaxymedia.tvsetup

import android.content.Context
import android.os.Build
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.cert.Certificate
import java.util.Date
import sun.security.x509.AlgorithmId
import sun.security.x509.CertificateAlgorithmId
import sun.security.x509.CertificateSerialNumber
import sun.security.x509.CertificateValidity
import sun.security.x509.CertificateVersion
import sun.security.x509.CertificateX509Key
import sun.security.x509.X500Name
import sun.security.x509.X509CertImpl
import sun.security.x509.X509CertInfo

/**
 * Talks to THIS device's own ADB daemon over loopback (127.0.0.1:5555).
 * Requires "ADB Debugging" ON in Developer Options. The first connect pops the
 * "Allow USB debugging?" dialog on the TV — the user ticks "Always allow" once.
 */
class AdbConnectionManager private constructor(ctx: Context) : AbsAdbConnectionManager() {

    private val keyPair: KeyPair
    private val cert: Certificate

    init {
        setApi(Build.VERSION.SDK_INT)
        val dir = File(ctx.filesDir, "adbkeys").apply { mkdirs() }
        val privFile = File(dir, "private.key")
        val certFile = File(dir, "cert.crt")

        if (privFile.exists() && certFile.exists()) {
            keyPair = ObjectInputStream(FileInputStream(privFile)).use { it.readObject() as KeyPair }
            cert = ObjectInputStream(FileInputStream(certFile)).use { it.readObject() as Certificate }
        } else {
            keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            cert = selfSign(keyPair)
            ObjectOutputStream(FileOutputStream(privFile)).use { it.writeObject(keyPair) }
            ObjectOutputStream(FileOutputStream(certFile)).use { it.writeObject(cert) }
        }
    }

    private fun selfSign(kp: KeyPair): Certificate {
        val name = X500Name("CN=GalaxyTVSetup")
        val notBefore = Date()
        val notAfter = Date(notBefore.time + 10L * 365 * 24 * 60 * 60 * 1000)
        val info = X509CertInfo().apply {
            set(X509CertInfo.VERSION, CertificateVersion(CertificateVersion.V3))
            set(X509CertInfo.SERIAL_NUMBER, CertificateSerialNumber(BigInteger.valueOf(System.currentTimeMillis())))
            set(X509CertInfo.ALGORITHM_ID, CertificateAlgorithmId(AlgorithmId.get("SHA512withRSA")))
            set(X509CertInfo.SUBJECT, name)
            set(X509CertInfo.ISSUER, name)
            set(X509CertInfo.KEY, CertificateX509Key(kp.public))
            set(X509CertInfo.VALIDITY, CertificateValidity(notBefore, notAfter))
        }
        return X509CertImpl(info).apply { sign(kp.private, "SHA512withRSA") }
    }

    override fun getPrivateKey(): PrivateKey = keyPair.private
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
        return stream.openInputStream().bufferedReader().use { it.readText() }.also { stream.close() }
    }
}
