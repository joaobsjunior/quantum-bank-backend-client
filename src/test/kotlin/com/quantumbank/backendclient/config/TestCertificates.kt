package com.quantumbank.backendclient.config

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.ExtendedKeyUsage
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.asn1.x509.KeyPurposeId
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.Date

/**
 * Generates real post-quantum PKCS12 keystores/truststores for mTLS factory
 * tests: an ML-DSA-87 test CA, ML-DSA-65 client and server identities, plus a
 * classical RSA identity signed by the same CA for the negative case.
 */
object TestCertificates {

    const val PASSWORD = "changeit"
    const val ML_DSA_65 = "ML-DSA-65"
    const val ML_DSA_87 = "ML-DSA-87"

    data class Material(val keyStore: Path, val trustStore: Path, val serverKeyStore: Path, val classicalKeyStore: Path)

    init {
        // The same installer the service uses provides the ML-DSA primitives.
        PostQuantumTls.install()
    }

    fun generate(dir: Path): Material {
        val caKeys = keyPair(ML_DSA_87)
        val ca = certificate("CN=backend-client-test-ca", caKeys.public, "CN=backend-client-test-ca", caKeys.private, ca = true)

        val clientKeys = keyPair(ML_DSA_65)
        val client = certificate("CN=backend-client-test", clientKeys.public, "CN=backend-client-test-ca", caKeys.private, ca = false)
        val serverKeys = keyPair(ML_DSA_65)
        val server = certificate("CN=localhost", serverKeys.public, "CN=backend-client-test-ca", caKeys.private, ca = false)
        val rsaKeys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val rsa = certificate("CN=classical-client", rsaKeys.public, "CN=backend-client-test-ca", caKeys.private, ca = false)

        val keyStorePath = keyStore(dir.resolve("client.p12"), "client", clientKeys, listOf(client, ca))
        val serverKeyStorePath = keyStore(dir.resolve("server.p12"), "server", serverKeys, listOf(server, ca))
        val classicalKeyStorePath = keyStore(dir.resolve("classical.p12"), "classical", rsaKeys, listOf(rsa, ca))

        val trustStorePath = dir.resolve("truststore.p12")
        KeyStore.getInstance("PKCS12").apply {
            load(null, null)
            setCertificateEntry("ca", ca)
            Files.newOutputStream(trustStorePath).use { store(it, PASSWORD.toCharArray()) }
        }

        return Material(keyStorePath, trustStorePath, serverKeyStorePath, classicalKeyStorePath)
    }

    private fun keyPair(algorithm: String): KeyPair =
        KeyPairGenerator.getInstance(algorithm, BouncyCastleProvider.PROVIDER_NAME).generateKeyPair()

    private fun keyStore(path: Path, alias: String, keys: KeyPair, chain: List<X509Certificate>): Path {
        KeyStore.getInstance("PKCS12").apply {
            load(null, null)
            setKeyEntry(alias, keys.private, PASSWORD.toCharArray(), chain.toTypedArray())
            Files.newOutputStream(path).use { store(it, PASSWORD.toCharArray()) }
        }
        return path
    }

    private fun certificate(
        subject: String,
        publicKey: java.security.PublicKey,
        issuer: String,
        signingKey: java.security.PrivateKey,
        ca: Boolean,
    ): X509Certificate {
        val now = Date()
        val until = Date(now.time + 86_400_000L)
        val builder = JcaX509v3CertificateBuilder(X500Name(issuer), BigInteger.valueOf(System.nanoTime()), now, until, X500Name(subject), publicKey)
        builder.addExtension(Extension.basicConstraints, true, BasicConstraints(ca))
        if (ca) {
            builder.addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.keyCertSign or KeyUsage.cRLSign))
        } else {
            builder.addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.digitalSignature))
            builder.addExtension(Extension.extendedKeyUsage, false, ExtendedKeyUsage(arrayOf(KeyPurposeId.id_kp_clientAuth, KeyPurposeId.id_kp_serverAuth)))
            builder.addExtension(Extension.subjectAlternativeName, false, GeneralNames(GeneralName(GeneralName.dNSName, "localhost")))
        }
        val signer = JcaContentSignerBuilder(signingKey.algorithm).setProvider(BouncyCastleProvider.PROVIDER_NAME).build(signingKey)
        return JcaX509CertificateConverter().setProvider(BouncyCastleProvider.PROVIDER_NAME).getCertificate(builder.build(signer))
    }
}
