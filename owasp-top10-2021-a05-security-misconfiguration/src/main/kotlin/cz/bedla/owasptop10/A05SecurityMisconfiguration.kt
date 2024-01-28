package cz.bedla.owasptop10

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.*
import java.net.URI
import java.time.LocalDateTime
import java.util.*


@SpringBootApplication
class A05SecurityMisconfiguration {
}

fun main(args: Array<String>) {
    runApplication<A05SecurityMisconfiguration>(*args)
}

@RestController
class UserController(
    private val database: Database
) {
    @GetMapping("/find-user-global-handler")
    fun findUserGlobalHandler(
        @RequestParam("last-name")
        lastName: String
    ): ResponseEntity<UserDto> {
        return invoke { database.findUserGlobalHandler(lastName) }
    }

    @GetMapping("/find-user-custom-handler")
    fun findUserCustomHandler(
        @RequestParam("last-name")
        lastName: String
    ): ResponseEntity<UserDto> {
        return invoke { database.findUserCustomHandler(lastName) }
    }

    @GetMapping("/find-user-problemdetails-handler")
    fun findUserProblemDetailsHandler(
        @RequestParam("last-name")
        lastName: String
    ): ResponseEntity<UserDto> {
        return invoke { database.findUserProblemDetailsHandler(lastName) }
    }

    private fun invoke(action: () -> UserEntity?): ResponseEntity<UserDto> {
        val userEntity = action()
        return if (userEntity == null) {
            ResponseEntity.notFound().build()
        } else {
            ResponseEntity.ok(userEntity.toDto())
        }
    }

    private fun UserEntity.toDto(): UserDto {
        return UserDto(UUID.randomUUID(), firstName, lastName)
    }
}


@RestControllerAdvice
@Profile("raw-entity-exception-handling")
class CustomExceptionHandlerEntity {
    @ExceptionHandler(CustomHandlerSensitiveUserEntityException::class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    fun customHandlerSensitiveUserEntityException(e: CustomHandlerSensitiveUserEntityException): CustomRestErrorResponse {
        return CustomRestErrorResponse(
            HttpStatus.FORBIDDEN.value(),
            e.message ?: "n/a",
            LocalDateTime.now()
        )
    }

    @ExceptionHandler(ProblemDetailsHandlerSensitiveUserEntityException::class)
    fun problemDetailsHandlerSensitiveUserEntityException(e: ProblemDetailsHandlerSensitiveUserEntityException): ProblemDetail {
        return ProblemDetail.forStatus(HttpStatus.FORBIDDEN).also {
            it.setType(URI.create("http://localhost:8080/errors/customer-not-found"))
            it.title = "Sensitive entity"
            it.setProperty("entity", e.userEntity)
        }
    }
}

@RestControllerAdvice
@Profile("id-exception-handling")
class CustomExceptionHandlerId {
    @ExceptionHandler(CustomHandlerSensitiveUserEntityException::class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    fun customHandlerSensitiveUserEntityException(e: CustomHandlerSensitiveUserEntityException): CustomRestErrorResponse {
        return CustomRestErrorResponse(
            HttpStatus.FORBIDDEN.value(),
            "Problem with entityId=${e.userEntity.id}",
            LocalDateTime.now()
        )
    }

    @ExceptionHandler(ProblemDetailsHandlerSensitiveUserEntityException::class)
    fun problemDetailsHandlerSensitiveUserEntityException(e: ProblemDetailsHandlerSensitiveUserEntityException): ProblemDetail {
        return ProblemDetail.forStatus(HttpStatus.FORBIDDEN).also {
            it.setType(URI.create("http://localhost:8080/errors/customer-not-found"))
            it.title = "Sensitive entity"
            it.setProperty("entityId", e.userEntity.id)
        }
    }
}

@RestControllerAdvice
@Profile("uuid-exception-handling")
class CustomExceptionHandlerUuid {
    @ExceptionHandler(CustomHandlerSensitiveUserEntityException::class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    fun customHandlerSensitiveUserEntityException(e: CustomHandlerSensitiveUserEntityException): CustomRestErrorResponse {
        return CustomRestErrorResponse(
            HttpStatus.FORBIDDEN.value(),
            "Problem with entityUuid=${UUID.randomUUID()}",
            LocalDateTime.now()
        )
    }

    @ExceptionHandler(ProblemDetailsHandlerSensitiveUserEntityException::class)
    fun problemDetailsHandlerSensitiveUserEntityException(e: ProblemDetailsHandlerSensitiveUserEntityException): ProblemDetail {
        return ProblemDetail.forStatus(HttpStatus.FORBIDDEN).also {
            it.setType(URI.create("http://localhost:8080/errors/customer-not-found"))
            it.title = "Sensitive entity"
            it.setProperty("entityUuid", UUID.randomUUID())
        }
    }
}

@Service
class Database {
    private val users = mutableListOf<UserEntity>()

    init {
        users += UserEntity(123, "Butch", "Coolidge")
        users += UserEntity(456, "Winston", "Wolf")
        users += UserEntity(789, "Esmeralda", "Villalobos")
    }

    fun findUserGlobalHandler(lastName: String): UserEntity? {
        return invoke(lastName) { entity -> GlobalHandlerSensitiveUserEntityException(entity) }
    }

    fun findUserCustomHandler(lastName: String): UserEntity? {
        return invoke(lastName) { entity -> CustomHandlerSensitiveUserEntityException(entity) }
    }

    fun findUserProblemDetailsHandler(lastName: String): UserEntity? {
        return invoke(lastName) { entity -> ProblemDetailsHandlerSensitiveUserEntityException(entity) }
    }

    private fun invoke(lastName: String, createExceptionAction: (UserEntity) -> Exception): UserEntity? {
        val entity = users.find { it.lastName.contains(lastName) } ?: return null
        if (entity.lastName.startsWith(prefix = "w", ignoreCase = true)) {
            throw createExceptionAction(entity)
        } else {
            return entity
        }
    }
}

class ProblemDetailsHandlerSensitiveUserEntityException(
    val userEntity: UserEntity
) : IllegalStateException("UserEntity is sensitive: $userEntity")

class CustomHandlerSensitiveUserEntityException(
    val userEntity: UserEntity
) : IllegalStateException("UserEntity is sensitive: $userEntity")

class GlobalHandlerSensitiveUserEntityException(
    val userEntity: UserEntity
) : IllegalStateException("UserEntity is sensitive: $userEntity")

data class CustomRestErrorResponse(
    val status: Int,
    val message: String,
    val timestamp: LocalDateTime
)

data class UserDto(
    val uuid: UUID,
    val firstName: String,
    val lastName: String
)

data class UserEntity(
    val id: Int,
    val firstName: String,
    val lastName: String
)