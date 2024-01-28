package cz.bedla.owasptop10

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication


@SpringBootApplication
class A03InjectionApplication {
}

fun main(args: Array<String>) {
    runApplication<A03InjectionApplication>(*args)
}
