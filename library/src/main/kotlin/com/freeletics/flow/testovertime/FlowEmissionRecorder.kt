package com.freeletics.flow.testovertime

import kotlinx.collections.immutable.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert

/**
 * This class records emissions of Flow type.
 *
 * Use Flow's extension function [record] to create an instance of this class.
 *
 * Use [shouldEmitNext] to do assertions. Internally we use .equals() to check if the emission is
 * the expected one.
 *
 * Use [cleanUp] once you are done with recording and verifying to clean up resources to avoid
 * memory leaks.
 *
 */
class FlowEmissionRecorder<T> internal constructor(
    flowToObserve: Flow<T>,
    private val timeoutMilliseconds: Long,
    coroutineScopeToLaunchFlowIn: CoroutineScope
) {

    private enum class Emission {
        NEXT_EMISSION_RECEIVED,
        NO_MORE_EMISSIONS
    }

    // TODO mutex actually doesnt work the way we thought it is. It's only
    //  mutal exclusive to a given lambda block but not as a semaphore to an entire lock.
    //  we need to reverify if this lock is actually needed in combincation with PersistenList
    //  or can be removed entirely.
    private val mutex = Mutex()
    private val lock = Any()
    private var verifiedEmissions: PersistentList<T> = persistentListOf()
    private var recordedEmissions: PersistentList<T> = persistentListOf()
    private val channel: ReceiveChannel<Emission>
    private lateinit var observingFlowJob: Job
    private var doCheckJob: Job? = null
    private var cleanedUp = false

    init {
        // TODO verify if we should move this to actor.
        channel = coroutineScopeToLaunchFlowIn.produce {
            observingFlowJob = launch {
                flowToObserve
                    .collect { emission ->
                        mutex.withLock(lock) {
                            recordedEmissions = recordedEmissions.add(emission)
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
        if (cleanedUp) {
            throw IllegalStateException(".cleanUp() already called. No more assertions allowed.")
        }
        runBlocking {
            doCheckJob = launch {
                val expectedEmissions = mutex.withLock(lock) {
                    verifiedEmissions + nextEmissions
                }

                do {
                    mutex.withLock(lock) {
                        if (expectedEmissions.size <= recordedEmissions.size) {
                            val actualEmissions: PersistentList<T> =
                                recordedEmissions.subList(0, expectedEmissions.size).toPersistentList()
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
                        val actualEmissions = recordedEmissions.subList(0, expectedEmissions.size).toPersistentList()
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
     * It uses `.equals()` to check if an emission matches the expected emission.
     */
    infix fun shouldEmitNext(emission: T) {
        doChecks(listOf(emission))
    }

    /**
     * Checks if the passed emissions are the given one (or waits for the next emissions or fails with a timeout)
     * It uses `.equals()` to check if an emission matches the expected emission.
     */
    fun shouldEmitNext(vararg emissions: T) {
        doChecks(emissions.toList())
    }


    /**
     * Call this to do clean up and release resources once you are done with
     * your assertions and verifications.
     *
     * You must call this to avoid running out of memory if you have tons and tons of tests going
     * on at the same time
     */
    fun cleanUp() {
        cleanedUp = true
        observingFlowJob.cancel()
        doCheckJob?.cancel()
        channel.cancel()
        verifiedEmissions = verifiedEmissions.clear()
        recordedEmissions = recordedEmissions.clear()
    }
}

/**
 * Starts subscribing / collecting the given Flow and records it's emission to verify them later
 *
 * @see FlowEmissionRecorder
 */
fun <T> Flow<T>.record(
    emissionTimeoutMilliseconds: Long = 5000,
    coroutineScopeToLaunchFlowIn: CoroutineScope = GlobalScope
): FlowEmissionRecorder<T> =
    FlowEmissionRecorder(
        flowToObserve = this,
        timeoutMilliseconds = emissionTimeoutMilliseconds,
        coroutineScopeToLaunchFlowIn = coroutineScopeToLaunchFlowIn
    )