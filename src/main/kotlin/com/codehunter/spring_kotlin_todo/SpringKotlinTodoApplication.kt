package com.codehunter.spring_kotlin_todo

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpEntity
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@SpringBootApplication
class SpringKotlinTodoApplication

@Entity
@Table(name = "todo")
data class Todo(
	@Id @GeneratedValue(strategy = GenerationType.UUID) var id:String?,
	var note:String, var isDone: Boolean = false) {
	fun markDone(): Todo {
		this.isDone = true
		return this
	}
	fun updateNote(newNote:String):Todo {
		this.note = newNote
		return this
	}
}

@Repository
interface TodoRepository: JpaRepository<Todo, String>


@Service
@Transactional
class TodoManager(private val todoRepository: TodoRepository){
	fun createNewNote(note:String):Todo  {
		val todo = Todo(null, note)
		return todoRepository.save<Todo>(todo)
	}
	fun getNote(id:String) = todoRepository.findByIdOrNull(id) ?: throw Exception("Todo with $id notfound")
	fun getAllNote() = todoRepository.findAll()
	fun updateNote(id:String, newNote:String):Todo {
		val oldNote = todoRepository.findByIdOrNull(id) ?: throw Exception("Todo with $id notfound")
		return todoRepository.save<Todo>(oldNote.updateNote(newNote))
	}
	fun markAsDone(id:String): Todo {
		val oldNote = todoRepository.findByIdOrNull(id) ?: throw Exception("Todo with $id notfound")
		return todoRepository.save<Todo>(oldNote.markDone())
	}
	fun deleteNote(id:String) {
		todoRepository.deleteById(id)
	}
}

data class CreateNoteRequest(val note:String)
data class UpdateNoteRequest(val id:String, val note:String)

@RestController
@RequestMapping("/api/todos")
class TodoController(private val todoManager: TodoManager) {
	val logger = LoggerFactory.getLogger(this::class.java)

	@PostMapping
	fun createNote(@RequestBody body: CreateNoteRequest): ResponseEntity<Todo>  {
		logger.info("Create new note")
		return ResponseEntity(todoManager.createNewNote(body.note), HttpStatus.CREATED)
	}

	@GetMapping
	fun getAllNote() : ResponseEntity<List<Todo>> {
		logger.info("Get all note")
		return ResponseEntity(todoManager.getAllNote(), HttpStatus.OK)
	}

	@GetMapping("/{id}")
	fun getNote(@PathVariable id:String): ResponseEntity<Todo> {
		logger.info("Get note with id=$id")
		return ResponseEntity(todoManager.getNote(id), HttpStatus.OK)
	}

	@PatchMapping
	fun updateNote(@RequestBody body: UpdateNoteRequest): ResponseEntity<Todo> {
		logger.info("Update note with id=${body.id}")
		return ResponseEntity(todoManager.updateNote(body.id, body.note), HttpStatus.OK)
	}

	@PatchMapping("/{id}")
	fun markNoteAsDone(@PathVariable id:String): ResponseEntity<Todo> {
		logger.info("Mark note with id=$id as done")
		return ResponseEntity(todoManager.markAsDone(id), HttpStatus.OK)
	}

	@DeleteMapping("/{id}")
	fun deleteNote(@PathVariable id:String): ResponseEntity<HttpStatus> {
		logger.info("Delete note with id=$id")
		todoManager.deleteNote(id)
		return ResponseEntity.status(HttpStatus.NO_CONTENT).build<HttpStatus>()
	}
}



fun main(args: Array<String>) {
	runApplication<SpringKotlinTodoApplication>(*args)
}
