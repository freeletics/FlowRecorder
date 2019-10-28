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
 * Tests for [FlowEmissionRecorder]
 */
class FlowEmissionRecorderTest {

    @Test
    fun `emissions recorded and checked`() {
        val emissions = flowOf(1, 2)
            .record()

        emissions shouldEmitNext 1
        emissions shouldEmitNext 2
    }
}
