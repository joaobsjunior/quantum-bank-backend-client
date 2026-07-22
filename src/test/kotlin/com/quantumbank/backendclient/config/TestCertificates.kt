package com.quantumbank.backendclient.config

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.util.Date

/** Generates real PKCS12 keystores/truststores for mTLS factory tests. */
object TestCertificates {

    const val PASSWORD = "changeit"

    data class Material(val keyStore: Path, val trustStore: Path)

    fun generate(dir: Path): Material {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val name = X500Name("CN=backend-client-test")
        val now = Date()
        val until = Date(now.time + 86_400_000L)
        val builder = JcaX509v3CertificateBuilder(name, BigInteger.ONE, now, until, name, keyPair.public)
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)
        val cert = JcaX509CertificateConverter().getCertificate(builder.build(signer))

        val keyStorePath = dir.resolve("client.p12")
        KeyStore.getInstance("PKCS12").apply {
            load(null, null)
            setKeyEntry("client", keyPair.private, PASSWORD.toCharArray(), arrayOf(cert))
            Files.newOutputStream(keyStorePath).use { store(it, PASSWORD.toCharArray()) }
        }

        val trustStorePath = dir.resolve("truststore.p12")
        KeyStore.getInstance("PKCS12").apply {
            load(null, null)
            setCertificateEntry("ca", cert)
            Files.newOutputStream(trustStorePath).use { store(it, PASSWORD.toCharArray()) }
        }

        return Material(keyStorePath, trustStorePath)
    }
}
