package com.codehunter.spring_kotlin_todo

import jakarta.persistence.*
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*

@SpringBootApplication
class SpringKotlinTodoApplication

@Entity
@Table(name = "todo")
data class TodoEntity(
    @Id @GeneratedValue(strategy = GenerationType.UUID) val id: String?,
    val note: String, val isDone: Boolean
)


class Todo(
    val id: String,
    var note: String, var isDone: Boolean = false
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

fun TodoEntity.toDomain(): Todo = Todo(id ?: throw IllegalArgumentException("is is null"), note, isDone)
fun Todo.toEntity(): TodoEntity = TodoEntity(id, note, isDone)

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
        val todoEntity = todoRepository.findByIdOrNull(id) ?: throw Exception("Todo with $id notfound")
        return todoEntity.toDomain()
    }

    fun getAllNote() = todoRepository.findAll().map { it.toDomain() }
    fun updateNote(id: String, newNote: String): Todo {
        val oldNote = todoRepository.findByIdOrNull(id) ?: throw Exception("Todo with $id notfound")
        val updatedNote = oldNote.toDomain().updateNote(newNote)
        return todoRepository.save<TodoEntity>(updatedNote.toEntity()).toDomain()
    }

    fun markAsDone(id: String): Todo {
        val oldNote = todoRepository.findByIdOrNull(id) ?: throw Exception("Todo with $id notfound")
        val updatedNote = oldNote.toDomain().markDone()
        return todoRepository.save<TodoEntity>(updatedNote.toEntity()).toDomain()
    }

    fun deleteNote(id: String) {
        todoRepository.deleteById(id)
    }
}

data class CreateNoteRequest(val note: String)
data class UpdateNoteRequest(val id: String, val note: String)

@RestController
@RequestMapping("/api/todos")
class TodoController(private val todoManager: TodoManager) {
    val logger = LoggerFactory.getLogger(this::class.java)

    @PostMapping
    fun createNote(@RequestBody body: CreateNoteRequest): ResponseEntity<Todo> {
        logger.info("Create new note")
        return ResponseEntity(todoManager.createNewNote(body.note), HttpStatus.CREATED)
    }

    @GetMapping
    fun getAllNote(): ResponseEntity<List<Todo>> {
        logger.info("Get all note")
        return ResponseEntity(todoManager.getAllNote(), HttpStatus.OK)
    }

    @GetMapping("/{id}")
    fun getNote(@PathVariable id: String): ResponseEntity<Todo> {
        logger.info("Get note with id=$id")
        return ResponseEntity(todoManager.getNote(id), HttpStatus.OK)
    }

    @PatchMapping
    fun updateNote(@RequestBody body: UpdateNoteRequest): ResponseEntity<Todo> {
        logger.info("Update note with id=${body.id}")
        return ResponseEntity(todoManager.updateNote(body.id, body.note), HttpStatus.OK)
    }

    @PatchMapping("/{id}")
    fun markNoteAsDone(@PathVariable id: String): ResponseEntity<Todo> {
        logger.info("Mark note with id=$id as done")
        return ResponseEntity(todoManager.markAsDone(id), HttpStatus.OK)
    }

    @DeleteMapping("/{id}")
    fun deleteNote(@PathVariable id: String): ResponseEntity<HttpStatus> {
        logger.info("Delete note with id=$id")
        todoManager.deleteNote(id)
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build<HttpStatus>()
    }
}


fun main(args: Array<String>) {
    runApplication<SpringKotlinTodoApplication>(*args)
}
