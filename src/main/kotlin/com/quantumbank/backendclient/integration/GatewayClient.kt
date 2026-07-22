package com.quantumbank.backendclient.integration

import tools.jackson.databind.ObjectMapper
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient

/**
 * Calls Quantum Bank banking capabilities exclusively through the gateway.
 *
 * Every call is fail-closed on credentials (a token is required and mTLS is
 * enforced by the underlying request factory), carries a `correlationId`, and
 * maps RFC 9457 problem-details errors into [ProblemDetailsException].
 */
class GatewayClient(
    private val gatewayRestClient: RestClient,
    private val tokenProvider: TokenProvider,
    private val correlationIdGenerator: CorrelationIdGenerator,
    private val objectMapper: ObjectMapper,
) {

    companion object {
        const val CORRELATION_HEADER = "X-Correlation-Id"
    }

    fun pixTransfer(request: PixTransferRequest): FlowResult {
        val token = tokenProvider.accessToken()
        val correlationId = correlationIdGenerator.newCorrelationId()
        val response = gatewayRestClient.post()
            .uri("/pix/transfers")
            .headers { applyHeaders(it, token, correlationId) }
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .onStatus({ it.is4xxClientError || it.is5xxServerError }) { _, res -> throw problem(correlationId, res.body.readBytes(), res.statusCode.value()) }
            .toEntity(String::class.java)
        return FlowResult(correlationId, response.statusCode.value(), response.body ?: "")
    }

    fun statement(): FlowResult = get("/statements")

    fun profile(): FlowResult = get("/profile")

    private fun get(path: String): FlowResult {
        val token = tokenProvider.accessToken()
        val correlationId = correlationIdGenerator.newCorrelationId()
        val response = gatewayRestClient.get()
            .uri(path)
            .headers { applyHeaders(it, token, correlationId) }
            .retrieve()
            .onStatus({ it.is4xxClientError || it.is5xxServerError }) { _, res -> throw problem(correlationId, res.body.readBytes(), res.statusCode.value()) }
            .toEntity(String::class.java)
        return FlowResult(correlationId, response.statusCode.value(), response.body ?: "")
    }

    private fun applyHeaders(headers: HttpHeaders, token: String, correlationId: String) {
        headers.setBearerAuth(token)
        headers.set(CORRELATION_HEADER, correlationId)
    }

    private fun problem(correlationId: String, body: ByteArray, status: Int): ProblemDetailsException {
        val problem = try {
            objectMapper.readValue(body, ProblemDetail::class.java)
        } catch (_: Exception) {
            ProblemDetail(status = status, title = "unparseable error", detail = String(body))
        }
        return ProblemDetailsException(correlationId, problem)
    }
}
