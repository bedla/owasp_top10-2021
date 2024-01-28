package cz.bedla.owasptop10

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.ZonedDateTime

@RestController
class ElevationOfPrivilegeController {
    @GetMapping("/elevation-of-privilege/hello")
    fun hello(
        @RequestParam(required = false, defaultValue = "World")
        name: String
    ): ResponseEntity<HelloResponse> {
        return ResponseEntity.ok(
            HelloResponse("Hello $name", ZonedDateTime.now())
        )
    }

    @GetMapping("/elevation-of-privilege/admin")
    fun adminAction(): ResponseEntity<AdminActionResponse> {
        return ResponseEntity.ok(
            AdminActionResponse("ALL_DATA_DELETED", ZonedDateTime.now())
        )
    }

    @GetMapping("/elevation-of-privilege/super-admin")
    fun superAdminAction(): ResponseEntity<AdminActionResponse> {
        return ResponseEntity.ok(
            AdminActionResponse("FORMAT C:", ZonedDateTime.now())
        )
    }

    @GetMapping("/elevation-of-privilege/technical")
    fun adminTechnicalAction(): ResponseEntity<AdminActionResponse> {
        return ResponseEntity.ok(
            AdminActionResponse("Calculate ULTIMATE QUESTION", ZonedDateTime.now())
        )
    }

    @GetMapping("/elevation-of-privilege/also-admin")
    fun alsoAdminAction(): ResponseEntity<AdminActionResponse> {
        return ResponseEntity.ok(
            AdminActionResponse("USER_DATA_DELETED", ZonedDateTime.now())
        )
    }

    data class AdminActionResponse(
        val dataDeleted: String,
        val timestamp: ZonedDateTime
    )

    data class HelloResponse(
        val message: String,
        val timestamp: ZonedDateTime
    )
}