package com.quantumbank.backendclient.config

import com.quantumbank.backendclient.integration.MissingCredentialException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class MutualTlsClientFactoryTest {

    @Test
    fun `builds an mTLS http client from real keystores`(@TempDir dir: Path) {
        val material = TestCertificates.generate(dir)

        val client = MutualTlsClientFactory.createHttpClient(
            material.keyStore.toString(),
            TestCertificates.PASSWORD,
            material.trustStore.toString(),
            TestCertificates.PASSWORD,
        )

        assertThat(client.sslContext().protocol).isEqualTo("TLS")
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
}
