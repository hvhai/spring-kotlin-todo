package com.codehunter.spring_kotlin_todo

import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules
import org.springframework.modulith.docs.Documenter


class SpringKotlinTodoModuleTest {
    private val modules = ApplicationModules.of(SpringKotlinTodoApplication::class.java)
    @Test
    fun `should be able to run test`() {
        modules.verify()
    }

    @Test
    fun writeDocumentationSnippets() {
        Documenter(modules)
            .writeModulesAsPlantUml()
            .writeIndividualModulesAsPlantUml()
            .writeAggregatingDocument()
    }
}