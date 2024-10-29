package com.github.navikt.tbd_libs.speed

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.navikt.tbd_libs.azure.AzureToken
import com.github.navikt.tbd_libs.azure.AzureTokenProvider
import com.github.navikt.tbd_libs.mock.MockHttpResponse
import com.github.navikt.tbd_libs.mock.bodyAsString
import com.github.navikt.tbd_libs.speed.IdentResponse
import com.github.navikt.tbd_libs.speed.SpeedClient
import com.github.navikt.tbd_libs.speed.SpeedException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.http.HttpClient
import java.time.LocalDateTime
import java.util.UUID
import kotlin.jvm.optionals.getOrNull

class SpeedClientTest {
    private companion object {
        private val objectMapper = jacksonObjectMapper()
            .registerModule(JavaTimeModule())
            .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
    }

    @Test
    fun `hent fnr og aktørId`() {
        utveksle("testident") { body ->
            body.hasNonNull("ident") && body.path("ident").asText() == "testident"
        }
    }

    @Test
    fun `hent fnr og aktørId - feil`() {
        val (speedClient, httpClient) = mockClient(errorResponse, 404)
        assertThrows<SpeedException> { speedClient.hentFødselsnummerOgAktørId("testident") }.also {
            assertEquals("Feil fra Speed: noe gikk galt", it.message)
        }
        verifiserPOST(httpClient)
    }

    private fun utveksle(ident: String, verifisering: (body: JsonNode) -> Boolean) {
        val (speedClient, httpClient) = mockClient(okResponse)

        val response = speedClient.hentFødselsnummerOgAktørId(ident)

        verifiserPOST(httpClient)
        verifiserRequestBody(httpClient, verifisering)
        assertEquals(IdentResponse(
            fødselsnummer = "fnr",
            aktørId = "aktørId",
            npid = null,
            kilde = IdentResponse.KildeResponse.CACHE
        ), response)
    }

    private fun mockClient(response: String, statusCode: Int = 200): Pair<SpeedClient, HttpClient> {
        val httpClient = mockk<HttpClient> {
            every {
                send<String>(any(), any())
            } returns MockHttpResponse(response, statusCode)
        }
        val tokenProvider = object : AzureTokenProvider {
            override fun onBehalfOfToken(scope: String, token: String): AzureToken {
                return AzureToken("on_behalf_of_token", LocalDateTime.now())
            }

            override fun bearerToken(scope: String): AzureToken {
                return AzureToken("bearer_token", LocalDateTime.now())
            }
        }
        val speedClient = SpeedClient(httpClient, objectMapper, tokenProvider)
        return speedClient to httpClient
    }

    fun verifiserPOST(httpClient: HttpClient) {
        verifiserRequestMethod(httpClient, "POST")
    }

    fun verifiserGET(httpClient: HttpClient) {
        verifiserRequestMethod(httpClient, "GET")
    }

    fun verifiserRequestMethod(httpClient: HttpClient, method: String) {
        verify {
            httpClient.send<String>(match { request ->
                request.method().uppercase() == method.uppercase()
            }, any())
        }
    }

    fun verifiserRequestHeader(httpClient: HttpClient, headerName: String, verifisering: (String?) -> Boolean) {
        verify {
            httpClient.send<String>(match { request ->
                verifisering(request.headers().firstValue(headerName).getOrNull())
            }, any())
        }
    }

    private fun verifiserRequestBody(httpClient: HttpClient, verifisering: (body: JsonNode) -> Boolean) {
        verify {
            httpClient.send<String>(match { request ->
                verifisering(objectMapper.readTree(request.bodyAsString()))
            }, any())
        }
    }

    @Language("JSON")
    private val errorResponse = """{
  "feilmelding": "noe gikk galt",
  "callId": "${UUID.randomUUID()}"
}"""

    @Language("JSON")
    private val okResponse = """{
    "fødselsnummer": "fnr",
    "aktørId": "aktørId",
    "npid": null,
    "kilde": "CACHE"
}"""
}