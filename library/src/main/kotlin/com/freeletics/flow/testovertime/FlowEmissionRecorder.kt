package com.freeletics.flow.testovertime

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert

class FlowEmissionRecorder<T>(
    flowToObserve: Flow<T>,
    coroutineScope: CoroutineScope = GlobalScope
) {

    private enum class Emission {
        NEXT_EMISSION_RECEIVED,
        NO_MORE_EMISSIONS
    }

    private val mutex = Mutex()
    private var veriefiedEmissions: List<T> = emptyList()
    private val recordedEmissions = ArrayList<T>()
    private val channel: ReceiveChannel<Emission>

    init {
        channel = coroutineScope.produce {
            launch {
                flowToObserve
                    .collect { emission ->
                        mutex.withLock {
                            println("original collected $emission")
                            recordedEmissions.add(emission)
                        }
                        this@produce.send(Emission.NEXT_EMISSION_RECEIVED)
                    }

                // done with channel emissions
                this@produce.send(Emission.NO_MORE_EMISSIONS)
            }
        }
    }


    @ExperimentalCoroutinesApi
    private fun doChecks(nextEmissions: List<T>) {
        runBlocking {
            launch {
                val expectedEmissions = mutex.withLock {
                    veriefiedEmissions + nextEmissions
                }
                
                do {
                    mutex.withLock {
                        if (expectedEmissions.size <= recordedEmissions.size) {
                            val actualEmissions = recordedEmissions.subList(0, expectedEmissions.size)
                            Assert.assertEquals(expectedEmissions, actualEmissions)
                            veriefiedEmissions = actualEmissions
                            return@launch
                        }
                    }
                } while (channel.receive() == Emission.NEXT_EMISSION_RECEIVED)


                mutex.withLock {
                    veriefiedEmissions = if (expectedEmissions.size <= recordedEmissions.size) {
                        val actualEmissions = recordedEmissions.subList(0, expectedEmissions.size)
                        Assert.assertEquals(expectedEmissions, actualEmissions)
                        actualEmissions
                    } else {
                        Assert.assertEquals(expectedEmissions, recordedEmissions)
                        recordedEmissions
                    }
                }
            }
        }
    }

    private data class RecoringQueryResult<T>(internal val data: List<T>, val completed: Boolean)

    infix fun shouldEmitNext(emission: T) {
        doChecks(listOf(emission))
    }

    fun shouldEmitNext(vararg emissions: T) {
        doChecks(emissions.toList())
    }
}

fun <T> Flow<T>.record(): FlowEmissionRecorder<T> = FlowEmissionRecorder(this)