package com.freeletics.flow.testovertime

import kotlinx.coroutines.flow.AbstractFlow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.suspendCoroutine

internal class ReplayRelay<T> : AbstractFlow<T>(), FlowCollector<T> {

    private val mutex = Mutex()
    private val collectors = ArrayList<FlowCollector<T>>()
    private val emissions = ArrayList<T>()

    override suspend fun collectSafely(collector: FlowCollector<T>) {
        mutex.withLock {
            collectors.add(collector)
            emissions.forEach {
                collector.emit(it)
            }
        }
    }

    override suspend fun emit(value: T) {
        mutex.withLock {
            emissions.add(value)
            collectors.forEach { collector ->
                collector.emit(value)
            }
        }
    }
}