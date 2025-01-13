package com.codehunter.spring_kotlin_todo

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@SpringBootTest(
	classes = arrayOf(SpringKotlinTodoApplication::class),
	webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ContextConfiguration(initializers = arrayOf(WiremockInitializer::class))
@ActiveProfiles("integration")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class SpringKotlinTodoApplicationTests : ContainerBaseTest() {

	@Test
	fun contextLoads() {
	}

}
