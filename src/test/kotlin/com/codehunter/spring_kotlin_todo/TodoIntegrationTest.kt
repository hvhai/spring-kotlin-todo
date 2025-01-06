package com.codehunter.spring_kotlin_todo

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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

    @AfterEach
    fun tearDown() {
        todoRepository.deleteAll()
    }

    @Test
    fun `given valid data when create note called then return new note`() {
        // when
        val actual: ResponseEntity<ResponseDTO<Todo>> =
            restTemplate.exchange(
                "/api/todos",
                HttpMethod.POST,
                HttpEntity(CreateNoteRequest("new note")),
                typeReference<ResponseDTO<Todo>>()
            )
        // then
        // verify response
        assertNotNull(actual)
        assertEquals(HttpStatusCode.valueOf(201), actual.statusCode)

        val newTodo = actual.body?.data
        assertNotNull(newTodo?.id)
        assertEquals("new note", newTodo!!.note)
        assertFalse(newTodo!!.isDone)

        // verify database
        val allNotes = todoRepository.findAll()
        assertEquals(1, allNotes.size)
        val newTodoEntity = allNotes.get(0)
        assertNotNull(newTodoEntity?.id)
        assertEquals("new note", newTodoEntity.note)
        assertFalse(newTodoEntity.isDone)
    }

    @Test
    fun `given existent note when get note by id then return note`() {
        // given
        val existentNote = todoRepository.save<TodoEntity>(TodoEntity(null, "note", true))
        val id = existentNote.id
        val expectedNote = Todo(id!!, "note", true)

        // when
        val actual: ResponseEntity<ResponseDTO<Todo>> =
            restTemplate.exchange(
                "/api/todos/$id",
                HttpMethod.GET,
                null,
                typeReference<ResponseDTO<Todo>>()
            )
        // then
        // verify response
        assertNotNull(actual)
        assertEquals(HttpStatusCode.valueOf(200), actual.statusCode)
        val newTodo = actual.body?.data
        assertEquals(expectedNote, newTodo)
    }

    @Test
    fun `given 2 existent notes when get all notes then return 2 notes`() {
        // given
        val notes = todoRepository.saveAll<TodoEntity>(
            listOf<TodoEntity>(
                TodoEntity(null, "note1", true),
                TodoEntity(null, "note2", false),
            )
        )
        // when
        val actual: ResponseEntity<ResponseDTO<List<Todo>>> =
            restTemplate.exchange(
                "/api/todos",
                HttpMethod.GET,
                null,
                typeReference<ResponseDTO<List<Todo>>>()
            )
        // then
        // verify response
        assertNotNull(actual)
        assertEquals(HttpStatusCode.valueOf(200), actual.statusCode)
        val newTodo = actual.body?.data
        assertEquals(notes.map { it.toDomain() }.sortedBy { it.note }, newTodo)
    }

    @Test
    fun `given existent note when update note by id then return updated note`() {
        // given
        val existentNote = todoRepository.save<TodoEntity>(TodoEntity(null, "note", true))
        val id = existentNote.id
        val expectedNote = Todo(id!!, "updated note", true)

        // when
        val actual: ResponseEntity<ResponseDTO<Todo>> =
            restTemplate.exchange(
                "/api/todos",
                HttpMethod.PATCH,
                HttpEntity(UpdateNoteRequest(id, "updated note")),
                typeReference<ResponseDTO<Todo>>()
            )
        // then
        // verify response
        assertNotNull(actual)
        assertEquals(HttpStatusCode.valueOf(200), actual.statusCode)
        val newTodo = actual.body?.data
        assertEquals(expectedNote, newTodo)

        // verify database
        val updatedNoteEntity = todoRepository.findByIdOrNull(id)
        assertNotNull(updatedNoteEntity)
        assertEquals(TodoEntity(id, "updated note", true), updatedNoteEntity)
    }

    @Test
    fun `given existent note when mark not as done by id then return updated note`() {
        // given note with isDone = false
        val existentNote = todoRepository.save<TodoEntity>(TodoEntity(null, "note", false))
        val id = existentNote.id
        val expectedNote = Todo(id!!, "note", true)

        // when
        val actual: ResponseEntity<ResponseDTO<Todo>> =
            restTemplate.exchange(
                "/api/todos/$id/done",
                HttpMethod.PATCH,
                null,
                typeReference<ResponseDTO<Todo>>()
            )
        // then
        // verify response
        assertNotNull(actual)
        assertEquals(HttpStatusCode.valueOf(200), actual.statusCode)
        val newTodo = actual.body?.data
        assertEquals(expectedNote, newTodo)

        // verify database
        val updatedNoteEntity = todoRepository.findByIdOrNull(id)
        assertNotNull(updatedNoteEntity)
        assertEquals(TodoEntity(id, "note", true), updatedNoteEntity)
    }

    @Test
    fun `given existent note when delete note by id then note is deleted with empty response`() {
        // given
        val existentNote = todoRepository.save<TodoEntity>(TodoEntity(null, "note", true))
        val id = existentNote.id

        // when
        val actual: ResponseEntity<ResponseDTO<Todo>> =
            restTemplate.exchange(
                "/api/todos/$id",
                HttpMethod.DELETE,
                null,
                typeReference<ResponseDTO<Todo>>()
            )
        // then
        // verify response
        assertNotNull(actual)
        assertEquals(HttpStatusCode.valueOf(204), actual.statusCode)

        // verify database
        val updatedNoteEntity = todoRepository.findByIdOrNull(id)
        assertNull(updatedNoteEntity)
    }
}
