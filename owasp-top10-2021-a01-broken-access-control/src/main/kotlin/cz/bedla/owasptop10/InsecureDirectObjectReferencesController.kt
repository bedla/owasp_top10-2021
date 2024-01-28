package cz.bedla.owasptop10

import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import java.util.*

@RestController
class InsecureDirectObjectReferencesController(
    private val database: Database
) {
    @GetMapping("/insecure-direct-object-references/account-unsafe/{id}")
    fun getAccountUnsafe(
        @PathVariable id: Int
    ): ResponseEntity<UserAccount> {
        val userAccount = database.findAccountById(id)
        return userAccount.toResponseEntity()
    }

    @GetMapping("/insecure-direct-object-references/account-uuid/{uuid}")
    fun getAccountUUID(
        @PathVariable uuid: UUID
    ): ResponseEntity<UserAccount> {
        val userAccount = database.findAccountByUuid(uuid)
        return userAccount.toResponseEntity()
    }

    @GetMapping("/insecure-direct-object-references/account/{idStr}")
    fun getAccount(
        @PathVariable idStr: String,
        @AuthenticationPrincipal principal: Jwt
    ): ResponseEntity<UserAccount> {
        val id: Any = if (idStr.contains("-")) {
            UUID.fromString(idStr)
        } else {
            idStr.toInt()
        }

        checkAuthorization(id, principal)

        val userAccount = database.findAccountBy(id)
        return userAccount.toResponseEntity()
    }

    private fun checkAuthorization(id: Any, principal: Jwt) {
        if (!database.userIsOwnerOfUserAccount(principal.claims["preferred_username"]?.toString() ?: "", id)) {
            throw AccessDeniedException("Unable to access")
        }
    }

    companion object {
        private fun UserAccount?.toResponseEntity(): ResponseEntity<UserAccount> {
            return if (this == null) ResponseEntity.notFound().build() else ResponseEntity.ok(this)
        }
    }
}

@Component
class Database {
    private val accounts = mutableMapOf<Int, UserAccount>()
    private val translator = mutableMapOf<UUID, Int>()
    private val authorization = mutableMapOf<String, List<UserAccount>>()

    init {
        val account1 = UserAccount(1, "Vincent Vega")
        val account2 = UserAccount(2, "Jules Winnfield")
        val account3 = UserAccount(3, "Mia Wallace")
        val accountsMap = listOf(account1, account2, account3)
            .groupBy { it.id }.mapValues { it.value.first() }
        accounts.putAll(accountsMap)

        val translatorMap = accounts.keys.map { UUID.randomUUID()!! to it }
            .groupBy({ it.first }, { it.second }).mapValues { it.value.first() }
        translator.putAll(translatorMap)

        val authorizationMap = mapOf(
            "service-account-my-client-kryton" to listOf(account1, account2),
            "service-account-my-client-rimmer" to listOf(account3),
            "service-account-my-client-lister" to listOf()
        )
        authorization.putAll(authorizationMap)

        logger.info("\n::: Accounts :::")
        accounts.values.forEach { logger.info("{}", it) }
        logger.info("\n::: Translator :::")
        translator.entries.forEach { logger.info("{} -> {}", it.key, it.value) }
        logger.info("\n")
    }

    fun findAccountById(id: Int): UserAccount? {
        return findAccountInternal(id)
    }

    fun findAccountByUuid(uuid: UUID): UserAccount? {
        val id = translate(uuid)
        return findAccountInternal(id)
    }

    fun findAccountBy(accountId: Any): UserAccount? {
        val id = translate(accountId)
        return findAccountInternal(id)
    }

    fun userIsOwnerOfUserAccount(username: String, accountId: Any): Boolean {
        val id = translate(accountId)
        return authorization[username]?.any { it.id == id } ?: false
    }

    private fun translate(accountId: Any): Int? = when (accountId) {
        is Int -> accountId
        is UUID -> translator[accountId]
        else -> null
    }

    private fun findAccountInternal(id: Int?): UserAccount? {
        return accounts[id]
    }

    fun leakAccountUuids(): List<UUID> = translator.keys.toList()

    fun getAccountIdByUuid(uuid: UUID): Int =
        translate(uuid) ?: error("Unable to find account by UUID $uuid")

    fun getAccountUuidById(id: Int): UUID =
        translator.filter { it.value == id }.firstNotNullOf { it.key }

    companion object {
        private val logger = LoggerFactory.getLogger(Database::class.java)!!
    }
}

data class UserAccount(
    val id: Int,
    val name: String
)