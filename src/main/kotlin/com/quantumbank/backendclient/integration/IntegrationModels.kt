package com.quantumbank.backendclient.integration

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.math.BigDecimal

/** Pix simulation scenario selected by the operator (never a real Pix rail). */
enum class PixScenario {
    SUCCESS,
    ERROR,
}

/** Inputs for a Pix transfer submitted through the gateway. */
data class PixTransferRequest(
    val amount: BigDecimal,
    val recipientKey: String,
    val description: String?,
    val scenario: PixScenario,
)

/**
 * Result of a successful gateway call: the correlation id used, the HTTP status,
 * and the raw response body so the console can display the exact gateway reply.
 */
data class FlowResult(
    val correlationId: String,
    val status: Int,
    val body: String,
)

/** RFC 9457 problem-details payload parsed from a gateway/backend error. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class ProblemDetail(
    val type: String? = null,
    val title: String? = null,
    val status: Int? = null,
    val detail: String? = null,
    val instance: String? = null,
)

/** Base type for every external-integration failure. */
open class ExternalIntegrationException(message: String) : RuntimeException(message)

/**
 * Raised when required credential material (token or certificate) is missing or
 * invalid. The service is fail-closed: it never calls without credentials and
 * never falls back to a permissive mode.
 */
class MissingCredentialException(message: String) : ExternalIntegrationException(message)

/** Raised when the gateway/backend returns an RFC 9457 problem-details error. */
class ProblemDetailsException(
    val correlationId: String,
    val problem: ProblemDetail,
) : ExternalIntegrationException(problem.detail ?: problem.title ?: "problem-details error")
