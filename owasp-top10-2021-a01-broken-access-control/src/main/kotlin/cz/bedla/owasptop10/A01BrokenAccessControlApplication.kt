package cz.bedla.owasptop10

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.oauth2.server.resource.authentication.JwtIssuerAuthenticationManagerResolver
import org.springframework.security.web.SecurityFilterChain


@SpringBootApplication
class A01BrokenAccessControlApplication(
    private val jwtIssuers: JwtIssuers
) {
    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .authorizeHttpRequests { authorize ->
                authorize
                    .requestMatchers("/elevation-of-privilege/admin/**").hasAnyAuthority("SCOPE_my-admin", "SCOPE_my-super-admin")
                    .requestMatchers("/elevation-of-privilege/super-admin/**").hasAuthority("SCOPE_my-super-admin")
                    .requestMatchers("/elevation-of-privilege/technical/**").authenticated() // here we forgot to specify admin authority
                    // here is missing /also-admin authorization configuration
                    .requestMatchers("/elevation-of-privilege/hello").anonymous()
                    // ---
                    .requestMatchers("/insecure-direct-object-references/account-unsafe/**").anonymous()
                    .requestMatchers("/insecure-direct-object-references/account-uuid/**").anonymous()
                    .requestMatchers("/insecure-direct-object-references/account/**").authenticated()
                    .anyRequest().authenticated()
            }
            .oauth2ResourceServer { oauth2 ->
//                oauth2.jwt(Customizer.withDefaults())
                oauth2.authenticationManagerResolver(jwtIssuerAuthenticationManagerResolver())
            }
        return http.build()
    }

    @Bean
    fun jwtIssuerAuthenticationManagerResolver(): JwtIssuerAuthenticationManagerResolver =
        JwtIssuerAuthenticationManagerResolver(jwtIssuers.list)
}

data class JwtIssuers(
    val list: List<String>
)

fun main(args: Array<String>) {
    runApplication<A01BrokenAccessControlApplication>(*args)
}


