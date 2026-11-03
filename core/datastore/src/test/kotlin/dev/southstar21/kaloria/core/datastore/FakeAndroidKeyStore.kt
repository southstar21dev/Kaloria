package dev.southstar21.kaloria.core.datastore

import android.security.keystore.KeyGenParameterSpec
import java.io.InputStream
import java.io.OutputStream
import java.security.Key
import java.security.KeyStore
import java.security.KeyStoreSpi
import java.security.Provider
import java.security.SecureRandom
import java.security.Security
import java.security.cert.Certificate
import java.security.spec.AlgorithmParameterSpec
import java.util.Collections
import java.util.Date
import java.util.Enumeration
import javax.crypto.KeyGenerator
import javax.crypto.KeyGeneratorSpi
import javax.crypto.SecretKey

/**
 * Robolectric has no "AndroidKeyStore". This JCA provider stands in for it: keys are plain AES keys
 * kept in memory, which is all SessionStore needs (generate once under an alias, look it up later).
 */
@Suppress("DEPRECATION") // android.jar only has the (String, double, String) constructor
class FakeAndroidKeyStore : Provider("AndroidKeyStore", 1.0, "Fake Android Keystore for tests") {
    init {
        put("KeyStore.AndroidKeyStore", FakeKeyStoreSpi::class.java.name)
        put("KeyGenerator.AES", FakeAesKeyGeneratorSpi::class.java.name)
    }

    companion object {
        val keys: MutableMap<String, SecretKey> = Collections.synchronizedMap(mutableMapOf())

        fun install() {
            Security.removeProvider("AndroidKeyStore")
            Security.insertProviderAt(FakeAndroidKeyStore(), 1)
        }
    }
}

class FakeKeyStoreSpi : KeyStoreSpi() {
    private val keys get() = FakeAndroidKeyStore.keys

    override fun engineGetEntry(alias: String, protParam: KeyStore.ProtectionParameter?): KeyStore.Entry? = keys[alias]?.let(KeyStore::SecretKeyEntry)

    override fun engineGetKey(alias: String, password: CharArray?): Key? = keys[alias]
    override fun engineGetCertificateChain(alias: String): Array<Certificate>? = null
    override fun engineGetCertificate(alias: String): Certificate? = null
    override fun engineGetCreationDate(alias: String): Date? = null
    override fun engineSetKeyEntry(alias: String, key: Key, password: CharArray?, chain: Array<out Certificate>?) {
        keys[alias] = key as SecretKey
    }
    override fun engineSetKeyEntry(alias: String, key: ByteArray, chain: Array<out Certificate>?) = throw UnsupportedOperationException()
    override fun engineSetCertificateEntry(alias: String, cert: Certificate) = throw UnsupportedOperationException()
    override fun engineDeleteEntry(alias: String) {
        keys.remove(alias)
    }
    override fun engineAliases(): Enumeration<String> = Collections.enumeration(keys.keys.toList())
    override fun engineContainsAlias(alias: String) = alias in keys
    override fun engineSize() = keys.size
    override fun engineIsKeyEntry(alias: String) = alias in keys
    override fun engineIsCertificateEntry(alias: String) = false
    override fun engineGetCertificateAlias(cert: Certificate): String? = null
    override fun engineStore(stream: OutputStream?, password: CharArray?) = Unit
    override fun engineLoad(stream: InputStream?, password: CharArray?) = Unit
}

class FakeAesKeyGeneratorSpi : KeyGeneratorSpi() {
    private var alias: String? = null

    override fun engineInit(random: SecureRandom?) = Unit
    override fun engineInit(params: AlgorithmParameterSpec?, random: SecureRandom?) {
        alias = (params as? KeyGenParameterSpec)?.keystoreAlias
    }
    override fun engineInit(keysize: Int, random: SecureRandom?) = Unit

    override fun engineGenerateKey(): SecretKey {
        // The JDK's own AES generator, not this provider's.
        val key = KeyGenerator.getInstance("AES", "SunJCE").apply { init(256) }.generateKey()
        alias?.let { FakeAndroidKeyStore.keys[it] = key }
        return key
    }
}
