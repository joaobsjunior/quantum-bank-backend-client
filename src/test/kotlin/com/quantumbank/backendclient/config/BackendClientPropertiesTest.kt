package com.quantumbank.backendclient.config

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class BackendClientPropertiesTest {

    private fun properties(clientSecret: String = "secret", tokenUri: String = "https://keycloak:8443/token") =
        BackendClientProperties(
            gatewayBaseUrl = "https://gateway-banking:8443",
            tokenUri = tokenUri,
            clientId = "quantum-bank-backend-client",
            clientSecret = clientSecret,
            scope = "pix:write",
            keyStore = "/etc/quantum-bank/runtime/backend-client.p12",
            keyStorePassword = "changeit",
            trustStore = "/etc/quantum-bank/runtime/backend-client-truststore.p12",
            trustStorePassword = "changeit",
        )

    @Test
    fun `accepts a complete https configuration`() {
        assertThat(properties().forbiddenDirectHosts).containsExactly("backend")
    }

    @Test
    fun `refuses to start without a client secret`() {
        assertThatThrownBy { properties(clientSecret = " ") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("client-secret is required")
    }

    @Test
    fun `refuses plaintext token endpoints`() {
        assertThatThrownBy { properties(tokenUri = "http://keycloak:8080/token") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("must use https")
    }
}
