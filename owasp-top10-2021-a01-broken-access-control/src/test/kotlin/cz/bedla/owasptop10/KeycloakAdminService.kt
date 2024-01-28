package cz.bedla.owasptop10

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.ws.rs.NotFoundException
import jakarta.ws.rs.core.Response
import org.keycloak.admin.client.Keycloak
import org.keycloak.admin.client.resource.ClientResource
import org.keycloak.representations.idm.ClientRepresentation
import org.keycloak.representations.idm.ClientScopeRepresentation
import org.keycloak.representations.idm.RealmRepresentation
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.RequestEntity
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestTemplate

object KeycloakAdminObject {
    internal val logger = LoggerFactory.getLogger(KeycloakAdminObject::class.java)!!

}

fun Keycloak.createRealm(realmName: String) {
    val realmsResource = realms()
    try {
        realmsResource.realm(realmName).toRepresentation()
        // found, do nothing
    } catch (e: NotFoundException) {
        KeycloakAdminObject.logger.trace("Unable to find realm {}", realmName, e)

        realmsResource.create(RealmRepresentation().apply {
            realm = realmName
            isEnabled = true
        })
    }
}

private fun Keycloak.createClientScope(realmName: String, scopeName: String): String {
    val existingScope = realms().realm(realmName).clientScopes().findAll().firstOrNull { it.name == scopeName }
    return if (existingScope == null) {
        val clientScopeUuid = realms().realm(realmName).clientScopes().create(ClientScopeRepresentation().apply {
            name = scopeName
            description = ""
            attributes = mapOf(
                "consent.screen.text" to "",
                "display.on.consent.screen" to "true",
                "include.in.token.scope" to "true",
                "gui.order" to ""
            )
            protocol = "openid-connect"
        }).use { response ->
            response.extractIdFromLocationHeader()
        }
        clientScopeUuid
    } else {
        existingScope.id
    }
}

private fun Keycloak.findClient(realmName: String, clientUuid: String): ClientResource {
    return realms().realm(realmName).clients().get(clientUuid) ?: error("Unable to find clientUuid=$clientUuid @ realm=$realmName")
}

private fun Keycloak.createClient(realmName: String, clientId: String): String {
    val clientsResource = realms().realm(realmName).clients()
    val clientUuid = clientsResource.create(ClientRepresentation().apply {
        protocol = "openid-connect"
        this.clientId = clientId
        name = ""
        description = ""
        isPublicClient = false
        authorizationServicesEnabled = false
        isServiceAccountsEnabled = true
        isImplicitFlowEnabled = false
        isDirectAccessGrantsEnabled = false
        isStandardFlowEnabled = false
        isFrontchannelLogout = true
        attributes = mapOf(
            "saml_idp_initiated_sso_url_name" to "",
            "oauth2.device.authorization.grant.enabled" to "false",
            "oidc.ciba.grant.enabled" to "false"
        )
        isAlwaysDisplayInConsole = false
        rootUrl = ""
        baseUrl = ""
    }).use { response ->
        response.extractIdFromLocationHeader()
    }
    return clientUuid
}

private fun Response.extractIdFromLocationHeader() =
    getHeaderString("Location")
        ?.substringAfterLast("/")
        ?: error("Unable to find Location in response: ${statusInfo}, ${headers}, ${readEntity(String::class.java)}")

fun Keycloak.createClient(realmName: String, clientId: String, vararg scopes: String): MyClient {
    val clientUuid =
        realms().realm(realmName).clients().findByClientId(clientId)?.firstOrNull().let {
            if (it == null) {
                KeycloakAdminObject.logger.trace("Unable to find client {} in realm {}", clientId, realmName)
                createClient(realmName, clientId)
            } else {
                it.id!!
            }
        }

    val client = findClient(realmName, clientUuid)
    val clientScopeUuids = scopes.map { createClientScope(realmName, it) }
    val existingClientScopeUuids = client.defaultClientScopes.map { it.id!! }.toSet()
    val clientScopeUuidsToAttach = clientScopeUuids.filter { scopeUuid -> !existingClientScopeUuids.contains(scopeUuid) }
    clientScopeUuidsToAttach.forEach { client.addDefaultClientScope(it) }

    return MyClient(clientId, client.secret.value)
}

fun <T> RestTemplate.httpGet(
    url: String,
    clazz: Class<T>,
    realmName: String? = null,
    client: MyClient? = null,
    uriVariables: Map<String, Any> = emptyMap(),
    authServerBaseUrl: String
): T {
    val self = this
    val requestEntity = RequestEntity.get(url)
        .headers(HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            if (client != null && realmName != null) {
                val accessToken = self.loginForToken(realmName, client, authServerBaseUrl)
                setBearerAuth(accessToken)
            }
        }).build()

    val responseEntity = this.exchange(url, HttpMethod.GET, requestEntity, clazz, uriVariables)
    return responseEntity.body ?: error("No body")
}

private fun RestTemplate.loginForToken(
    realmName: String,
    client: MyClient,
    baseUrl: String
): String {
    val url = "$baseUrl/realms/$realmName/protocol/openid-connect/token"
    val requestEntity = RequestEntity.post(url)
        .headers(HttpHeaders().apply {
            contentType = MediaType.APPLICATION_FORM_URLENCODED
            setBasicAuth(client.clientId, client.clientSecret)
        })
        .body(LinkedMultiValueMap(mapOf("grant_type" to listOf("client_credentials"))))

    val responseEntity = this.exchange(url, HttpMethod.POST, requestEntity, TokenResponse::class.java)
    return responseEntity.body?.accessToken ?: error("Access token not found")
}

data class MyClient(
    val clientId: String,
    val clientSecret: String
)

data class TokenResponse(
    @JsonProperty("access_token")
    val accessToken: String,
    @JsonProperty("expires_in")
    val expiresIn: Int,
    @JsonProperty("refresh_expires_in")
    val refreshExpiresIn: Int,
    @JsonProperty("token_type")
    val tokenType: String,
    @JsonProperty("not-before-policy")
    val notBeforePolicy: Int,
    val scope: String
)
