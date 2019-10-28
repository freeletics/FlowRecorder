package com.freeletics.flow.testovertime

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert

class FlowEmissionRecorder<T>(
    flowToObserve: Flow<T>,
    coroutineScope: CoroutineScope = GlobalScope
) {

    private var veriefiedEmissions: List<T> = emptyList()
    private val mutex = Mutex()
    private val recordedEmissions = ArrayList<T>()
    private var hasSubscribers: Boolean = false
    private val channel = Channel<T>(Channel.UNLIMITED)

    init {
        coroutineScope.launch {
            flowToObserve
                .collect { emission ->
                    mutex.withLock {
                        println("original collected $emission")
                        recordedEmissions.add(emission)
                        if (hasSubscribers) {
                            channel.send(emission)
                        }
                    }
                }

            // Done collecting because original Flow terminated
            println("Closing channel")
            channel.close()

        }
    }


    @ExperimentalCoroutinesApi
    private fun doChecks(nextEmissions: List<T>) {
        runBlocking {
            launch {
                val expectedEmissions = mutex.withLock {
                    veriefiedEmissions + nextEmissions
                }

                println("doChecks for $nextEmissions :")
                println("  - recorded: $recordedEmissions")
                println("  - next    : $nextEmissions")
                println("  - expected: $expectedEmissions")

                var emitted = 0
                val actualEmissions = ArrayList<T>()

                println("  - received: $actualEmissions")
                flow {
                    mutex.withLock {
                        hasSubscribers = true
                        recordedEmissions.forEach {
                            println("$nextEmissions from recordings I got $it")
                            emit(it)
                        }

                    }
                }

                    .concat(channel.consumeAsFlow().map {
                        println("from channel")
                        it
                    })
                    .take(expectedEmissions.size)  // TODO implement timeout
                    .map {
                        println("after take $it")
                        it
                    }
                    .collect {
                        println("$nextEmissions for collect I got $it")
                        actualEmissions.add(it)
                    }

                Assert.assertEquals(expectedEmissions, actualEmissions)
                mutex.withLock {
                    veriefiedEmissions = expectedEmissions
                }
            }
        }
    }

    infix fun shouldEmitNext(emission: T) {
        doChecks(listOf(emission))
    }

    fun shouldEmitNext(vararg emissions: T) {
        doChecks(emissions.toList())
    }
}

private fun <T> Flow<T>.concat(flow: Flow<T>): Flow<T> {
    val upstream = this

    return flow {
        coroutineScope {
            upstream.collect {
                emit(it)
            }

            println("Done with upstream")

            flow.collect {
                emit(it)
            }
            println("Done with downstream")
        }
    }
}

fun <T> Flow<T>.record(): FlowEmissionRecorder<T> = FlowEmissionRecorder(this)