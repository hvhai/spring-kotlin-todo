package com.codehunter.spring_kotlin_todo.ui

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping

@Controller
@RequestMapping("/")
class UIController {
    @GetMapping
    fun getIndexPage() = "index"
}