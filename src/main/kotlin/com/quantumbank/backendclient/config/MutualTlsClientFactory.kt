package com.quantumbank.backendclient.config

import com.quantumbank.backendclient.integration.MissingCredentialException
import java.net.http.HttpClient
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

/**
 * Builds an mTLS-capable [HttpClient] from the PKI-issued service keystore and
 * the truststore of CA anchors. Fail-closed: any missing or unreadable material
 * raises [MissingCredentialException] so the service never talks to the gateway
 * without a client certificate and never uses a permissive TLS mode.
 */
object MutualTlsClientFactory {

    fun createHttpClient(
        keyStorePath: String,
        keyStorePassword: String,
        trustStorePath: String,
        trustStorePassword: String,
    ): HttpClient {
        val keyManagers = loadKeyStore(keyStorePath, keyStorePassword).let { store ->
            KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
                init(store, keyStorePassword.toCharArray())
            }.keyManagers
        }
        val trustManagers = loadKeyStore(trustStorePath, trustStorePassword).let { store ->
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
                init(store)
            }.trustManagers
        }
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(keyManagers, trustManagers, null)
        }
        return HttpClient.newBuilder().sslContext(sslContext).build()
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
