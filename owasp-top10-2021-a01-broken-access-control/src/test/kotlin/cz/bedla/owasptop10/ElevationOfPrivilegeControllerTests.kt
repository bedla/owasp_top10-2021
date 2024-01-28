package cz.bedla.owasptop10

import dasniko.testcontainers.keycloak.KeycloakContainer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.keycloak.admin.client.Keycloak
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import java.time.ZonedDateTime
import java.util.stream.Stream

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class ElevationOfPrivilegeControllerTests {
    @Autowired
    private lateinit var keycloakContainer: KeycloakContainer

    @Autowired
    private lateinit var restTemplateBuilder: RestTemplateBuilder

    private lateinit var restTemplate: RestTemplate

    private lateinit var keycloakAdminClient: Keycloak

    @LocalServerPort
    private var port = 0

    @BeforeEach
    fun setUp() {
        keycloakAdminClient = keycloakContainer.keycloakAdminClient
        restTemplate = restTemplateBuilder.build()

        keycloakAdminClient.createRealm(realmName)
    }

    @Test
    fun anonymous() {
        val responseEntityHello = restTemplate.httpGet(
            "http://localhost:{port}/elevation-of-privilege/hello?name={name}",
            ElevationOfPrivilegeController.HelloResponse::class.java,
            uriVariables = mapOf(
                "name" to "monkey",
                "port" to port
            ),
            authServerBaseUrl = keycloakContainer.authServerUrl
        )

        assertThat(responseEntityHello.message).isEqualTo("Hello monkey")
        assertThat(responseEntityHello.timestamp).isAfter(ZonedDateTime.now().minusMinutes(1))
    }

    @ParameterizedTest
    @MethodSource("elevationOfPrivilege_EndpointProvider")
    fun elevationOfPrivilege_Endpoint(
        endpointSuffix: String,
        clientCreator: (Keycloak) -> MyClient,
        expectedDataDeleted: String?,
        expectedErrorStatus: HttpStatus?
    ) {
        val client = clientCreator(keycloakAdminClient)

        val action = {
            restTemplate.httpGet(
                "http://localhost:{port}/elevation-of-privilege$endpointSuffix",
                ElevationOfPrivilegeController.AdminActionResponse::class.java,
                realmName,
                client,
                uriVariables = mapOf(
                    "port" to port
                ),
                authServerBaseUrl = keycloakContainer.authServerUrl
            )
        }

        if (expectedErrorStatus == null) {
            val responseEntityAdmin = action()
            assertThat(responseEntityAdmin.dataDeleted).isEqualTo(expectedDataDeleted)
            assertThat(responseEntityAdmin.timestamp).isAfter(ZonedDateTime.now().minusMinutes(1))
        } else {
            assertThatThrownBy { action() }
                .isInstanceOfSatisfying(HttpClientErrorException::class.java) {
                    assertThat(it.statusCode).isEqualTo(expectedErrorStatus)
                }
        }
    }

    companion object {
        internal val realmName = "elevation-of-privilege-realm"

        @JvmStatic
        fun elevationOfPrivilege_EndpointProvider(): Stream<Arguments> {
            val createClientSuperAdmin = { kc: Keycloak -> kc.createClient(realmName, "my-client-super-admin", "my-super-admin") }
            val createClientAdmin = { kc: Keycloak -> kc.createClient(realmName, "my-client-admin", "my-admin") }
            val createClientUser = { kc: Keycloak -> kc.createClient(realmName, "my-client-user", "my-user") }

            return Stream.of(
                Arguments.of("/admin", createClientSuperAdmin, "ALL_DATA_DELETED", null),
                Arguments.of("/admin", createClientAdmin, "ALL_DATA_DELETED", null),
                Arguments.of("/admin", createClientUser, "ALL_DATA_DELETED", HttpStatus.FORBIDDEN),
                // ---
                Arguments.of("/super-admin", createClientSuperAdmin, "FORMAT C:", null),
                Arguments.of("/super-admin", createClientAdmin, null, HttpStatus.FORBIDDEN),
                Arguments.of("/super-admin", createClientUser, null, HttpStatus.FORBIDDEN),
                // ---
                Arguments.of("/technical", createClientSuperAdmin, "Calculate ULTIMATE QUESTION", null),
                Arguments.of("/technical", createClientAdmin, "Calculate ULTIMATE QUESTION", null),
                Arguments.of("/technical", createClientUser, "Calculate ULTIMATE QUESTION", null),
                // ---
                Arguments.of("/also-admin", createClientSuperAdmin, "USER_DATA_DELETED", null),
                Arguments.of("/also-admin", createClientAdmin, "USER_DATA_DELETED", null),
                Arguments.of("/also-admin", createClientUser, "USER_DATA_DELETED", null),
            )
        }
    }
}
