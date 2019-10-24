package com.freeletics.flow.testovertime

import io.reactivex.Observable
import io.reactivex.ObservableEmitter
import io.reactivex.schedulers.TestScheduler
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import org.junit.Assert
import org.junit.Ignore
import org.junit.Test

/**
 * Tests for [RxEmissionHistoryTestObserver]
 */
class FlowEmissionHistoryTest {

    @Test
    fun `emissions recorded and checked`() {
        val emissions = flowOf(1, 2)
            .testOverTime()

        Assert.assertEquals(listOf(1, 2), emissions.replayHistory!!.values.toList())

        emissions shouldEmitNext 1
        Assert.assertEquals(listOf(1), emissions.verifiedHistory)
        emissions shouldEmitNext 2

        Assert.assertEquals(listOf(1, 2), emissions.verifiedHistory)

        val emissions2 = flowOf(1, 2)
            .testOverTime()
        emissions2.shouldEmitNext(1, 2)
        Assert.assertEquals(listOf(1, 2), emissions2.verifiedHistory)
        Assert.assertEquals(emissions2.replayHistory!!.values.toList(), listOf(1, 2))
    }

    @Test
    fun `timeout throws exception`() {

        val emissions = flow {
            emit(1)
            // Doesn't emit a second item, thus should run into timeout later
        }.testOverTime(TimeoutConfig(timeout = 20, timeoutTimeUnit = TimeUnit.MILLISECONDS))

        emissions shouldEmitNext 1

        try {
            emissions shouldEmitNext 2 // never be emitted but we expect a timeout
            Assert.fail("Exception expected")
        } catch (t: RuntimeException) {
            val cause = t.cause
            if (cause is TimeoutException) {
                Assert.assertEquals(
                    "The source did not signal an event for 20 milliseconds " +
                            "and has been terminated.", cause.message
                )
            } else {
                t.printStackTrace()
                Assert.fail("TimeoutException expected but got $t")
            }
        }
    }

    @Test
    fun `on shouldEmitNoMoreValues throws exception on unchecked items left`() {
        val emissions = flowOf(1, 2, 3)
            .testOverTime()

        emissions shouldEmitNext 1

        try {
            emissions.shouldNotEmitMoreValues()
            throw RuntimeException("Exception expected")
        } catch (e: AssertionError) {
            val expMsg = "Unverified items detected that you have never checked before by calling " +
                    ".shouldEmitNext(...).\n" +
                    "Verified: [1]\n" +
                    "All emitted items: [1, 2, 3]\n" +
                    "Unverified items emitted after last .shouldEmitNext() call: [2, 3]"

            Assert.assertEquals(expMsg, e.message)
        }
    }

    /*
    @Test
    @Ignore("TODO: Add proper error handler or error verification")
    fun `on shouldEmitNoMoreValues throws exception on observable source emitting values`() {

        val testScheduler = TestScheduler()
        val emissions = Observable.interval(100, TimeUnit.MILLISECONDS, testScheduler)
            .subscribeOn(testScheduler)
            .testOverTime()

        testScheduler.advanceTimeBy(101, TimeUnit.MILLISECONDS)
        emissions shouldEmitNext 0
        emissions.shouldNotEmitMoreValues()

        try {
            testScheduler.advanceTimeBy(100, TimeUnit.MILLISECONDS)
            Assert.fail("Exception expected")
        } catch (t: IllegalStateException) {
            t.message shouldEqual "No more emissions expected because " +
                    "RxEmissionHistoryTestObserver.shouldNotEmitMoreValues() has been called but received new emission: 2"
        }
    }
     */

    /*
    @Test
    fun `shouldNotHaveEmittedSinceLastCheck detects unwanted changes `() {
        var emitter: ObservableEmitter<Int>? = null
        val emissions = Observable.create<Int> {
            emitter = it
            emitter!!.onNext(1)
        }.testOverTime()

        emissions shouldEmitNext 1
        emissions.shouldNotHaveEmittedSinceLastCheck()

        emitter!!.onNext(2)

        try {
            emissions.shouldNotHaveEmittedSinceLastCheck()
            Assert.fail("Exception expected")
        } catch (error: AssertionError) {
            error.message shouldEqual "expected:<[1[]]> but was:<[1[, 2]]>"
        }
    }
     */

    @Test
    fun `shouldNotHaveEmittedSinceLastCheck works even without any emission`() {
        flow<Int> { } // Never emits
            .testOverTime()
            .shouldNotHaveEmittedSinceLastCheck() // No error expected
    }

    @Test
    fun `once disposeAndCleanUp has been called shouldNotHaveEmittedSinceLastCheck throws exception`() {
        val emissions = flow<Int> { } // Never emits
            .testOverTime()

        emissions.disposeAndCleanUp()

        try {
            emissions.shouldNotHaveEmittedSinceLastCheck()
            Assert.fail("Exception expected")
        } catch (e: IllegalStateException) {
            Assert.assertEquals(
                "Already cleaned up. " +
                        "Not possible to call this method after .disposeAndCleanUp()", e.message
            )
        }
    }
}
