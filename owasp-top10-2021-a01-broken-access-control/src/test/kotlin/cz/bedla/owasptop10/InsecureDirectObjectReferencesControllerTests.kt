package cz.bedla.owasptop10

import dasniko.testcontainers.keycloak.KeycloakContainer
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.keycloak.admin.client.Keycloak
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.client.RestTemplate
import java.util.*
import java.util.stream.Stream


@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class InsecureDirectObjectReferencesControllerTests {
    @Autowired
    private lateinit var keycloakContainer: KeycloakContainer

    @Autowired
    private lateinit var database: Database

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
    fun referenceObjectWithSequenceIds() {

        // 1. let's try some arbitrary ID
        assertThat(
            restTemplate.getAccountUnsafeForException(999)
                .statusCode
        ).isEqualTo(HttpStatus.NOT_FOUND)

        // 2. maybe try to guess lowe one
        val responseEntity = restTemplate.getAccountUnsafe(1)
        assertThat(responseEntity.statusCode).isEqualTo(HttpStatus.OK)
        logger.logInfo("Let's see what we get {}", responseEntity.body)

        // 3. now we can try to dump whole DB
        for (id in 0..20 /* this can be any range */) {
            try {
                val userAccount = restTemplate.getAccountUnsafe(id).body
                logger.logInfo("We found account.id={} with data {}", userAccount?.id, id)
            } catch (e: RestClientResponseException) {
                logger.logWarn("There is no account.id={} in the DB", id)
            }
        }
    }

    @Test
    fun referenceObjectWithUuids() {
        // 1. let's try some arbitrary UUID
        assertThat(
            restTemplate.getAccountUuidForException(UUID.randomUUID())
                .statusCode
        ).isEqualTo(HttpStatus.NOT_FOUND)

        // 2. Somebody leaked UUIDs (or some other unsafe business logic returned different UUIDs - other that is relevant to user's identity)
        val accountUuids = database.leakAccountUuids()
        for (accountUuid in accountUuids) {
            val responseEntity = restTemplate.getAccountUuid(accountUuid)
            assertThat(responseEntity.statusCode).isEqualTo(HttpStatus.OK)
            logger.logInfo("Let's see what we get {}", responseEntity.body)
        }
    }

    @ParameterizedTest
    @MethodSource("referenceObjectWithAuthorizationProvider")
    fun referenceObjectWithAuthorization(
        idGetter: (Database) -> Any,
        clientCreator: (Keycloak) -> MyClient,
        expectedErrorStatus: HttpStatus?
    ) {
        val client = clientCreator(keycloakAdminClient)
        val id = idGetter(database)

        val action = {
            restTemplate.httpGet(
                "$baseUrl/account/{accountId}",
                UserAccount::class.java,
                realmName,
                client,
                uriVariables = mapOf(
                    "accountId" to id,
                    "port" to port
                ),
                authServerBaseUrl = keycloakContainer.authServerUrl
            )
        }

        if (expectedErrorStatus == null) {
            val userAccount = action()
            val accountId = when (id) {
                is Int -> {
                    id
                }

                is UUID -> {
                    database.getAccountIdByUuid(id)
                }

                else -> {
                    error("Strange ID type: $id")
                }
            }
            assertThat(userAccount.id).isEqualTo(accountId)
        } else {
            Assertions.assertThatThrownBy { action() }
                .isInstanceOfSatisfying(HttpClientErrorException::class.java) {
                    assertThat(it.statusCode).isEqualTo(expectedErrorStatus)
                }
        }
    }

    private fun RestTemplate.getAccountUuid(uuid: UUID): ResponseEntity<UserAccount> =
        getForEntity(
            "$baseUrl/account-uuid/{uuid}",
            UserAccount::class.java,
            mapOf("uuid" to uuid, "port" to port)
        )

    private fun RestTemplate.getAccountUuidForException(uuid: UUID): RestClientResponseException =
        try {
            getAccountUuid(uuid)
            error("We do not expect OK response")
        } catch (e: RestClientResponseException) {
            e
        }

    private fun RestTemplate.getAccountUnsafe(id: Int): ResponseEntity<UserAccount> =
        getForEntity(
            "$baseUrl/account-unsafe/{id}",
            UserAccount::class.java,
            mapOf("id" to id, "port" to port)
        )

    private fun RestTemplate.getAccountUnsafeForException(id: Int): RestClientResponseException =
        try {
            getAccountUnsafe(id)
            error("We do not expect OK response")
        } catch (e: RestClientResponseException) {
            e
        }

    companion object {
        private val baseUrl = "http://localhost:{port}/insecure-direct-object-references"
        internal val realmName = "insecure-direct-object-references"

        private val logger = LoggerFactory.getLogger(InsecureDirectObjectReferencesControllerTests::class.java)!!

        private const val logPrefix = "\n\n***\n"
        private const val logSuffix = "\n***\n"

        private fun Logger.logInfo(message: String, vararg params: Any?) =
            info("$logPrefix$message$logSuffix", *params)

        private fun Logger.logWarn(message: String, vararg params: Any?) =
            warn("$logPrefix$message$logSuffix", *params)

        @JvmStatic
        fun referenceObjectWithAuthorizationProvider(): Stream<Arguments> {
            // See Database class for authorization config
            val createClientRimmer = { kc: Keycloak -> kc.createClient(realmName, "my-client-rimmer") }
            val createClientKryton = { kc: Keycloak -> kc.createClient(realmName, "my-client-kryton") }
            val createClientLister = { kc: Keycloak -> kc.createClient(realmName, "my-client-lister") }

            fun accountId(id: Int) = { _: Database -> id }
            fun accountUuid(id: Int) = { db: Database -> db.getAccountUuidById(id) }

            return Stream.of(
                Arguments.of(accountId(1), createClientRimmer, HttpStatus.FORBIDDEN),
                Arguments.of(accountId(2), createClientRimmer, HttpStatus.FORBIDDEN),
                Arguments.of(accountId(3), createClientRimmer, null),
                // ---
                Arguments.of(accountId(1), createClientKryton, null),
                Arguments.of(accountId(2), createClientKryton, null),
                Arguments.of(accountId(3), createClientKryton, HttpStatus.FORBIDDEN),
                // ---
                Arguments.of(accountId(1), createClientLister, HttpStatus.FORBIDDEN),
                Arguments.of(accountId(2), createClientLister, HttpStatus.FORBIDDEN),
                Arguments.of(accountId(3), createClientLister, HttpStatus.FORBIDDEN),
                // ---
                Arguments.of(accountUuid(1), createClientRimmer, HttpStatus.FORBIDDEN),
                Arguments.of(accountUuid(2), createClientRimmer, HttpStatus.FORBIDDEN),
                Arguments.of(accountUuid(3), createClientRimmer, null),
                // ---
                Arguments.of(accountUuid(1), createClientKryton, null),
                Arguments.of(accountUuid(2), createClientKryton, null),
                Arguments.of(accountUuid(3), createClientKryton, HttpStatus.FORBIDDEN),
                // ---
                Arguments.of(accountUuid(1), createClientLister, HttpStatus.FORBIDDEN),
                Arguments.of(accountUuid(2), createClientLister, HttpStatus.FORBIDDEN),
                Arguments.of(accountUuid(3), createClientLister, HttpStatus.FORBIDDEN),
            )
        }
    }
}