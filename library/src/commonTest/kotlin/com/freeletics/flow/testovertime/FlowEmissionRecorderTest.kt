package com.freeletics.flow.testovertime

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlin.test.DefaultAsserter
import kotlin.test.Test

/**
 * Tests for [FlowEmissionRecorder]
 */
class FlowEmissionRecorderTest {

    private fun assertEquals(expected: Any?, actual: Any?) {
        DefaultAsserter.assertEquals(null, expected, actual)
    }

    @Test
    fun `recorded_and_checked_successfully`() {
        val emissions = flowOf(1, 2)
            .record()

        emissions shouldEmitNext 1
        emissions shouldEmitNext 2

        emissions.stopRecordingAndCleanUp()
    }


    @Test
    fun `recorded_and_comparison_fails`() {
        val emissions = flowOf(1, 2)
            .record()

        emissions shouldEmitNext 1

        try {
            emissions shouldEmitNext 3 // 2 is actually expected
            DefaultAsserter.fail("AssertionError expected")
        } catch (e: AssertionError) {
            val expectedMessage = "expected:<[1, 3]> but was:<[1, 2]>"
            if (e.message != expectedMessage) {
                throw e
            } else {
                assertEquals(expectedMessage, e.message)
            }
        } finally {
            emissions.stopRecordingAndCleanUp()
        }
    }

    @Test
    fun `deferred_emissions_are_forwarded_and_checked`() {

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
    fun `deferred_wrong_emission_causes_failure`() {

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
            DefaultAsserter.fail("AssertionError expected")
        } catch (e: AssertionError) {
            val expectedMessage = "expected:<[1, 2]> but was:<[1, 9]>"
            if (e.message != expectedMessage) {
                throw e
            } else {
                assertEquals(expectedMessage, e.message)
            }
        }
    }

    /*
    @Test
    fun `check_only_a_subset_of_deferred_emissions_works_and_doesnt_wait_for_all_to_complete`() {

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
     */


    @Test
    fun `too_long_taking_emission_in_flow_triggers_timeout`() {
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
            DefaultAsserter.fail("Timeout expected")
        } catch (e: AssertionError) {
            assertEquals(
                "Waiting for [2] but no new emission within 50ms. " +
                        "Emissions so far: [1]", e.message
            )
        } finally {
            emissions.stopRecordingAndCleanUp()
        }
    }

    @Test
    fun `empty_flow_causes_comparison_to_fail`() {
        val emission1 = emptyFlow<Int>()
            .record()
        try {
            emission1 shouldEmitNext 1
            DefaultAsserter.fail("Exception expected")
        } catch (e: AssertionError) {
            assertEquals("expected:<[1]> but was:<[]>", e.message)
        } finally {
            emission1.stopRecordingAndCleanUp()
        }

        val emission2 = flow<Int> {}
            .record()
        try {
            emission2 shouldEmitNext 1
            DefaultAsserter.fail("Exception expected")
        } catch (e: AssertionError) {
            assertEquals("expected:<[1]> but was:<[]>", e.message)
        } finally {
            emission2.stopRecordingAndCleanUp()
        }
    }

    @Test
    fun `more_verification_after_cleanup_throws_Exception`() {
        val emission = flowOf(1, 2)
            .record()

        emission shouldEmitNext 1
        emission.stopRecordingAndCleanUp()

        try {
            emission shouldEmitNext 2
            DefaultAsserter.fail("Exception expected")
        } catch (e: IllegalStateException) {
            val expectedMessage = ".cleanUp() already called. No more assertions allowed."
            assertEquals(expectedMessage, e.message)
        }
    }
}
