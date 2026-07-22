package com.quantumbank.backendclient.integration

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.hamcrest.Matchers.startsWith
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

class ClientCredentialsTokenProviderTest {

    private val tokenUri = "http://keycloak:8080/token"

    private fun provider(configure: (MockRestServiceServer) -> Unit): ClientCredentialsTokenProvider {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        configure(server)
        return ClientCredentialsTokenProvider(
            builder.build(), tokenUri, "quantum-bank-backend-client", "secret", "pix:write",
        )
    }

    @Test
    fun `returns the access token on success`() {
        val provider = provider { server ->
            server.expect(requestTo(tokenUri))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", startsWith("Basic ")))
                .andRespond(withSuccess("""{"access_token":"abc.def"}""", MediaType.APPLICATION_JSON))
        }

        assertThat(provider.accessToken()).isEqualTo("abc.def")
    }

    @Test
    fun `fails closed when token endpoint returns no token`() {
        val provider = provider { server ->
            server.expect(requestTo(tokenUri))
                .andRespond(withSuccess("""{"token_type":"Bearer"}""", MediaType.APPLICATION_JSON))
        }

        assertThatThrownBy { provider.accessToken() }
            .isInstanceOf(MissingCredentialException::class.java)
            .hasMessageContaining("no access_token")
    }

    @Test
    fun `fails closed when token endpoint errors`() {
        val provider = provider { server ->
            server.expect(requestTo(tokenUri))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED))
        }

        assertThatThrownBy { provider.accessToken() }
            .isInstanceOf(MissingCredentialException::class.java)
            .hasMessageContaining("failed to obtain client-credentials token")
    }
}
