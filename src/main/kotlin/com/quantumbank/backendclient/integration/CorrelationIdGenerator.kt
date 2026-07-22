package com.quantumbank.backendclient.integration

import org.springframework.stereotype.Component
import java.util.UUID

/** Generates a correlation id per outbound request; injectable for tests. */
@Component
class CorrelationIdGenerator {
    fun newCorrelationId(): String = UUID.randomUUID().toString()
}
