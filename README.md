# Quantum Bank — backend-client

`backend-client` simulates an **external company service** that consumes Quantum
Bank's internal banking capabilities the way a real partner integration would.
It is a Spring Boot Kotlin service that:

- reaches Quantum Bank **only through the KrakenD API gateway** (never a backend
  directly);
- authenticates as its own OAuth2 **client-credentials** client
  (`quantum-bank-backend-client`);
- presents a **PKI-issued service certificate** for mutual TLS on the banking
  listener;
- exercises the Pix (success/error simulation), account statement, and profile
  flows, propagating a `correlationId` and parsing RFC 9457 problem-details
  errors;
- ships a **web console** so a person can run every flow by hand and observe the
  request, gateway response, correlation id, and error body.

The service is **fail-closed**: without a valid token or certificate it does not
call, and it never falls back to a permissive TLS mode.

## Everything runs in Docker

No Java or Gradle toolchain is required on the host.

Build, test, and enforce the 100% coverage gate:

```sh
docker build --target build -t quantum-bank-backend-client:local .
```

Build the runtime image:

```sh
docker build -t quantum-bank-backend-client:local .
```

Run the whole stack (from the superproject) via the infrastructure compose file,
which builds and starts `backend-client` alongside `keycloak`, `backend`,
`api-gateway`, and `pki`:

```sh
cd ../infrastructure
docker compose up --build
```

The console is then available on the host port published by the compose service.

## Configuration

All configuration is supplied by container environment (no hard-coded secrets):

| Env var | Purpose |
| --- | --- |
| `QUANTUM_BANK_CLIENT_GATEWAY_BASE_URL` | Gateway banking-listener origin |
| `QUANTUM_BANK_CLIENT_TOKEN_URI` | OAuth2 token endpoint |
| `QUANTUM_BANK_CLIENT_ID` / `QUANTUM_BANK_CLIENT_SECRET` | Client-credentials client |
| `QUANTUM_BANK_CLIENT_SCOPE` | Requested scopes |
| `QUANTUM_BANK_CLIENT_KEY_STORE` / `..._PASSWORD` | Service certificate keystore |
| `QUANTUM_BANK_CLIENT_TRUST_STORE` / `..._PASSWORD` | CA truststore for gateway mTLS |

## Specs

Behavior is governed by the Spec Kit features in the superproject:
`005-external-service-integration`, `001-backend-client-console`, and
`004-deployment-infrastructure`.

## Post-Quantum Transport

`MutualTlsClientFactory` builds the gateway and token-endpoint clients on
BouncyCastle BCJSSE (installed by `PostQuantumTls` before any socket exists):
TLS 1.3 only, `mldsa65,mldsa87` signature schemes, `X25519MLKEM768` key
exchange, the PKI-issued ML-DSA-65 service identity from `backend-client.p12`
and the ML-DSA-87 anchors from `backend-client-truststore.p12`. There is no
classical fallback: a missing, invalid or classical (RSA) identity fails closed,
which `MutualTlsClientFactoryTest` proves with a real loopback ML-DSA handshake.
