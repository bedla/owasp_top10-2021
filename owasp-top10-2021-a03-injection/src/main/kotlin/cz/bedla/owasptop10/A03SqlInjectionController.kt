package cz.bedla.owasptop10

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDateTime

@RestController
@Profile("db")
class UserController(
    private val userRepository: UserRepository
) {
    @GetMapping("/user-injection")
    fun findUserInjection(
        @RequestParam id: String
    ): List<UserDto> {
        return userRepository.findUserInjection(id).map { it.toDto() }
    }

    @GetMapping("/user")
    fun findUser(
        @RequestParam id: String
    ): List<UserDto> {
        return userRepository.findUser(id).map { it.toDto() }
    }

    // https://www.postgresql.org/docs/current/view-pg-prepared-statements.html
    @GetMapping("/prepare-statement-dump-injection")
    @Transactional // never use @Transactional on Controller layer
    fun prepareStatementDumpInjection(): List<PrepareStatementInfo> {
        for (id in 1..10) {
            userRepository.findUserInjection("xxx$id-${LocalDateTime.now()}")
        }
        return dumpPgPreparedStatements()
    }

    @GetMapping("/prepare-statement-dump")
    @Transactional // never use @Transactional on Controller layer
    fun prepareStatementDump(): List<PrepareStatementInfo> {
        for (id in 1..10) {
            userRepository.findUser("yyy$id-${LocalDateTime.now()}")
        }
        return dumpPgPreparedStatements()
    }

    @GetMapping("/prepare-statement-clear")
    fun prepareStatementClear() {
        userRepository.clearPreparedStatements()
    }

    fun dumpPgPreparedStatements(): List<PrepareStatementInfo> = userRepository.dumpPgPreparedStatements()
        .filter {
            it.statement.contains("public.user")
        }.sortedBy { it.name }
}

data class PrepareStatementInfo(
    val name: String,
    val statement: String,
    @JsonProperty("prepare_time")
    val prepareTime: Timestamp,
    @JsonProperty("parameter_types")
    val parameterTypes: String?,
    @JsonProperty("result_types")
    val resultTypes: String?,
    @JsonProperty("from_sql")
    val fromSql: Boolean,
    @JsonProperty("generic_plans")
    val genericPlans: Int,
    @JsonProperty("custom_plans")
    val customPlans: Int
)

@Component
@Transactional
@Profile("db")
class UserRepository(
    private val jdbcTemplate: JdbcTemplate
) {
    fun findUserInjection(id: String): List<UserEntity> {
        return jdbcTemplate.query("SELECT * FROM public.user WHERE id = '$id'")
        { rs, _ -> rs.toEntity() }
    }

    fun findUser(id: String): List<UserEntity> {
        return jdbcTemplate.query(
            "SELECT * FROM public.user WHERE id = ?",
            { rs, _ -> rs.toEntity() },
            id
        )
    }

    fun dumpPgPreparedStatements(): List<PrepareStatementInfo> {
        return jdbcTemplate.queryForList("SELECT * FROM pg_prepared_statements").map {
            PrepareStatementInfo(
                it["name"] as String,
                it["statement"] as String,
                it["prepare_time"] as Timestamp,
                (it["parameter_types"] as java.sql.Array?)?.toString(),
                (it["result_types"] as java.sql.Array?)?.toString(),
                it["from_sql"] as Boolean,
                (it["generic_plans"] as Number).toInt(),
                (it["custom_plans"] as Number).toInt(),
            )
        }
    }

    private fun ResultSet.toEntity(): UserEntity = UserEntity(
        getString("id"),
        getString("first_name"),
        getString("last_name"),
        getString("user_name"),
        getString("password")
    )

    fun clearPreparedStatements() {
        return jdbcTemplate.execute("DEALLOCATE ALL")
    }
}

data class UserEntity(
    val id: String,
    val firstName: String,
    val lastName: String,
    val username: String,
    val password: String
)

private fun UserEntity.toDto() =
    UserDto(id, firstName, lastName, username, password)

data class UserDto(
    val id: String,
    val firstName: String,
    val lastName: String,
    val username: String,
    val password: String
)
