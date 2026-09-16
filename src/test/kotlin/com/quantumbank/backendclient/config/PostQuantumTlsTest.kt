package com.quantumbank.backendclient.config

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.security.KeyStore
import java.security.Security
import java.security.Signature
import javax.net.ssl.SSLContext

class PostQuantumTlsTest {

    @Test
    fun `installs BCJSSE first, BC for the primitives, and the post-quantum policy idempotently`() {
        PostQuantumTls.install()
        PostQuantumTls.install()

        assertThat(Security.getProviders().first().name).isEqualTo(PostQuantumTls.JSSE_PROVIDER)
        assertThat(Security.getProviders().count { it.name == PostQuantumTls.JSSE_PROVIDER }).isEqualTo(1)
        assertThat(Security.getProviders().count { it.name == "BC" }).isEqualTo(1)
        assertThat(SSLContext.getDefault().provider.name).isEqualTo(PostQuantumTls.JSSE_PROVIDER)
        assertThat(KeyStore.getInstance("PKCS12").provider.name).isEqualTo("SUN")
        assertThat(Signature.getInstance("ML-DSA-65").provider.name).isEqualTo("BC")
        assertThat(System.getProperty("jdk.tls.client.SignatureSchemes")).isEqualTo(PostQuantumTls.SIGNATURE_SCHEMES)
        assertThat(System.getProperty("jdk.tls.namedGroups")).isEqualTo(PostQuantumTls.NAMED_GROUPS)
        assertThat(System.getProperty("jdk.tls.client.protocols")).isEqualTo(PostQuantumTls.PROTOCOLS)
        PostQuantumTls.verifyInstalled()
        assertThat(PostQuantumTlsConfiguration()).isNotNull()
    }

    @Test
    fun failsClosedWhenTheDefaultJsseProviderIsNotPostQuantum() {
        PostQuantumTls.install()
        val bcjsse = Security.getProvider(PostQuantumTls.JSSE_PROVIDER)
        try {
            Security.removeProvider(PostQuantumTls.JSSE_PROVIDER)
            assertThatThrownBy { PostQuantumTls.verifyInstalled() }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("post-quantum TLS provider is not active")
        } finally {
            Security.insertProviderAt(bcjsse, 1)
        }
        PostQuantumTls.install()
        PostQuantumTls.verifyInstalled()
    }
}
