package com.freeletics.flow.testovertime

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert
import org.junit.Test
import kotlin.system.measureTimeMillis

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

        emissions.stopRecordingAndCleanUp()
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
            val expectedMessage = "expected:<[1, 3]> but was:<[1, 2]>"
            if (e.message != expectedMessage) {
                throw e
            } else {
                Assert.assertEquals(expectedMessage, e.message)
            }
        } finally {
            emissions.stopRecordingAndCleanUp()
        }
    }

    @Test
    fun `deferred emissions are forwarded and checked`() {

        val emissions = flow {
            emit(1)
            delay(50)
            emit(2)
            delay(50)
            emit(3)
            delay(50)
            emit(4)
        }.record()

        emissions shouldEmitNext 1
        emissions shouldEmitNext 2
        emissions.shouldEmitNext(3, 4)

        emissions.stopRecordingAndCleanUp()
    }

    @Test
    fun `deferred wrong emission causes failure`() {

        val emissions = flow {
            emit(1)
            delay(50)
            emit(9)
            delay(50)
            emit(5)
            delay(50)
            emit(4)
        }.record()

        try {
            emissions shouldEmitNext 1
            emissions shouldEmitNext 2
            Assert.fail("AssertionError expected")
        } catch (e: AssertionError) {
            val expectedMessage = "expected:<[1, 2]> but was:<[1, 9]>"
            if (e.message != expectedMessage) {
                throw e
            } else {
                Assert.assertEquals(expectedMessage, e.message)
            }
        }
    }

    @Test
    fun `check only a subset of deferred emissions works and doesnt wait for all to complete`() {

        val longDelay = 10000L
        val timeElapsed = measureTimeMillis {
            val emissions = flow {
                emit(1)
                delay(50)
                emit(2)
                delay(longDelay)
                emit(3)
            }.record()

            emissions.shouldEmitNext(1, 2)
            emissions.stopRecordingAndCleanUp()
        }
        Assert.assertTrue(
            "Test execution took too long, most likely because internally something was waiting until last " +
                    "emission and completion. That is not how it's suppose to work.",
            timeElapsed < longDelay
        )
    }


    @Test
    fun `too long taking emission in flow triggers timeout`() {
        val emissions = flow<Int> {
            emit(1)
            delay(1000)
            emit(2)
        }.record(
            emissionTimeoutMilliseconds = 50
        )

        emissions shouldEmitNext 1
        try {
            emissions shouldEmitNext 2
            Assert.fail("Timeout expected")
        } catch (e: AssertionError) {
            Assert.assertEquals(
                "Waiting for [2] but no new emission within 50ms. " +
                        "Emissions so far: [1]", e.message
            )
        } finally {
            emissions.stopRecordingAndCleanUp()
        }
    }

    @Test
    fun `empty flow causes comparison to fail`() {
        val emission1 = emptyFlow<Int>()
            .record()
        try {
            emission1 shouldEmitNext 1
            Assert.fail("Exception expected")
        } catch (e: AssertionError) {
            Assert.assertEquals("expected:<[1]> but was:<[]>", e.message)
        } finally {
            emission1.stopRecordingAndCleanUp()
        }

        val emission2 = flow<Int> {}
            .record()
        try {
            emission2 shouldEmitNext 1
            Assert.fail("Exception expected")
        } catch (e: AssertionError) {
            Assert.assertEquals("expected:<[1]> but was:<[]>", e.message)
        } finally {
            emission2.stopRecordingAndCleanUp()
        }
    }

    @Test
    fun `more verification after clea up throws Exception`() {
        val emission = flowOf(1, 2)
            .record()

        emission shouldEmitNext 1
        emission.stopRecordingAndCleanUp()

        try {
            emission shouldEmitNext 2
            Assert.fail("Exception expected")
        } catch (e: IllegalStateException) {
            val expectedMessage = ".cleanUp() already called. No more assertions allowed."
            Assert.assertEquals(expectedMessage, e.message)
        }
    }
}
