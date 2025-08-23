package tech.syncvr.mdm_agent.platform_sdk

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.PERMISSION_DENIED
import android.content.pm.Signature
import android.os.Binder
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import tech.syncvr.platform_sdk.BuildConfig
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.cert.CertificateEncodingException
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlatformServiceAccessControl @Inject constructor() {
    companion object {
        val TAG: String = this::class.java.declaringClass.simpleName
        const val PERMISSION_PLATFORM_SDK = "tech.syncvr.permission.PLATFORM_SDK"
    }

    private val authorizedPackagesFingerprintMap =
        mapOf(
            "tech.syncvr.spectating_agent.beta" to
                    "D9:4B:5E:AD:67:FF:6E:77:66:16:BE:0A:C9:CF:AE:EB:D1:EA:0E:02",
            "tech.syncvr.spectating_agent" to
                    "D9:4B:5E:AD:67:FF:6E:77:66:16:BE:0A:C9:CF:AE:EB:D1:EA:0E:02"
        )

    internal fun checkCallingPackageSigningCert(context: Context) {
        val authorized = isCallingPackageSigningCert(context)
        if (!authorized) {
            throw SecurityException("Unauthorized: caller package or fingerprint unknown")
        }
    }

    internal fun checkCallingPackagePermission(context: Context) {
        if (PERMISSION_DENIED == context.checkCallingPermission(PERMISSION_PLATFORM_SDK)) {
            throw SecurityException(
                "Unauthorized: caller package doesn't have permission $PERMISSION_PLATFORM_SDK"
            )
        }
    }

    private fun getCallingUidPackageFingerprintPairs(context: Context): List<Pair<String, String?>>? {
        val callingUid = Binder.getCallingUid()
        val packagesForCallingUid = context.packageManager.getPackagesForUid(callingUid)
        return packagesForCallingUid?.map { callingPackage ->
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                Pair(callingPackage, getCertificateSHA1FingerprintPreP(context, callingPackage))
            } else {
                Pair(callingPackage, getCertificateSHA1Fingerprint(context, callingPackage))
            }
        }
    }

    private fun isCallingPackageSigningCert(context: Context): Boolean {
        val packageFingerprintPairsFromUid = getCallingUidPackageFingerprintPairs(context)
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Signing cert based authorization disabled due to debug mode")
            Log.d(TAG, "Calling packages and fingerprints $packageFingerprintPairsFromUid")
            return true
        }
        val matchedAuthorizedPairs = packageFingerprintPairsFromUid?.filter { (packageName, fingerprint) ->
            authorizedPackagesFingerprintMap[packageName]?.equals(fingerprint) == true
        }
        return matchedAuthorizedPairs?.isNotEmpty() == true
    }

    private fun getCertificateSHA1FingerprintPreP(
        context: Context,
        packageName: String
    ): String? {
        val pm: PackageManager = context.packageManager
        val flags = PackageManager.GET_SIGNATURES
        var packageInfo: PackageInfo? = null
        try {
            packageInfo = pm.getPackageInfo(packageName, flags)
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
        val signatures = packageInfo!!.signatures
        val cert = signatures[0].toByteArray()
        val input: InputStream = ByteArrayInputStream(cert)
        var cf: CertificateFactory? = null
        try {
            cf = CertificateFactory.getInstance("X509")
        } catch (e: CertificateException) {
            e.printStackTrace()
        }
        var c: X509Certificate? = null
        try {
            c = cf!!.generateCertificate(input) as X509Certificate
        } catch (e: CertificateException) {
            e.printStackTrace()
        }
        var hexString: String? = null
        try {
            val md = MessageDigest.getInstance("SHA1")
            val publicKey = md.digest(c!!.encoded)
            hexString = byte2HexFormatted(publicKey)
        } catch (e1: NoSuchAlgorithmException) {
            e1.printStackTrace()
        } catch (e: CertificateEncodingException) {
            e.printStackTrace()
        }
        return hexString
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun getCertificateSHA1Fingerprint(context: Context, packageName: String): String? {
        val pm: PackageManager = context.packageManager
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        var packageInfo: PackageInfo? = null
        try {
            packageInfo = pm.getPackageInfo(packageName, flags)
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
        val signatures: Array<Signature> = packageInfo!!.signingInfo.signingCertificateHistory
        val cert: ByteArray = signatures[0].toByteArray()
        val input: InputStream = ByteArrayInputStream(cert)
        var cf: CertificateFactory? = null
        try {
            cf = CertificateFactory.getInstance("X509")
        } catch (e: CertificateException) {
            e.printStackTrace()
        }
        var c: X509Certificate? = null
        try {
            c = cf?.generateCertificate(input) as X509Certificate
        } catch (e: CertificateException) {
            e.printStackTrace()
        }
        var hexString: String? = null
        try {
            val md: MessageDigest = MessageDigest.getInstance("SHA1")
            val publicKey: ByteArray = md.digest(c?.encoded)
            hexString = byte2HexFormatted(publicKey)
        } catch (e1: NoSuchAlgorithmException) {
            e1.printStackTrace()
        } catch (e: CertificateEncodingException) {
            e.printStackTrace()
        }
        return hexString
    }

    fun byte2HexFormatted(arr: ByteArray): String? {
        val str = StringBuilder(arr.size * 2)
        for (i in arr.indices) {
            var h = Integer.toHexString(arr[i].toInt())
            val l = h.length
            if (l == 1) h = "0$h"
            if (l > 2) h = h.substring(l - 2, l)
            str.append(h.uppercase(Locale.getDefault()))
            if (i < arr.size - 1) str.append(':')
        }
        return str.toString()
    }
}