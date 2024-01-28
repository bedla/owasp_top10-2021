package cz.bedla.owasptop10

import dasniko.testcontainers.keycloak.KeycloakContainer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class KeycloakConfig {
    @Bean
//    fun keycloakContainer(registry: DynamicPropertyRegistry): KeycloakContainer {
    fun keycloakContainer(): KeycloakContainer {
        val container = KeycloakContainer("quay.io/keycloak/keycloak:23.0")
//        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri") { "${container.authServerUrl}/realms/$realmName" }
        return container
    }

    @Bean
    fun jwtIssuers(container: KeycloakContainer): JwtIssuers {
        return JwtIssuers(
            listOf(
                "${container.authServerUrl}/realms/${ElevationOfPrivilegeControllerTests.realmName}",
                "${container.authServerUrl}/realms/${InsecureDirectObjectReferencesControllerTests.realmName}"
            )
        )
    }
}