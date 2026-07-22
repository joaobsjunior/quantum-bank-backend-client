package com.quantumbank.backendclient.console

import com.quantumbank.backendclient.integration.ExternalIntegrationException
import com.quantumbank.backendclient.integration.FlowResult
import com.quantumbank.backendclient.integration.GatewayClient
import com.quantumbank.backendclient.integration.PixScenario
import com.quantumbank.backendclient.integration.PixTransferRequest
import com.quantumbank.backendclient.integration.ProblemDetailsException
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import java.math.BigDecimal

/**
 * Human-facing console. Every action routes through [GatewayClient] — the
 * gateway-fronted, OAuth2 + mTLS path — and there is no control that reaches a
 * backend directly. Each run surfaces the request, response, correlation id, and
 * any problem-details error.
 */
@Controller
class ConsoleController(
    private val gatewayClient: GatewayClient,
) {

    @GetMapping("/")
    fun home(): String = "home"

    @GetMapping("/pix")
    fun pixForm(): String = "pix"

    @PostMapping("/pix")
    fun submitPix(
        @RequestParam amount: BigDecimal,
        @RequestParam recipientKey: String,
        @RequestParam(required = false) description: String?,
        @RequestParam scenario: PixScenario,
        model: Model,
    ): String = runFlow("Pix transfer", model) {
        gatewayClient.pixTransfer(PixTransferRequest(amount, recipientKey, description, scenario))
    }

    @PostMapping("/statement")
    fun submitStatement(model: Model): String =
        runFlow("Account statement", model) { gatewayClient.statement() }

    @PostMapping("/profile")
    fun submitProfile(model: Model): String =
        runFlow("Profile", model) { gatewayClient.profile() }

    private fun runFlow(flow: String, model: Model, action: () -> FlowResult): String {
        model.addAttribute("flow", flow)
        return try {
            val result = action()
            model.addAttribute("correlationId", result.correlationId)
            model.addAttribute("status", result.status)
            model.addAttribute("body", result.body)
            "result"
        } catch (ex: ProblemDetailsException) {
            model.addAttribute("correlationId", ex.correlationId)
            model.addAttribute("problem", ex.problem)
            model.addAttribute("message", ex.message)
            "error"
        } catch (ex: ExternalIntegrationException) {
            model.addAttribute("message", ex.message)
            "error"
        }
    }
}
