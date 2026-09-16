package com.quantumbank.backendclient.config

import com.quantumbank.backendclient.integration.MissingCredentialException
import java.net.http.HttpClient
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.time.Duration
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

/**
 * Builds a post-quantum mTLS [HttpClient] from the PKI-issued ML-DSA-65
 * service keystore and the truststore of ML-DSA-87 CA anchors. The context is
 * requested explicitly from BCJSSE (TLS 1.3, `mldsa65`/`mldsa87` signature
 * schemes, `X25519MLKEM768` group) so a classical JSSE can never be selected
 * by provider order. Fail-closed: any missing or unreadable material raises
 * [MissingCredentialException] so the service never talks to the gateway
 * without a client certificate and never uses a permissive TLS mode.
 */
object MutualTlsClientFactory {

    fun createHttpClient(
        keyStorePath: String,
        keyStorePassword: String,
        trustStorePath: String,
        trustStorePassword: String,
    ): HttpClient {
        PostQuantumTls.install()
        val keyManagers = loadKeyStore(keyStorePath, keyStorePassword).let { store ->
            KeyManagerFactory.getInstance("PKIX", PostQuantumTls.JSSE_PROVIDER).apply {
                init(store, keyStorePassword.toCharArray())
            }.keyManagers
        }
        val trustManagers = loadKeyStore(trustStorePath, trustStorePassword).let { store ->
            TrustManagerFactory.getInstance("PKIX", PostQuantumTls.JSSE_PROVIDER).apply {
                init(store)
            }.trustManagers
        }
        val sslContext = SSLContext.getInstance("TLSv1.3", PostQuantumTls.JSSE_PROVIDER).apply {
            init(keyManagers, trustManagers, null)
        }
        val parameters = sslContext.defaultSSLParameters.apply {
            protocols = arrayOf(PostQuantumTls.PROTOCOLS)
        }
        return HttpClient.newBuilder()
            .sslContext(sslContext)
            .sslParameters(parameters)
            .connectTimeout(Duration.ofSeconds(10))
            .build()
    }

    private fun loadKeyStore(path: String, password: String): KeyStore {
        val file = Path.of(path)
        if (!Files.isReadable(file)) {
            throw MissingCredentialException("keystore/truststore not readable at '$path'")
        }
        return try {
            Files.newInputStream(file).use { input ->
                KeyStore.getInstance("PKCS12").apply { load(input, password.toCharArray()) }
            }
        } catch (ex: Exception) {
            throw MissingCredentialException("failed to load keystore/truststore at '$path': ${ex.message}")
        }
    }
}
