package com.freeletics.flow.testovertime

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FlowEmissionRecorder<T>(
    flowToObserve: Flow<T>,
    coroutineScope: CoroutineScope = GlobalScope
) {

    private val recordingFlowCollector = ReplayFlow<T>()

    private val veriefiedEmissions = ArrayList<T>()
    private val readMutex = Mutex()

    init {
        coroutineScope.launch {
            flowToObserve.collect(recordingFlowCollector::onNewEmission)
        }
    }


    private fun doChecks(nextEmissions: List<T>) {
        runBlocking {
            launch {

                readMutex.withLock {
                    val recorder = recordingFlowCollector.getRecordedEmissions()
                    val expectedEmissions = veriefiedEmissions + nextEmissions


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

private class ReplayFlow<T> : Flow<T> {

    private val _recordedEmissions = ArrayList<T>()
    private val mutex = Mutex()

    internal suspend fun onNewEmission(t: T) {
        mutex.withLock {
            _recordedEmissions.add(t)
        }
    }

    /**
     * Returns an immutable and threadsafe copy of the internally recoreded emissions
     */
    internal suspend fun getRecordedEmissions(): List<T> = mutex.withLock {
        ArrayList(_recordedEmissions)
    }

    @ExperimentalCoroutinesApi
    @InternalCoroutinesApi
    override suspend fun collect(collector: FlowCollector<T>) {

        flowOf(_recordedEmissions)
            .onCompletion {

            }
            .conflate()
    }
}

fun <T> Flow<T>.record(): FlowEmissionRecorder<T> = FlowEmissionRecorder(this)