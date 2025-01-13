package com.codehunter.spring_kotlin_todo

import com.codehunter.spring_kotlin_todo.todo.*
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.http.Request
import com.github.tomakehurst.wiremock.http.Response
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import com.nimbusds.jose.Algorithm
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ApplicationEvent
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.event.ContextClosedEvent
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.*
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WiremockInitializer : ApplicationContextInitializer<ConfigurableApplicationContext> {
    val log = LoggerFactory.getLogger(this::class.java)

    companion object {
//        @JvmStatic
//        @RegisterExtension
//        val wiremockServer = WireMockExtension.newInstance()
//            .options(wireMockConfig().dynamicPort().dynamicHttpsPort())
//            .configureStaticDsl(true)
//            .build();
    }

    override fun initialize(applicationContext: ConfigurableApplicationContext) {
        val wiremockServer = WireMockServer(WireMockConfiguration.options().dynamicPort())
        wiremockServer.addMockServiceRequestListener(::requestReceived)
        wiremockServer.start()
        applicationContext.beanFactory.registerSingleton("wiremockServer", wiremockServer)
        applicationContext.addApplicationListener({ event: ApplicationEvent ->
            if (event is ContextClosedEvent) {
                wiremockServer.stop()
                log.info("WireMock server stopped")
            }
        })
        TestPropertyValues.of(
            mapOf(
                "spring.security.oauth2.resourceserver.jwt.jwk-set-uri" to "${wiremockServer.baseUrl()}/jwks.json",
            )
        ).applyTo(applicationContext)
    }

    fun requestReceived(
        inRequest: Request,
        inResponse: Response
    ) {
        log.info("WireMock request at URL: {}", inRequest.absoluteUrl)
        log.info("WireMock request headers: \n{}", inRequest.headers)
        log.info("WireMock request body: \n{}", inRequest.bodyAsString)
        log.info("WireMock response body: \n{}", inResponse.bodyAsString)
        log.info("WireMock response headers: \n{}", inResponse.headers)
    }
}

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

        // init mock authentication
        val rsaKey = RSAKeyGenerator(2048)
            .keyUse(KeyUse.SIGNATURE)
            .expirationTime(Date(Date().time + 60 * 1000))
            .algorithm(Algorithm("RS256"))
            .keyID("1234")
            .generate();
        val token = getSignedJwt()

        private fun getSignedJwt(): String {
            val signer = RSASSASigner(rsaKey)
            val claimsSet = JWTClaimsSet.Builder()
                .expirationTime(Date(Date().time + 60 * 1000))
                .claim("http://coundowntimer.com/roles", listOf("user"))
                .issuer("https://dev-codehunter.auth0.com/")
                .subject("auth0|604a3194414b5e007020aacd")
                .audience("https://dev-codehunter.auth0.com/api/v2/")
                .claim(
                    "scope",
                    "read:current_user update:current_user_metadata delete:current_user_metadata create:current_user_metadata create:current_user_device_credentials delete:current_user_device_credentials update:current_user_identities"
                )
                .claim("gty", "password")
                .claim("azp", "sNYVOrixNb0ZyE0WZnxvurbuOYTmX9SK")
                .build()
            val signedJWT = SignedJWT(
                JWSHeader.Builder(JWSAlgorithm.RS256)
                    .keyID(rsaKey!!.keyID).build(), claimsSet
            )
            signedJWT.sign(signer)
            return signedJWT.serialize()
        }
    }
}

@Testcontainers
@SpringBootTest(
    classes = arrayOf(SpringKotlinTodoApplication::class),
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ContextConfiguration(initializers = arrayOf(WiremockInitializer::class))
@ActiveProfiles("integration")
class TodoIntegrationTest : ContainerBaseTest() {
    @Autowired
    lateinit var restTemplate: TestRestTemplate

    @Autowired
    lateinit var todoRepository: TodoRepository

    @Autowired
    lateinit var wiremockServer: WireMockServer

    @AfterEach
    fun tearDown() {
        todoRepository.deleteAll()
    }

    companion object {

    }

    @BeforeEach
    fun setup() {
        mockAuthenticate()
    }

    private fun mockAuthenticate(): StubMapping? {
        val rsaPublicJWK = rsaKey.toPublicJWK();
        val jwkResponse = "{\"keys\": [" +
                rsaPublicJWK.toJSONString() +
                "]}"

        // return mock JWK response
        return wiremockServer.stubFor(
            WireMock.get("/jwks.json")
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody(jwkResponse)
                )
        )
    }


    @Test
    fun `given valid data when create note called then return new note`() {
        // when
        val actual: ResponseEntity<ResponseDTO<Todo>> =
            restTemplate.exchange(
                "/api/todos",
                HttpMethod.POST,
                HttpEntity(
                    CreateNoteRequest("new note"),
                    HttpHeaders().apply {
                        setBearerAuth(token)
                    }),
                typeReference<ResponseDTO<Todo>>()
            )
        // then
        // verify response
        assertNotNull(actual)
        assertEquals(HttpStatusCode.valueOf(201), actual.statusCode)

        val newTodo = actual.body?.data
        assertNotNull(newTodo?.id)
        assertEquals("new note", newTodo?.note)
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
                HttpEntity(
                    null,
                    HttpHeaders().apply {
                        setBearerAuth(token)
                    }),
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
                HttpEntity(
                    null,
                    HttpHeaders().apply {
                        setBearerAuth(token)
                    }),
                typeReference<ResponseDTO<List<Todo>>>()
            )
        // then
        // verify response
        assertNotNull(actual)
        assertEquals(HttpStatusCode.valueOf(200), actual.statusCode)
        val newTodo = actual.body?.data
        assertEquals(notes.map { it.toDomain() }.sortedBy { it.note }, newTodo?.sortedBy { it.note })
    }

    @Test
    fun `given existent note when update note by id then return updated note`() {
//        val mockAuth = mockAuthenticate()
        // given
        val existentNote = todoRepository.save<TodoEntity>(TodoEntity(null, "note", true))
        val id = existentNote.id
        val expectedNote = Todo(id!!, "updated note", true)

        // when
        val actual: ResponseEntity<ResponseDTO<Todo>> =
            restTemplate.exchange(
                "/api/todos",
                HttpMethod.PATCH,
                HttpEntity(
                    UpdateNoteRequest(id, "updated note"),
                    HttpHeaders().apply {
                        setBearerAuth(token)
                    }),
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
                HttpEntity(
                    null,
                    HttpHeaders().apply {
                        setBearerAuth(token)
                    }),
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
                HttpEntity(
                    null,
                    HttpHeaders().apply {
                        setBearerAuth(token)
                    }),
                typeReference<ResponseDTO<Todo>>()
            )
        // then
        // verify response
        assertNotNull(actual)
        assertEquals(HttpStatusCode.valueOf(204), actual.statusCode)

        // verify database
        val updatedNoteEntity = todoRepository.findByIdOrNull(id)
        assertNull(updatedNoteEntity)
        wiremockServer.removeStub(WireMock.get("/jwks.json"))
    }
}
