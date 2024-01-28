package cz.bedla.owasptop10

import org.springframework.context.annotation.Profile
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import java.nio.file.Files
import java.nio.file.Path

// https://github.com/google/security-research/security/advisories/GHSA-mjmj-j48q-9wg2
@RestController
@Profile("rce")
class RemoteCodeExecutionController {
    @PostMapping("/process-yaml-unsafe")
    fun processYamlUnsafe(
        @RequestBody
        request: ProcessYamlRequest
    ): ResponseEntity<Any> {
        val yamlDto = Yaml().load<Any>(request.yaml)
        return ResponseEntity.ok(mapOf("result" to yamlDto.toString()))
    }

    @PostMapping("/process-yaml-safe")
    fun processYamlSafe(
        @RequestBody
        request: ProcessYamlRequest
    ): ResponseEntity<Any> {
        val yamlDto = Yaml(SafeConstructor()).load<Any>(request.yaml)
        return ResponseEntity.ok(mapOf("result" to yamlDto.toString()))
    }

    @GetMapping("/evil-jar")
    fun downloadEvilJar(): ResponseEntity<ByteArrayResource> {

        val resource = ByteArrayResource(
            Files.readAllBytes(
                Path.of(
                    ".",
                    "owasp-top10-2021-a03-injection",
                    "target",
                    "owasp-top10-2021-a03-injection-0.0.1-SNAPSHOT.jar.original"
                )
            )
        )

        return ResponseEntity.ok()
            .contentLength(resource.contentLength())
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(resource)
    }

    @GetMapping("/reset-hacked-status")
    fun resetHackedStatus(): ResponseEntity<Any> {
        if (Files.exists(Path.of(".", "hacked.txt"))) {
            Files.delete(Path.of(".", "hacked.txt"))
        }
        return ResponseEntity.ok().build()
    }

    @GetMapping("/get-hacked-status")
    fun getHackedStatus(): ResponseEntity<Map<*, *>> {
        return ResponseEntity.ok(
            mapOf(
                "hacked" to Files.exists(Path.of(".", "hacked.txt"))
            )
        )
    }

}

data class ProcessYamlRequest(
    val yaml: String
)