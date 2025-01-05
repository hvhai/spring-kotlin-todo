package com.codehunter.spring_kotlin_todo

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

abstract class ContainerBaseTest {
    companion object {
        @Container
        @JvmStatic
        val mySQLContainer = MySQLContainer(DockerImageName.parse("mysql:8.0.33")).withReuse(true)

        @DynamicPropertySource
        @JvmStatic
        fun mySqlProperties(registry: DynamicPropertyRegistry) {
//            registry.add("spring.datasource.url" ){
//                "${mySQLContainer.jdbcUrl.replace("127.0.0.1", "localhost")}?allowPublicKeyRetrieval=true&useSSL=false"
//            }
            registry.add("spring.datasource.url", mySQLContainer::getJdbcUrl)
            registry.add("spring.datasource.username", mySQLContainer::getUsername)
            registry.add("spring.datasource.password", mySQLContainer::getPassword)
        }
    }
}

@Testcontainers
@SpringBootTest(
    classes = arrayOf(SpringKotlinTodoApplication::class),
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("integration")
class TodoIntegrationTest : ContainerBaseTest() {
    @Autowired
    lateinit var restTemplate: TestRestTemplate

    @Autowired
    lateinit var todoRepository: TodoRepository

    @Test
    fun `given valid data when create note called then return new note`() {
        val actual = restTemplate.postForEntity<Todo>("/api/todos", CreateNoteRequest("new note"), Todo::class.java)
        assertNotNull(actual)

        val allNotes = todoRepository.findAll()
        assertEquals(1, allNotes.size)
    }

}