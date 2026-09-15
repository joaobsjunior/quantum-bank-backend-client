package com.quantumbank.backendclient.console

import com.quantumbank.backendclient.integration.FlowResult
import com.quantumbank.backendclient.integration.GatewayClient
import com.quantumbank.backendclient.integration.MissingCredentialException
import com.quantumbank.backendclient.integration.ProblemDetail
import com.quantumbank.backendclient.integration.ProblemDetailsException
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.model
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.view
import com.quantumbank.backendclient.config.ConsoleSecurityConfig

@WebMvcTest(ConsoleController::class)
@Import(ConsoleSecurityConfig::class)
@TestPropertySource(
    properties = [
        "quantum-bank.console.username=operator",
        "quantum-bank.console.password=operator-password-1",
        "quantum-bank.client.client-secret=test-secret",
        "quantum-bank.client.token-uri=https://keycloak:8443/token",
    ],
)
class ConsoleControllerTest {

    private val operator = user("operator").roles("OPERATOR")

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var gatewayClient: GatewayClient

    // Kotlin-friendly Mockito any(): the generic `null as T` is unchecked and
    // safe, unlike `ArgumentMatchers.any(Class)` which NPEs on non-null params.
    private fun <T> anyArg(): T {
        Mockito.any<T>()
        @Suppress("UNCHECKED_CAST")
        return null as T
    }

    @Test
    fun `home lists the flows`() {
        mockMvc.perform(get("/").with(operator))
            .andExpect(status().isOk)
            .andExpect(view().name("home"))
            .andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.containsString("frame-ancestors 'none'")))
    }

    @Test
    fun `anonymous operators are redirected to the login page`() {
        mockMvc.perform(get("/").with(anonymous()))
            .andExpect(status().is3xxRedirection)
            .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/login")))

        mockMvc.perform(post("/statement").with(anonymous()).with(csrf()))
            .andExpect(status().is3xxRedirection)
    }

    @Test
    fun `state-changing posts without a csrf token are rejected`() {
        mockMvc.perform(post("/statement").with(operator))
            .andExpect(status().isForbidden)

        mockMvc.perform(post("/pix").with(operator).param("amount", "10.00").param("recipientKey", "x").param("scenario", "SUCCESS"))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `pix form is served`() {
        mockMvc.perform(get("/pix").with(operator))
            .andExpect(status().isOk)
            .andExpect(view().name("pix"))
    }

    @Test
    fun `submitting pix shows the result`() {
        given(gatewayClient.pixTransfer(anyArg()))
            .willReturn(FlowResult("corr-1", 200, """{"status":"ACCEPTED"}"""))

        mockMvc.perform(
            post("/pix")
                .with(operator)
                .with(csrf())
                .param("amount", "10.00")
                .param("recipientKey", "alice@quantumbank.local")
                .param("description", "console")
                .param("scenario", "SUCCESS"),
        )
            .andExpect(status().isOk)
            .andExpect(view().name("result"))
            .andExpect(model().attribute("flow", "Pix transfer"))
            .andExpect(model().attribute("correlationId", "corr-1"))
            .andExpect(model().attribute("status", 200))
    }

    @Test
    fun `statement problem-details error renders the error view`() {
        given(gatewayClient.statement())
            .willThrow(ProblemDetailsException("corr-2", ProblemDetail(title = "failed", status = 400, detail = "bad")))

        mockMvc.perform(post("/statement").with(operator).with(csrf()))
            .andExpect(status().isOk)
            .andExpect(view().name("error"))
            .andExpect(model().attribute("flow", "Account statement"))
            .andExpect(model().attribute("correlationId", "corr-2"))
            .andExpect(model().attributeExists("problem"))
    }

    @Test
    fun `missing credential error renders the error view`() {
        given(gatewayClient.profile()).willThrow(MissingCredentialException("no token"))

        mockMvc.perform(post("/profile").with(operator).with(csrf()))
            .andExpect(status().isOk)
            .andExpect(view().name("error"))
            .andExpect(model().attribute("flow", "Profile"))
            .andExpect(model().attribute("message", "no token"))
    }
}
