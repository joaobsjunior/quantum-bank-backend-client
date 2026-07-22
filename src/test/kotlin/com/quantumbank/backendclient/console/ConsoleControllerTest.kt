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
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.model
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.view

@WebMvcTest(ConsoleController::class)
class ConsoleControllerTest {

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
        mockMvc.perform(get("/"))
            .andExpect(status().isOk)
            .andExpect(view().name("home"))
    }

    @Test
    fun `pix form is served`() {
        mockMvc.perform(get("/pix"))
            .andExpect(status().isOk)
            .andExpect(view().name("pix"))
    }

    @Test
    fun `submitting pix shows the result`() {
        given(gatewayClient.pixTransfer(anyArg()))
            .willReturn(FlowResult("corr-1", 200, """{"status":"ACCEPTED"}"""))

        mockMvc.perform(
            post("/pix")
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

        mockMvc.perform(post("/statement"))
            .andExpect(status().isOk)
            .andExpect(view().name("error"))
            .andExpect(model().attribute("flow", "Account statement"))
            .andExpect(model().attribute("correlationId", "corr-2"))
            .andExpect(model().attributeExists("problem"))
    }

    @Test
    fun `missing credential error renders the error view`() {
        given(gatewayClient.profile()).willThrow(MissingCredentialException("no token"))

        mockMvc.perform(post("/profile"))
            .andExpect(status().isOk)
            .andExpect(view().name("error"))
            .andExpect(model().attribute("flow", "Profile"))
            .andExpect(model().attribute("message", "no token"))
    }
}
