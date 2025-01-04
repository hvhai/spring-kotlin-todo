package com.codehunter.spring_kotlin_todo

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class SpringKotlinTodoApplication

fun main(args: Array<String>) {
	runApplication<SpringKotlinTodoApplication>(*args)
}
