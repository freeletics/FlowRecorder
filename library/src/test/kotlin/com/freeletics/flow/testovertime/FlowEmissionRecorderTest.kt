package com.freeletics.flow.testovertime

import io.reactivex.Observable
import io.reactivex.ObservableEmitter
import io.reactivex.schedulers.TestScheduler
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import org.junit.Assert
import org.junit.Ignore
import org.junit.Test
import java.lang.AssertionError

/**
 * Tests for [FlowEmissionRecorder]
 */
class FlowEmissionRecorderTest {

    @Test
    fun `recorded and checked successfully`() {
        val emissions = flowOf(1, 2)
            .record()

        emissions shouldEmitNext 1
        emissions shouldEmitNext 2
    }


    @Test
    fun `recorded and comparison fails`() {
        val emissions = flowOf(1, 2)
            .record()

        emissions shouldEmitNext 1

        try {
            emissions shouldEmitNext 3 // 2 is actually expected
            Assert.fail("AssertionError expected")
        } catch (e: AssertionError) {
            // TODO should we check for a certain message?
        }
    }

    @Test
    fun `late emissions are forwarded and checked`() {

        val emissions = flow {
            emit(1)
            delay(500)
            emit(2)
            delay(500)
            emit(3)
            delay(2000)
        }.record()

        emissions shouldEmitNext 1
        emissions shouldEmitNext 2
        emissions shouldEmitNext 3


    }
}
