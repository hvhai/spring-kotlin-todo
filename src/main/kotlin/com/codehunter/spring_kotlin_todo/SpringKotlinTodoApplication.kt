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

@Entity
@Table(name = "todo")
data class TodoEntity(
    @Id @GeneratedValue(strategy = GenerationType.UUID) val id: String?,
    val note: String, val isDone: Boolean
)


data class Todo(
    val id: String,
    var note: String,
    var isDone: Boolean = false
) {
    fun markDone(): Todo {
        this.isDone = true
        return this
    }

    fun updateNote(newNote: String): Todo {
        this.note = newNote
        return this
    }
}

data class ResponseDTO<T>(
    val data: T?,
    @JsonProperty("errors")
    val errorInfo: ErrorInfo?
)

data class TodoDTO(
    val id: String,
    val note: String,
    val isDone: Boolean
)

data class CreateNoteRequest(val note: String)
data class UpdateNoteRequest(val id: String, val note: String)
data class IdNotFoundException(override val message: String) : Exception(message)
data class ErrorInfo(val url: String, val error: String)

fun TodoEntity.toDomain(): Todo = Todo(id ?: throw IllegalArgumentException("is is null"), note, isDone)
fun Todo.toEntity(): TodoEntity = TodoEntity(id, note, isDone)
fun Todo.toDTO(): TodoDTO = TodoDTO(id, note, isDone)


@Repository
interface TodoRepository : JpaRepository<TodoEntity, String>


@Service
@Transactional
class TodoManager(private val todoRepository: TodoRepository) {
    fun createNewNote(note: String): Todo {
        val todo = TodoEntity(null, note, false)
        return todoRepository.save<TodoEntity>(todo).toDomain()
    }

    fun getNote(id: String): Todo {
        val todoEntity = todoRepository.findByIdOrNull(id) ?: throw IdNotFoundException("Todo with $id notfound")
        return todoEntity.toDomain()
    }

    fun getAllNote() = todoRepository.findAll().map { it.toDomain() }
    fun updateNote(id: String, newNote: String): Todo {
        val oldNote = todoRepository.findByIdOrNull(id) ?: throw IdNotFoundException("Todo with $id notfound")
        val updatedNote = oldNote.toDomain().updateNote(newNote)
        return todoRepository.save<TodoEntity>(updatedNote.toEntity()).toDomain()
    }

    fun markAsDone(id: String): Todo {
        val oldNote = todoRepository.findByIdOrNull(id) ?: throw IdNotFoundException("Todo with $id notfound")
        val updatedNote = oldNote.toDomain().markDone()
        return todoRepository.save<TodoEntity>(updatedNote.toEntity()).toDomain()
    }

    fun deleteNote(id: String) {
        todoRepository.deleteById(id)
    }
}


@RestController
@RequestMapping("/api/todos")
class TodoController(private val todoManager: TodoManager) {
    val logger = LoggerFactory.getLogger(this::class.java)

    @PostMapping
    fun createNote(@RequestBody body: CreateNoteRequest): ResponseEntity<ResponseDTO<Todo>> {
        logger.info("Create new note")
        return ResponseEntity(ResponseDTO(todoManager.createNewNote(body.note), null), HttpStatus.CREATED)
    }

    @GetMapping
    fun getAllNote(): ResponseEntity<ResponseDTO<List<Todo>>> {
        logger.info("Get all note")
        return ResponseEntity(ResponseDTO(data = todoManager.getAllNote(), errorInfo = null), HttpStatus.OK)
    }

    @GetMapping("/{id}")
    fun getNote(@PathVariable id: String): ResponseEntity<ResponseDTO<Todo>> {
        logger.info("Get note with id=$id")
        val response: ResponseDTO<Todo> = ResponseDTO(data = todoManager.getNote(id), errorInfo = null)
        return ResponseEntity<ResponseDTO<Todo>>(response, HttpStatus.OK)
    }

    @PatchMapping
    fun updateNote(@RequestBody body: UpdateNoteRequest): ResponseEntity<ResponseDTO<Todo>> {
        logger.info("Update note with id=${body.id}")
        return ResponseEntity(
            ResponseDTO(data = todoManager.updateNote(body.id, body.note), errorInfo = null),
            HttpStatus.OK
        )
    }

    @PatchMapping("/{id}/done")
    fun markNoteAsDone(@PathVariable id: String): ResponseEntity<ResponseDTO<Todo>> {
        logger.info("Mark note with id=$id as done")
        return ResponseEntity(ResponseDTO(data = todoManager.markAsDone(id), errorInfo = null), HttpStatus.OK)
    }

    @DeleteMapping("/{id}")
    fun deleteNote(@PathVariable id: String): ResponseEntity<HttpStatus> {
        logger.info("Delete note with id=$id")
        todoManager.deleteNote(id)
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build<HttpStatus>()
    }
}


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
