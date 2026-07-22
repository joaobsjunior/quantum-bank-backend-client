package com.quantumbank.backendclient.integration

import tools.jackson.databind.json.JsonMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito
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
import java.math.BigDecimal

class GatewayClientTest {

    private val baseUrl = "https://gateway-banking:8443"

    private class Fixture(
        val server: MockRestServiceServer,
        val client: GatewayClient,
    )

    private fun fixture(): Fixture {
        val builder = RestClient.builder().baseUrl(baseUrl)
        val server = MockRestServiceServer.bindTo(builder).build()
        val correlationIdGenerator = Mockito.mock(CorrelationIdGenerator::class.java)
        Mockito.`when`(correlationIdGenerator.newCorrelationId()).thenReturn("corr-123")
        val client = GatewayClient(
            builder.build(),
            TokenProvider { "test-token" },
            correlationIdGenerator,
            JsonMapper.builder().build(),
        )
        return Fixture(server, client)
    }

    @Test
    fun `pix transfer propagates token and correlation id and returns the body`() {
        val f = fixture()
        f.server.expect(requestTo("$baseUrl/pix/transfers"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer test-token"))
            .andExpect(header(GatewayClient.CORRELATION_HEADER, "corr-123"))
            .andRespond(withSuccess("""{"status":"ACCEPTED"}""", MediaType.APPLICATION_JSON))

        val result = f.client.pixTransfer(
            PixTransferRequest(BigDecimal("10.00"), "alice@quantumbank.local", "hi", PixScenario.SUCCESS),
        )

        assertThat(result.correlationId).isEqualTo("corr-123")
        assertThat(result.status).isEqualTo(200)
        assertThat(result.body).contains("ACCEPTED")
        f.server.verify()
    }

    @Test
    fun `statement returns the gateway body`() {
        val f = fixture()
        f.server.expect(requestTo("$baseUrl/statements"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("""{"entries":[]}""", MediaType.APPLICATION_JSON))

        val result = f.client.statement()

        assertThat(result.status).isEqualTo(200)
        assertThat(result.body).contains("entries")
    }

    @Test
    fun `profile returns the gateway body`() {
        val f = fixture()
        f.server.expect(requestTo("$baseUrl/profile"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("""{"name":"Alice"}""", MediaType.APPLICATION_JSON))

        val result = f.client.profile()

        assertThat(result.body).contains("Alice")
    }

    @Test
    fun `maps a problem-details error into a typed exception`() {
        val f = fixture()
        f.server.expect(requestTo("$baseUrl/pix/transfers"))
            .andRespond(
                withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body("""{"type":"about:blank","title":"Pix failed","status":422,"detail":"insufficient funds","instance":"/pix/transfers"}""")
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON),
            )

        assertThatThrownBy {
            f.client.pixTransfer(
                PixTransferRequest(BigDecimal("1.00"), "bob", null, PixScenario.ERROR),
            )
        }.isInstanceOf(ProblemDetailsException::class.java)
            .satisfies({
                val ex = it as ProblemDetailsException
                assertThat(ex.correlationId).isEqualTo("corr-123")
                assertThat(ex.problem.title).isEqualTo("Pix failed")
                assertThat(ex.problem.status).isEqualTo(422)
                assertThat(ex.problem.detail).isEqualTo("insufficient funds")
                assertThat(ex.message).isEqualTo("insufficient funds")
            })
    }

    @Test
    fun `falls back when the error body is not problem-details json`() {
        val f = fixture()
        f.server.expect(requestTo("$baseUrl/statements"))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("boom"))

        assertThatThrownBy { f.client.statement() }
            .isInstanceOf(ProblemDetailsException::class.java)
            .satisfies({
                val ex = it as ProblemDetailsException
                assertThat(ex.problem.status).isEqualTo(500)
                assertThat(ex.problem.title).isEqualTo("unparseable error")
                assertThat(ex.problem.detail).isEqualTo("boom")
            })
    }
}
