package com.freeletics.flow.testovertime

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert

class FlowEmissionRecorder<T> internal constructor(
    flowToObserve: Flow<T>,
    private val timeoutMilliseconds: Long,
    coroutineScopeToLaunchFlowIn: CoroutineScope
) {

    private enum class Emission {
        NEXT_EMISSION_RECEIVED,
        NO_MORE_EMISSIONS
    }

    private val mutex = Mutex()
    private val lock = Any()
    private var verifiedEmissions: List<T> = emptyList()
    private val recordedEmissions = ArrayList<T>()
    private val channel: ReceiveChannel<Emission>

    init {
        channel = coroutineScopeToLaunchFlowIn.produce {
            launch {
                flowToObserve
                    .collect { emission ->
                        mutex.withLock(lock) {
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
                val expectedEmissions = mutex.withLock(lock) {
                    verifiedEmissions + nextEmissions
                }

                do {
                    mutex.withLock(lock) {
                        if (expectedEmissions.size <= recordedEmissions.size) {
                            val actualEmissions = recordedEmissions.subList(0, expectedEmissions.size)
                            Assert.assertEquals(expectedEmissions, actualEmissions)
                            verifiedEmissions = actualEmissions
                            return@launch
                        }
                    }

                    val nextEmissionType = withTimeoutOrNull(timeoutMilliseconds) {
                        channel.receive()
                    }
                    if (nextEmissionType == null) {
                        val emissionsSoFar = mutex.withLock(lock) { ArrayList(recordedEmissions) }
                        Assert.fail(
                            "Waiting for $nextEmissions but no new emission within " +
                                    "${timeoutMilliseconds}ms. Emissions so far: $emissionsSoFar"
                        )
                    }
                } while (nextEmissionType == Emission.NEXT_EMISSION_RECEIVED)


                mutex.withLock(lock) {
                    verifiedEmissions = if (expectedEmissions.size <= recordedEmissions.size) {
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

    /**
     * Checks if the next emissions is the given one (or waits for next emission or fails with a timeout)
     */
    infix fun shouldEmitNext(emission: T) {
        doChecks(listOf(emission))
    }

    /**
     * Checks if the passed emissions are the given one (or waits for the next emissions or fails with a timeout)
     */
    fun shouldEmitNext(vararg emissions: T) {
        doChecks(emissions.toList())
    }
}

/**
 * Starts subscribing / collecting the given Flow and records it's emission to verify them later
 */
fun <T> Flow<T>.record(
    emissionTimeoutMilliseconds: Long = 5000,
    coroutineScopeToLaunchFlowIn: CoroutineScope = GlobalScope + Dispatchers.IO
): FlowEmissionRecorder<T> =
    FlowEmissionRecorder(
        flowToObserve = this,
        timeoutMilliseconds = emissionTimeoutMilliseconds,
        coroutineScopeToLaunchFlowIn = coroutineScopeToLaunchFlowIn
    )