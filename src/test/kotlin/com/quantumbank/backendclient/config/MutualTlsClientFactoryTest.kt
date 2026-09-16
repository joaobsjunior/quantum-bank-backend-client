package com.quantumbank.backendclient.config

import com.quantumbank.backendclient.integration.MissingCredentialException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory

class MutualTlsClientFactoryTest {

    @Test
    fun `builds a post-quantum mTLS http client from real keystores`(@TempDir dir: Path) {
        val material = TestCertificates.generate(dir)

        val client = MutualTlsClientFactory.createHttpClient(
            material.keyStore.toString(),
            TestCertificates.PASSWORD,
            material.trustStore.toString(),
            TestCertificates.PASSWORD,
        )

        assertThat(client.sslContext().protocol).isEqualTo("TLSv1.3")
        assertThat(client.sslContext().provider.name).isEqualTo(PostQuantumTls.JSSE_PROVIDER)
        assertThat(client.sslParameters().protocols).containsExactly("TLSv1.3")
    }

    @Test
    fun `completes an ML-DSA mutual handshake over the hybrid group and refuses a classical identity`(@TempDir dir: Path) {
        val material = TestCertificates.generate(dir)

        LoopbackTlsServer(serverContext(material)).use { server ->
            val request = HttpRequest.newBuilder(URI.create("https://localhost:${server.port}/")).GET().build()

            val response = MutualTlsClientFactory.createHttpClient(
                material.keyStore.toString(),
                TestCertificates.PASSWORD,
                material.trustStore.toString(),
                TestCertificates.PASSWORD,
            ).send(request, HttpResponse.BodyHandlers.ofString())

            assertThat(response.statusCode()).isEqualTo(200)
            assertThat(response.body()).isEqualTo("pqc-ok")
            assertThat(response.sslSession().orElseThrow().protocol).isEqualTo("TLSv1.3")
            assertThat(response.sslSession().orElseThrow().peerCertificates.first().publicKey.algorithm).isEqualTo(TestCertificates.ML_DSA_65)
            assertThat(server.lastPeerKeyAlgorithm).isEqualTo(TestCertificates.ML_DSA_65)

            // A certificate the CA signed for an RSA key cannot produce an accepted
            // CertificateVerify: the server aborts inside the handshake (TLS alert or
            // closed connection before any HTTP byte) and never records the peer.
            server.lastPeerKeyAlgorithm = null
            assertThatThrownBy {
                MutualTlsClientFactory.createHttpClient(
                    material.classicalKeyStore.toString(),
                    TestCertificates.PASSWORD,
                    material.trustStore.toString(),
                    TestCertificates.PASSWORD,
                ).send(request, HttpResponse.BodyHandlers.ofString())
            }.isInstanceOf(IOException::class.java)
            assertThat(server.lastPeerKeyAlgorithm).isNull()
            assertThat(server.rejectedHandshakes).isGreaterThanOrEqualTo(1)
        }
    }

    @Test
    fun `fails closed when keystore is missing`(@TempDir dir: Path) {
        val material = TestCertificates.generate(dir)

        assertThatThrownBy {
            MutualTlsClientFactory.createHttpClient(
                dir.resolve("absent.p12").toString(),
                TestCertificates.PASSWORD,
                material.trustStore.toString(),
                TestCertificates.PASSWORD,
            )
        }.isInstanceOf(MissingCredentialException::class.java)
            .hasMessageContaining("not readable")
    }

    @Test
    fun `fails closed when keystore is corrupt`(@TempDir dir: Path) {
        val material = TestCertificates.generate(dir)
        val corrupt = dir.resolve("corrupt.p12")
        Files.write(corrupt, byteArrayOf(1, 2, 3, 4))

        assertThatThrownBy {
            MutualTlsClientFactory.createHttpClient(
                corrupt.toString(),
                TestCertificates.PASSWORD,
                material.trustStore.toString(),
                TestCertificates.PASSWORD,
            )
        }.isInstanceOf(MissingCredentialException::class.java)
            .hasMessageContaining("failed to load")
    }

    private fun serverContext(material: TestCertificates.Material): SSLContext {
        PostQuantumTls.install()
        val keyStore = KeyStore.getInstance("PKCS12").apply {
            Files.newInputStream(material.serverKeyStore).use { load(it, TestCertificates.PASSWORD.toCharArray()) }
        }
        val trustStore = KeyStore.getInstance("PKCS12").apply {
            Files.newInputStream(material.trustStore).use { load(it, TestCertificates.PASSWORD.toCharArray()) }
        }
        val kmf = KeyManagerFactory.getInstance("PKIX", PostQuantumTls.JSSE_PROVIDER).apply { init(keyStore, TestCertificates.PASSWORD.toCharArray()) }
        val tmf = TrustManagerFactory.getInstance("PKIX", PostQuantumTls.JSSE_PROVIDER).apply { init(trustStore) }
        return SSLContext.getInstance("TLSv1.3", PostQuantumTls.JSSE_PROVIDER).apply { init(kmf.keyManagers, tmf.trustManagers, null) }
    }

    /** Minimal HTTP/1.1 responder over BCJSSE requiring a client certificate. */
    private class LoopbackTlsServer(context: SSLContext) : AutoCloseable {
        private val socket = (context.serverSocketFactory.createServerSocket(0) as SSLServerSocket).apply {
            needClientAuth = true
            enabledProtocols = arrayOf("TLSv1.3")
        }
        private val executor = Executors.newSingleThreadExecutor()
        val port: Int = socket.localPort

        @Volatile
        var lastPeerKeyAlgorithm: String? = null

        @Volatile
        var rejectedHandshakes: Int = 0

        init {
            executor.submit {
                while (!socket.isClosed) {
                    try {
                        socket.accept().use { accepted ->
                            val tls = accepted as SSLSocket
                            tls.startHandshake()
                            lastPeerKeyAlgorithm = tls.session.peerCertificates.first().publicKey.algorithm
                            val reader = tls.inputStream.bufferedReader()
                            var line = reader.readLine()
                            while (!line.isNullOrEmpty()) {
                                line = reader.readLine()
                            }
                            tls.outputStream.write("HTTP/1.1 200 OK\r\nContent-Length: 6\r\nConnection: close\r\n\r\npqc-ok".toByteArray())
                            tls.outputStream.flush()
                        }
                    } catch (_: Exception) {
                        // handshake failures are the expected outcome of the negative case
                        if (!socket.isClosed) {
                            rejectedHandshakes += 1
                        }
                    }
                }
            }
        }

        override fun close() {
            socket.close()
            executor.shutdownNow()
            executor.awaitTermination(5, TimeUnit.SECONDS)
        }
    }
}
