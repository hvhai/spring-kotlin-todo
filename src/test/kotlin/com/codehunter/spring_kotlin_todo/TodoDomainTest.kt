package com.codehunter.spring_kotlin_todo

import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

@ExtendWith(MockKExtension::class)
class TodoDomainTest {
    val todoRepository: TodoRepository = mockk()
    val todoManager: TodoManager = TodoManager(todoRepository)

    @Test
    fun `given valid data when create new note should new note return with id`() {
        // given
        val requestedTodoEntity = slot<TodoEntity>()
        every { todoRepository.save<TodoEntity>(capture(requestedTodoEntity)) }.returns(TodoEntity("id", "note", false))

        // when
        val actual = todoManager.createNewNote("note")

        // then
        assertEquals(Todo("id", "note", false), actual)
        verify(exactly = 1) { todoRepository.save<TodoEntity>(any()) }

        assertNull(requestedTodoEntity.captured.id)
        assertEquals("note", requestedTodoEntity.captured.note)
        assertFalse { requestedTodoEntity.captured.isDone}
    }
}