# ADR 0001 — Java 21 and Spring Boot 4

## Status
Accepted (2026-11-05)

## Context
The target role asks for 5+ years of Java and Spring in production. The project
must run on the local Windows machine (JDK 23 installed) and in Linux containers.

## Decision
- Target **Java 21** (LTS), compiled with `--release 21`. The local JDK 23 can
  build it without behavioral drift.
- Use **Spring Boot 4.1.2** and its modular starters (`spring-boot-starter-webmvc`,
  `spring-boot-health` contributor API).
- Use the **Maven Wrapper** so no global Maven installation is required.

## Consequences
- The code uses the Spring Boot 4 health API
  (`org.springframework.boot.health.contributor`), not the Boot 3
  `org.springframework.boot.actuate.health` package.
- Builds are reproducible across the local machine, CI and Docker.
