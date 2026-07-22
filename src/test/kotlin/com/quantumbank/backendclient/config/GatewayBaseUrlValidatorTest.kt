package com.quantumbank.backendclient.config

import com.quantumbank.backendclient.integration.ExternalIntegrationException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class GatewayBaseUrlValidatorTest {

    private val forbidden = listOf("backend")

    @Test
    fun `accepts a gateway url`() {
        val url = "https://gateway-banking:8443"
        assertThat(GatewayBaseUrlValidator.validate(url, forbidden)).isEqualTo(url)
    }

    @Test
    fun `rejects a url whose host is a backend service`() {
        assertThatThrownBy { GatewayBaseUrlValidator.validate("https://backend:8080", forbidden) }
            .isInstanceOf(ExternalIntegrationException::class.java)
            .hasMessageContaining("backend service")
    }

    @Test
    fun `rejects a url with no host`() {
        assertThatThrownBy { GatewayBaseUrlValidator.validate("not-a-url", forbidden) }
            .isInstanceOf(ExternalIntegrationException::class.java)
            .hasMessageContaining("no host")
    }

    @Test
    fun `rejects a malformed url`() {
        assertThatThrownBy { GatewayBaseUrlValidator.validate("http://[bad", forbidden) }
            .isInstanceOf(ExternalIntegrationException::class.java)
            .hasMessageContaining("invalid gateway base URL")
    }
}
