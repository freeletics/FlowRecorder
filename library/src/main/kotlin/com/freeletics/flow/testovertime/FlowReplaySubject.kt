package com.freeletics.flow.testovertime

import kotlinx.coroutines.flow.AbstractFlow
import kotlinx.coroutines.flow.FlowCollector
import java.util.concurrent.atomic.AtomicReference

class FlowReplaySubject<T> : AbstractFlow<T>(), FlowCollector<T> {

    private companion object {
        private val EMPTY = arrayOf<InnerCollector<Any>>()
        private val TERMINATED = arrayOf<InnerCollector<Any>>()
    }

    private val buffer: Buffer<T> = UnboundedReplayBuffer()

    @Suppress("UNCHECKED_CAST")
    private val collectors = AtomicReference(emptyArray<InnerCollector<T>>())

    private var done: Boolean = false



    /**
     * Accepts a [collector] and emits the cached values upfront
     * and any subsequent value received by this ReplaySubject until
     * the ReplaySubject gets terminated.
     */
    override suspend fun collectSafely(collector: FlowCollector<T>) {
        val inner = InnerCollector(collector, this)
        add(inner)
        buffer.replay(inner)
    }

    /**
     * Emit a value to all current collectors when they are ready.
     */
    override suspend fun emit(value: T) {
        if (!done) {
            buffer.emit(value)

            for (collector in collectors.get()) {
                collector.resume()
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun emitError(ex: Throwable) {
        if (!done) {
            done = true
            buffer.error(ex)
            for (collector in collectors.getAndSet(TERMINATED as Array<InnerCollector<T>>)) {
                collector.resume()
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun complete() {
        if (!done) {
            done = true
            buffer.complete()
            for (collector in collectors.getAndSet(TERMINATED as Array<InnerCollector<T>>)) {
                collector.resume()
            }
        }
    }

    /**
     * Returns true if this PublishSubject has any collectors.
     */
    fun hasCollectors() : Boolean = collectors.get().isNotEmpty()

    /**
     * Returns the current number of collectors.
     */
    fun collectorCount() : Int = collectors.get().size

    @Suppress("UNCHECKED_CAST", "")
    private fun add(inner: InnerCollector<T>) : Boolean {
        while (true) {

            val a = collectors.get()
            if (a as Any == TERMINATED as Any) {
                return false
            }
            val n = a.size
            val b = a.copyOf(n + 1)
            b[n] = inner
            if (collectors.compareAndSet(a, b as Array<InnerCollector<T>>)) {
                return true
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun remove(inner: InnerCollector<T>) {
        while (true) {
            val a = collectors.get()
            val n = a.size
            if (n == 0) {
                return
            }

            val j = a.indexOf(inner)
            if (j < 0) {
                return
            }

            var b = EMPTY as Array<InnerCollector<T>?>
            if (n != 1) {
                b = Array(n - 1) { null }
                System.arraycopy(a, 0, b, 0, j)
                System.arraycopy(a, j + 1, b, j, n - j - 1)
            }
            if (collectors.compareAndSet(a, b as Array<InnerCollector<T>>)) {
                return
            }
        }
    }

    private interface Buffer<T> {

        fun emit(value: T)

        fun error(ex: Throwable)

        fun complete()

        suspend fun replay(consumer: InnerCollector<T>)
    }

    private class InnerCollector<T>(val consumer: FlowCollector<T>, val parent: FlowReplaySubject<T>) : Resumable() {
        var index: Long = 0L
    }

    private class UnboundedReplayBuffer<T> : Buffer<T> {

        @Volatile
        private var size: Long = 0

        private val list : ArrayList<T> = ArrayList()

        @Volatile
        private var done: Boolean = false
        private var error: Throwable? = null

        override fun emit(value: T) {
            list.add(value)
            size += 1
        }

        override fun error(ex: Throwable) {
            error = ex
            done = true
        }

        override fun complete() {
            done = true
        }

        override suspend fun replay(consumer: InnerCollector<T>) {
            while (true) {

                val d = done
                val empty = consumer.index == size

                if (d && empty) {
                    val ex = error
                    if (ex != null) {
                        throw ex
                    }
                    return
                }

                if (!empty) {
                    try {
                        consumer.consumer.emit(list[consumer.index.toInt()])
                        consumer.index++
                    } catch (ex: Throwable) {
                        consumer.parent.remove(consumer)

                        throw ex
                    }
                    continue
                }

                consumer.await()
            }
        }
    }
}