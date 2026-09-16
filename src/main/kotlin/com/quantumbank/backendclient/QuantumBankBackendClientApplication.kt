package com.quantumbank.backendclient

import com.quantumbank.backendclient.config.PostQuantumTls
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class QuantumBankBackendClientApplication

fun main(args: Array<String>) {
    // Post-quantum TLS must be in place before any socket exists.
    PostQuantumTls.install()
    runApplication<QuantumBankBackendClientApplication>(*args)
}
