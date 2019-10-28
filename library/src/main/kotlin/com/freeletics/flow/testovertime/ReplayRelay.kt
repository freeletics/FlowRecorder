package com.freeletics.flow.testovertime

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.AbstractFlow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.suspendCoroutine

internal class ReplayRelay<T> : AbstractFlow<T>(), FlowCollector<T> {

    private val mutex = Mutex()
    private val collectors = ArrayList<FlowCollector<T>>()
    private val emissions = ArrayList<T>()

    private val channel = Channel<T>()
    init {

    }

    override suspend fun collectSafely(collector: FlowCollector<T>) {
        mutex.withLock {
            collectors.add(collector)
            emissions.forEach {
                collector.emit(it)
            }
        }
        channel.consumeEach {
            collector.emit(it)
        }
    }

    override suspend fun emit(value: T) {
        mutex.withLock {
            emissions.add(value)
            channel.send(value)
        }
    }
}