package com.quantumbank.backendclient.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class IntegrationModelsTest {

    @Test
    fun `problem-details exception message prefers detail`() {
        val ex = ProblemDetailsException("c", ProblemDetail(title = "t", detail = "d"))
        assertThat(ex.message).isEqualTo("d")
    }

    @Test
    fun `problem-details exception message falls back to title`() {
        val ex = ProblemDetailsException("c", ProblemDetail(title = "t"))
        assertThat(ex.message).isEqualTo("t")
    }

    @Test
    fun `problem-details exception message falls back to a default`() {
        val ex = ProblemDetailsException("c", ProblemDetail())
        assertThat(ex.message).isEqualTo("problem-details error")
    }

    @Test
    fun `missing credential is an external integration exception`() {
        val ex = MissingCredentialException("nope")
        assertThat(ex).isInstanceOf(ExternalIntegrationException::class.java)
        assertThat(ex.message).isEqualTo("nope")
    }

    @Test
    fun `pix scenario has both simulation options`() {
        assertThat(PixScenario.entries).containsExactly(PixScenario.SUCCESS, PixScenario.ERROR)
        assertThat(PixScenario.valueOf("ERROR")).isEqualTo(PixScenario.ERROR)
    }

    @Test
    fun `data classes expose value semantics`() {
        val request = PixTransferRequest(BigDecimal.ONE, "key", "desc", PixScenario.SUCCESS)
        assertThat(request.copy(recipientKey = "key")).isEqualTo(request)
        assertThat(request.hashCode()).isEqualTo(request.copy().hashCode())
        assertThat(request.toString()).contains("key")

        val result = FlowResult("c", 200, "{}")
        assertThat(result.copy()).isEqualTo(result)
        assertThat(result.toString()).contains("200")

        val problem = ProblemDetail(type = "t", title = "ti", status = 1, detail = "de", instance = "in")
        assertThat(problem.copy()).isEqualTo(problem)
        assertThat(problem.toString()).contains("ti")

        val token = ClientCredentialsToken("abc")
        assertThat(token.copy()).isEqualTo(token)
        assertThat(token.toString()).contains("abc")
    }
}
