package cz.bedla.owasptop10

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.assertj.core.api.InstanceOfAssertFactories.map
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer
import org.springframework.core.ParameterizedTypeReference
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.Environment
import org.springframework.core.env.MapPropertySource
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.exchange
import kotlin.Int


class SecurityMisconfigurationTest {
    @ParameterizedTest
    @EnumSource(value = TestCase::class)
    fun testHandlers(testCase: TestCase) {
        SpringApplicationBuilder(A05SecurityMisconfiguration::class.java).apply {
            testCase.profile?.let { profiles(it) }
            sources(TestConfigGlobalInsecure::class.java, TestConfigGlobalSecure::class.java)
            web(WebApplicationType.SERVLET)
        }
            .run()
            .use { context ->
                val environment = context.getBean(Environment::class.java)
                val port = environment.getProperty("local.server.port", Int::class.java)

                val restTemplate = context.getBean(RestTemplateBuilder::class.java).build()!!

                val action: () -> ResponseEntity<Map<String, Any?>> = {
                    restTemplate.exchange<Map<String, Any?>>(
                        "http://localhost:$port${testCase.urlPath}?last-name={lastName}",
                        HttpMethod.GET,
                        null,
                        mapOf(
                            "port" to port,
                            "lastName" to testCase.lastName
                        )
                    )
                }

                if (testCase.assertExceptionAction == null && testCase.assertResponseEntityAction != null) {
                    val responseEntity = action()
                    testCase.assertResponseEntityAction.invoke(responseEntity)
                } else if (testCase.assertExceptionAction != null && testCase.assertResponseEntityAction == null) {
                    try {
                        val responseEntity = action()
                        fail("Exception expected, but we got $responseEntity")
                    } catch (e: Exception) {
                        testCase.assertExceptionAction.invoke(e)
                    }
                } else {
                    error("You cannot specify both asserts for $testCase")
                }
            }
    }

    @Configuration
    @Profile("global-unhandled-exception-insecure")
    class TestConfigGlobalInsecure {
        @Bean
        fun propertySourcesPlaceholderConfigurer(environment: Environment): PropertySourcesPlaceholderConfigurer {
            return PropertySourcesPlaceholderConfigurer().also {
                val ps = (environment as ConfigurableEnvironment).propertySources
                ps.addFirst(MapPropertySource("my-test-properties", createPropertiesMapInsecure()))
                it.setPropertySources(ps)
            }
        }
    }

    @Configuration
    @Profile("global-unhandled-exception-secure")
    class TestConfigGlobalSecure {
        @Bean
        fun propertySourcesPlaceholderConfigurer(environment: Environment): PropertySourcesPlaceholderConfigurer {
            return PropertySourcesPlaceholderConfigurer().also {
                val ps = (environment as ConfigurableEnvironment).propertySources
                ps.addFirst(MapPropertySource("my-test-properties", createPropertiesMapSecure()))
                it.setPropertySources(ps)
            }
        }
    }

    companion object {
        fun createPropertiesMapInsecure(): Map<String, String> = createPropertiesMap(
            serverErrorIncludeException = "true",
            serverErrorIncludeMessage = "always",
            serverErrorIncludeStacktrace = "always"
        )

        fun createPropertiesMapSecure(): Map<String, String> = createPropertiesMap(
            serverErrorIncludeException = "false",
            serverErrorIncludeMessage = "never",
            serverErrorIncludeStacktrace = "never"
        )

        fun createPropertiesMap(
            serverErrorIncludeException: String,
            serverErrorIncludeMessage: String,
            serverErrorIncludeStacktrace: String
        ): Map<String, String> {
            return mapOf(
                "server.port" to "0",
                "server.error.include-binding-errors" to "always",
                "server.error.include-exception" to serverErrorIncludeException,
                "server.error.include-message" to serverErrorIncludeMessage,
                "server.error.include-stacktrace" to serverErrorIncludeStacktrace
            )
        }
    }

    enum class TestCase(
        val lastName: String,
        val urlPath: String,
        val profile: String? = null,
        val assertExceptionAction: ((Exception) -> Unit)? = null,
        val assertResponseEntityAction: ((ResponseEntity<Map<String, Any?>>) -> Unit)? = null
    ) {
        GLOBAL_NOT_FOUND(
            lastName = "Smid",
            urlPath = "/find-user-global-handler",
            assertExceptionAction = { e ->
                assertThat(e)
                    .isInstanceOf(HttpClientErrorException.NotFound::class.java)
            }
        ),
        GLOBAL_OK(
            lastName = "Coolidge",
            urlPath = "/find-user-global-handler",
            assertResponseEntityAction = { responseEntity ->
                assertThat(responseEntity.statusCode)
                    .isEqualTo(HttpStatus.OK)
                assertThat(responseEntity.body)
                    .containsEntry("firstName", "Butch")
                    .containsEntry("lastName", "Coolidge")
                    .hasEntrySatisfying("uuid") { assertThat(it).isNotNull }
            }),
        GLOBAL_UNHANDLED_EXCEPTION_INSECURE(
            lastName = "Wolf",
            urlPath = "/find-user-global-handler",
            profile = "global-unhandled-exception-insecure",
            assertExceptionAction = { e ->
                assertThat(e)
                    .isInstanceOfSatisfying(HttpServerErrorException.InternalServerError::class.java) { responseException ->
                        assertThat(responseException.getResponseBodyAs(object : ParameterizedTypeReference<Map<String, Any?>>() {}))
                            .hasEntrySatisfying("timestamp") { assertThat(it).isNotNull }
                            .hasEntrySatisfying("status") { assertThat(it).isEqualTo(500) }
                            .hasEntrySatisfying("error") { assertThat(it).isEqualTo("Internal Server Error") }
                            .hasEntrySatisfying("exception") { assertThat(it).isEqualTo(GlobalHandlerSensitiveUserEntityException::class.qualifiedName) }
                            .hasEntrySatisfying("trace") {
                                assertThat(it)
                                    .asString()
                                    .contains("cz.bedla.owasptop10.UserController.findUserGlobalHandler")
                            }
                            .hasEntrySatisfying("message") {
                                assertThat(it)
                                    .asString()
                                    .contains("UserEntity is sensitive")
                                    .contains("UserEntity(id=456, firstName=Winston, lastName=Wolf)")
                            }
                            .hasEntrySatisfying("path") {
                                assertThat(it)
                                    .asString()
                                    .isEqualTo("/find-user-global-handler")
                            }
                    }
            }
        ),
        GLOBAL_UNHANDLED_EXCEPTION_SECURE(
            lastName = "Wolf",
            urlPath = "/find-user-global-handler",
            profile = "global-unhandled-exception-secure",
            assertExceptionAction = { e ->
                assertThat(e)
                    .isInstanceOfSatisfying(HttpServerErrorException.InternalServerError::class.java) { responseException ->
                        assertThat(responseException.getResponseBodyAs(object : ParameterizedTypeReference<Map<String, Any?>>() {}))
                            .hasEntrySatisfying("timestamp") { assertThat(it).isNotNull }
                            .hasEntrySatisfying("status") { assertThat(it).isEqualTo(500) }
                            .hasEntrySatisfying("error") { assertThat(it).isEqualTo("Internal Server Error") }
                            .doesNotContainKeys(
                                "exception",
                                "trace",
                                "message"
                            )
                            .hasEntrySatisfying("path") {
                                assertThat(it)
                                    .asString()
                                    .isEqualTo("/find-user-global-handler")
                            }
                    }
            }
        ),
        CUSTOM_HANDLER_RAW_ENTITY(
            lastName = "Wolf",
            urlPath = "/find-user-custom-handler",
            profile = "raw-entity-exception-handling",
            assertExceptionAction = { e ->
                assertThat(e)
                    .isInstanceOfSatisfying(HttpClientErrorException.Forbidden::class.java) { responseException ->
                        assertThat(responseException.getResponseBodyAs(object : ParameterizedTypeReference<Map<String, Any?>>() {}))
                            .hasEntrySatisfying("timestamp") { assertThat(it).isNotNull }
                            .hasEntrySatisfying("status") { assertThat(it).isEqualTo(403) }
                            .hasEntrySatisfying("message") {
                                assertThat(it)
                                    .asString()
                                    .contains("UserEntity is sensitive")
                                    .contains("UserEntity(id=456, firstName=Winston, lastName=Wolf)")
                            }
                    }
            }
        ),
        CUSTOM_HANDLER_ENTITY_ID(
            lastName = "Wolf",
            urlPath = "/find-user-custom-handler",
            profile = "id-exception-handling",
            assertExceptionAction = { e ->
                assertThat(e)
                    .isInstanceOfSatisfying(HttpClientErrorException.Forbidden::class.java) { responseException ->
                        assertThat(responseException.getResponseBodyAs(object : ParameterizedTypeReference<Map<String, Any?>>() {}))
                            .hasEntrySatisfying("timestamp") { assertThat(it).isNotNull }
                            .hasEntrySatisfying("status") { assertThat(it).isEqualTo(403) }
                            .hasEntrySatisfying("message") { assertThat(it).isEqualTo("Problem with entityId=456") }
                    }
            }
        ),
        CUSTOM_HANDLER_ENTITY_UUID(
            lastName = "Wolf",
            urlPath = "/find-user-custom-handler",
            profile = "uuid-exception-handling",
            assertExceptionAction = { e ->
                assertThat(e)
                    .isInstanceOfSatisfying(HttpClientErrorException.Forbidden::class.java) { responseException ->
                        assertThat(responseException.getResponseBodyAs(object : ParameterizedTypeReference<Map<String, Any?>>() {}))
                            .hasEntrySatisfying("timestamp") { assertThat(it).isNotNull }
                            .hasEntrySatisfying("status") { assertThat(it).isEqualTo(403) }
                            .hasEntrySatisfying("message") {
                                assertThat(it)
                                    .asString()
                                    .startsWith("Problem with entityUuid=")
                            }
                    }
            }
        ),
        PROBLEM_DETAILS_HANDLER_RAW_ENTITY(
            lastName = "Wolf",
            urlPath = "/find-user-problemdetails-handler",
            profile = "raw-entity-exception-handling",
            assertExceptionAction = { e ->
                assertThat(e)
                    .isInstanceOfSatisfying(HttpClientErrorException.Forbidden::class.java) { responseException ->
                        assertThat(responseException.getResponseBodyAs(object : ParameterizedTypeReference<Map<String, Any?>>() {}))
                            .hasEntrySatisfying("instance") { assertThat(it).isEqualTo("/find-user-problemdetails-handler") }
                            .hasEntrySatisfying("title") { assertThat(it).isEqualTo("Sensitive entity") }
                            .hasEntrySatisfying("type") { assertThat(it).isEqualTo("http://localhost:8080/errors/customer-not-found") }
                            .hasEntrySatisfying("status") { assertThat(it).isEqualTo(403) }
                            .hasEntrySatisfying("entity") {
                                assertThat(it)
                                    .asInstanceOf(map(String::class.java, Any::class.java))
                                    .containsEntry("id", 456)
                                    .containsEntry("firstName", "Winston")
                                    .containsEntry("lastName", "Wolf")
                            }
                    }
            }
        ),
        PROBLEM_DETAILS_HANDLER_ENTITY_ID(
            lastName = "Wolf",
            urlPath = "/find-user-problemdetails-handler",
            profile = "id-exception-handling",
            assertExceptionAction = { e ->
                assertThat(e)
                    .isInstanceOfSatisfying(HttpClientErrorException.Forbidden::class.java) { responseException ->
                        assertThat(responseException.getResponseBodyAs(object : ParameterizedTypeReference<Map<String, Any?>>() {}))
                            .hasEntrySatisfying("instance") { assertThat(it).isEqualTo("/find-user-problemdetails-handler") }
                            .hasEntrySatisfying("title") { assertThat(it).isEqualTo("Sensitive entity") }
                            .hasEntrySatisfying("type") { assertThat(it).isEqualTo("http://localhost:8080/errors/customer-not-found") }
                            .hasEntrySatisfying("status") { assertThat(it).isEqualTo(403) }
                            .hasEntrySatisfying("entityId") { assertThat(it).isEqualTo(456) }
                            .doesNotContainKeys("entity", "entityUuid")
                    }
            }
        ),
        PROBLEM_DETAILS_HANDLER_ENTITY_UUID(
            lastName = "Wolf",
            urlPath = "/find-user-problemdetails-handler",
            profile = "uuid-exception-handling",
            assertExceptionAction = { e ->
                assertThat(e)
                    .isInstanceOfSatisfying(HttpClientErrorException.Forbidden::class.java) { responseException ->
                        assertThat(responseException.getResponseBodyAs(object : ParameterizedTypeReference<Map<String, Any?>>() {}))
                            .hasEntrySatisfying("instance") { assertThat(it).isEqualTo("/find-user-problemdetails-handler") }
                            .hasEntrySatisfying("title") { assertThat(it).isEqualTo("Sensitive entity") }
                            .hasEntrySatisfying("type") { assertThat(it).isEqualTo("http://localhost:8080/errors/customer-not-found") }
                            .hasEntrySatisfying("status") { assertThat(it).isEqualTo(403) }
                            .hasEntrySatisfying("entityUuid") { assertThat(it).isNotNull }
                            .doesNotContainKeys("entity", "entityId")
                    }
            }
        )
    }
}