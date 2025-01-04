package com.codehunter.spring_kotlin_todo

import org.springframework.boot.fromApplication
import org.springframework.boot.with


fun main(args: Array<String>) {
	fromApplication<SpringKotlinTodoApplication>().with(TestcontainersConfiguration::class).run(*args)
}
