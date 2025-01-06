package com.codehunter.spring_kotlin_todo

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.annotation.PostConstruct
import jakarta.persistence.*
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.core.env.Environment
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.bind.annotation.ExceptionHandler

@SpringBootApplication
class SpringKotlinTodoApplication {
    @Autowired
    lateinit var environment: Environment

    @PostConstruct
    fun printConfigProperties() {
        var propertyKeys = listOf<String>(
            "management.endpoints.web.exposure.include",
            "management.endpoint.env.show-values",
            "management.tracing.sampling.probability",
            "management.tracing.enabled",
            "management.zipkin.tracing.endpoint",
            "spring.datasource.url",
            "spring.h2.console.enabled",
            "spring.h2.console.path",
            "spring.h2.console.settings.web-allow-others"
        );
        propertyKeys.forEach { println(" $it  = ${environment.getProperty(it)}") }
    }
}

data class ResponseDTO<T>(
    val data: T?,
    @JsonProperty("errors")
    val errorInfo: ErrorInfo?
)

data class IdNotFoundException(override val message: String) : Exception(message)
data class ErrorInfo(val url: String, val error: String)


@ControllerAdvice
class ExceptionHandler() {
    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(IdNotFoundException::class)
    @ResponseBody
    fun handleNotFound(request: HttpServletRequest, exception: Exception) =
        ErrorInfo(request.requestURL.toString(), exception.message ?: "Not found")

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(IllegalArgumentException::class)
    @ResponseBody
    fun handleIllegalArgumentException(request: HttpServletRequest, exception: Exception) =
        ErrorInfo(request.requestURL.toString(), exception.message ?: "Invalid request argument")
}


fun main(args: Array<String>) {
    runApplication<SpringKotlinTodoApplication>(*args)
}
