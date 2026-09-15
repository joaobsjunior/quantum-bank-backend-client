package com.quantumbank.backendclient.config

import tools.jackson.databind.json.JsonMapper
import com.quantumbank.backendclient.integration.ClientCredentialsTokenProvider
import com.quantumbank.backendclient.integration.CorrelationIdGenerator
import com.quantumbank.backendclient.integration.GatewayClient
import com.quantumbank.backendclient.integration.TokenProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.web.client.RestClient
import java.nio.file.Path

class GatewayClientConfigTest {

    private val config = GatewayClientConfig()

    private fun properties(dir: Path): BackendClientProperties {
        val material = TestCertificates.generate(dir)
        return BackendClientProperties(
            gatewayBaseUrl = "https://gateway-banking:8443",
            tokenUri = "https://keycloak:8443/token",
            clientId = "quantum-bank-backend-client",
            clientSecret = "secret",
            scope = "pix:write",
            keyStore = material.keyStore.toString(),
            keyStorePassword = TestCertificates.PASSWORD,
            trustStore = material.trustStore.toString(),
            trustStorePassword = TestCertificates.PASSWORD,
        )
    }

    @Test
    fun `builds the mTLS gateway rest client`(@TempDir dir: Path) {
        assertThat(config.gatewayRestClient(properties(dir))).isNotNull()
    }

    @Test
    fun `builds a client-credentials token provider`(@TempDir dir: Path) {
        assertThat(config.tokenProvider(properties(dir)))
            .isInstanceOf(ClientCredentialsTokenProvider::class.java)
    }

    @Test
    fun `builds the gateway client`() {
        val gatewayClient = config.gatewayClient(
            RestClient.builder().build(),
            TokenProvider { "token" },
            CorrelationIdGenerator(),
            JsonMapper.builder().build(),
        )
        assertThat(gatewayClient).isInstanceOf(GatewayClient::class.java)
    }
}
