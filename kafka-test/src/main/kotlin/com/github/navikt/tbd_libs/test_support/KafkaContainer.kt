package com.github.navikt.tbd_libs.test_support

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.apache.kafka.clients.CommonClientConfigs
import org.testcontainers.DockerClientFactory
import org.testcontainers.kafka.ConfluentKafkaContainer
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import java.util.Properties
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toKotlinDuration

class KafkaContainer(
    private val appnavn: String,
    private val poolSize: Int
) {
    private companion object {
        private const val IKKE_INITIALISERT = false
        private const val ER_INITIALISERT = true
    }
    private val instance by lazy {
        ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.7.1")).apply {
            withCreateContainerCmdModifier { command -> command.withName(appnavn) }
            withReuse(true)
            withLabel("app-navn", appnavn)
            DockerClientFactory.lazyClient().apply {
                this
                    .listContainersCmd()
                    .exec()
                    .filter { it.labels["app-navn"] == appnavn }
                    .forEach {
                        killContainerCmd(it.id).exec()
                        removeContainerCmd(it.id).withForce(true).exec()
                    }
            }
            println("Starting kafka container")
            start()
        }
    }

    private val topicsInitialized = AtomicBoolean(IKKE_INITIALISERT)
    private val tilgjengeligeTopics = Channel<TestTopic>(poolSize)

    val connectionProperties by lazy {
        Properties().apply {
            println("Bootstrap servers: ${instance.bootstrapServers}")
            put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, instance.bootstrapServers)
        }
    }

    suspend fun nyTopic(timeout: Duration = Duration.ofSeconds(20)): TestTopic {
        return nyeTopics(1, timeout).single()
    }

    suspend fun nyeTopics(antall: Int, timeout: Duration = Duration.ofSeconds(40)): List<TestTopic> {
        check(antall <= poolSize) { "det er satt opp $poolSize topics, men $antall ønskes. det går ikke!" }
        opprettTopicsHvisIkkeInitialisert()
        return fåTopicsOrThrow(antall, timeout)
    }
    private suspend fun fåTopicsOrThrow(antall: Int, timeout: Duration): List<TestTopic> {
        return withTimeoutOrNull(timeout.toKotlinDuration()) {
            // implementerer en "alt eller ingenting"-algoritme siden det kan være begrenset
            // mengde topics tilgjengelig og at det kan være flere tråder som konkurrerer om samme ressurs (potensiell deadlock).
            // Eksempel:
            //   Bassenget består av to topics, og to tester kjører i parallell.
            //   Begge testene vil ha to topics.
            //   Begge får én topic hver, og så står begge to og venter på siste — men det er jo ingen igjen!
            // Derfor gjør vi et forsøk på å få 2 stk med en gang, eller så gir vi tilbake det vi fikk. Før eller
            // siden vil en av trådene kunne få to topics hver.
            while (isActive) {
                val claimedTopics = fåTopics(antall)
                if (claimedTopics.size == antall) return@withTimeoutOrNull claimedTopics
                droppTopics(claimedTopics)
            }
            return@withTimeoutOrNull null
        }
            ?.also {
                println("> Får topic ${it.joinToString { it.topicnavn} }")
            }
            ?: throw RuntimeException("Fikk ikke tak i $antall topics innen ${timeout.toMillis()} millisekunder")
    }

    private fun fåTopics(antall: Int): List<TestTopic> {
        // forsøker å få <antall> topics, ellers returneres det som var tilgjengelig
        return buildList {
            (1..antall).forEach {
                    tilgjengeligeTopics.tryReceive().getOrNull()?.also {
                        add(it)
                    } ?: return@buildList
            }
        }
    }

    private suspend fun opprettTopicsHvisIkkeInitialisert() {
        if (!topicsInitialized.compareAndSet(IKKE_INITIALISERT, ER_INITIALISERT)) return
        withTimeout(10.seconds) {
            repeat(poolSize) {
                tilgjengeligeTopics.send(TestTopic("test.topic.$it", connectionProperties))
            }
        }
    }

    suspend fun droppTopic(testTopic: TestTopic) {
        droppTopics(listOf(testTopic))
    }

    suspend fun droppTopics(testTopics: List<TestTopic>) {
        if (testTopics.isEmpty()) return
        println("Tilgjengeliggjør ${testTopics.joinToString { it.topicnavn }} igjen")
        try {
            testTopics.forEach { it.cleanUp() }
        } finally {
            withTimeout(20.seconds) { testTopics.forEach { tilgjengeligeTopics.send(it) } }
        }
    }
}

fun kafkaTest(kafkaContainer: KafkaContainer, testBlock: suspend TestTopic.() -> Unit) {
    runBlocking {
        val testTopic = kafkaContainer.nyTopic()
        try {
            testBlock(testTopic)
        } finally {
            kafkaContainer.droppTopic(testTopic)
        }
    }
}