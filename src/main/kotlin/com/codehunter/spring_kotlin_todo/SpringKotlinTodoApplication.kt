package com.codehunter.spring_kotlin_todo

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.annotation.PostConstruct
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.http.HttpStatus
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configurers.oauth2.server.resource.OAuth2ResourceServerConfigurer
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer


@SpringBootApplication
class SpringKotlinTodoApplication {
    @Autowired
    lateinit var environment: Environment

    @PostConstruct
    fun printConfigProperties() {
        val propertyKeys = listOf<String>(
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

@Configuration
@EnableWebSecurity
class DirectlyConfiguredJwkSetUri {
    @Bean
    @Throws(java.lang.Exception::class)
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .authorizeHttpRequests(Customizer {
                it
                    .anyRequest().authenticated()
            })
            .oauth2ResourceServer { oauth2: OAuth2ResourceServerConfigurer<HttpSecurity?> ->
                oauth2
                    .jwt(Customizer {
                        it
                            .jwkSetUri("https://dev-codehunter.auth0.com/.well-known/jwks.json")
                    })
            }
            /*
            If you use Spring MVC’s CORS support, you can omit specifying the CorsConfigurationSource
            and Spring Security uses the CORS configuration provided to Spring MVC:
            link: https://docs.spring.io/spring-security/reference/servlet/integrations/cors.html
             */
            .cors { }
        return http.build()
    }
}

@Configuration
class WebConfig : WebMvcConfigurer {
    @Bean
    fun corsConfigurer(): WebMvcConfigurer {
        return object : WebMvcConfigurer {
            override fun addCorsMappings(registry: CorsRegistry) {
                registry.addMapping("/**")
                    .allowedOrigins("*")
                    .allowedMethods("*")
            }
        }
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
