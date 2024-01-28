package cz.bedla.owasptop10

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.RequestEntity
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.web.client.RestTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.util.*


@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
@Testcontainers
@ActiveProfiles("db")
class SqlInjectionControllerTest {
    @Autowired
    private lateinit var restTemplateBuilder: RestTemplateBuilder

    private lateinit var restTemplate: RestTemplate

    @LocalServerPort
    private var port = 0


    @BeforeEach
    fun setUp() {
        restTemplate = restTemplateBuilder.build()
        restTemplate.clearPreparedStatementsCache(port)
    }


    @Test
    fun unsafeFindUsersSingleUser() {
        val requestEntity = RequestEntity.method(
            HttpMethod.GET,
            "${baseUrl}/user-injection?id={id}",
            mapOf(
                "id" to "a1",
                "port" to port
            )
        ).build()

        val responseEntity = restTemplate.exchange(requestEntity, typeRef<List<UserDto>>())

        assertThat(responseEntity.statusCode)
            .isEqualTo(HttpStatus.OK)
        val response = responseEntity.body ?: error("No response body")
        assertThat(response)
            .hasSize(1)
            .allSatisfy {
                assertThat(it.id).isEqualTo("a1")
                assertThat(it.firstName).isEqualTo("Vincent")
                assertThat(it.lastName).isEqualTo("Vega")
                assertThat(it.username).isEqualTo("vincent")
                assertThat(it.password).isEqualTo("password")
            }
    }

    @Test
    fun unsafeFindUsersNoMatch() {
        val requestEntity = RequestEntity.method(
            HttpMethod.GET,
            "${baseUrl}/user-injection?id={id}",
            mapOf(
                "id" to UUID.randomUUID(),
                "port" to port
            )
        ).build()

        val responseEntity = restTemplate.exchange(requestEntity, typeRef<List<UserDto>>())

        assertThat(responseEntity.statusCode)
            .isEqualTo(HttpStatus.OK)
        val response = responseEntity.body ?: error("No response body")
        assertThat(response)
            .isEmpty()
    }

    @Test
    fun unsafeFindUsersWithSqlInjection() {
        val requestEntity = RequestEntity.method(
            HttpMethod.GET,
            "${baseUrl}/user-injection?id={id}",
            mapOf(
                "id" to "' OR 1=1 --",
                "port" to port
            )
        ).build()

        val responseEntity = restTemplate.exchange(requestEntity, typeRef<List<UserDto>>())

        assertThat(responseEntity.statusCode)
            .isEqualTo(HttpStatus.OK)
        val response = responseEntity.body ?: error("No response body")
        assertThat(response)
            .hasSize(6)
        assertThat(response.map { it.id })
            .containsExactlyInAnyOrder("a1", "a2", "a3", "a4", "a5", "a6")
    }

    @ParameterizedTest
    @CsvSource(
        "/prepare-statement-dump-injection , 10",
        "/prepare-statement-dump           ,  1"
    )
    fun preparedStatements(urlPath: String, expectedCount: Int) {
        val requestEntity = RequestEntity.method(
            HttpMethod.GET,
            "${baseUrl}$urlPath",
            mapOf(
                "port" to port
            )
        ).build()

        val responseEntity = restTemplate.exchange(requestEntity, typeRef<List<PrepareStatementInfo>>())

        assertThat(responseEntity.statusCode)
            .isEqualTo(HttpStatus.OK)
        val response = responseEntity.body ?: error("No response body")
        assertThat(response)
            .hasSize(expectedCount)

        logger.info("\n\n***\n{}\n***\n", response.mapIndexed { index, it -> "${index + 1}.\t${it.name} -> ${it.statement}" }.joinToString("\n"))
    }

    companion object {
        private val logger = LoggerFactory.getLogger(SqlInjectionControllerTest::class.java)!!

        private val baseUrl = "http://localhost:{port}"

        inline fun <reified T : Any> typeRef(): ParameterizedTypeReference<T> = object : ParameterizedTypeReference<T>() {}

        @JvmStatic
        @Container
        private val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:15"))

        @JvmStatic
        @DynamicPropertySource
        fun postgresProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
        }

        fun RestTemplate.clearPreparedStatementsCache(port: Int) {
            val responseEntity = this.getForEntity(
                "${baseUrl}/prepare-statement-clear",
                Any::class.java,
                mapOf(
                    "port" to port
                )
            )
            assertThat(responseEntity.statusCode)
                .isEqualTo(HttpStatus.OK)
        }
    }
}
