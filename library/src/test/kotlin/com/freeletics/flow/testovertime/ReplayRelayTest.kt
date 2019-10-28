package com.freeletics.flow.testovertime

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import org.junit.Assert
import org.junit.Test

class ReplayRelayTest {


    @UseExperimental(InternalCoroutinesApi::class)
    @Test
    fun `completed flow gets replayed`() {
        val replay = ReplayRelay<Int>()
        val expected = listOf(1, 2, 3)

        val original = GlobalScope.async {
            withContext(Dispatchers.IO) {
                flowOf(1, 2, 3).collect(replay)
            }
        }

        runBlocking {
            original.await()

            launch {
                val emissions1 = replay.toList()
                Assert.assertEquals(expected, emissions1)
            }

            launch {
                val emissions2 = replay.toList()
                Assert.assertEquals(expected, emissions2)
            }
        }
    }


    @UseExperimental(InternalCoroutinesApi::class)
    @Test
    fun `a late observer gets all previous emissions and the new ones`() {

        val replay = ReplayRelay<Int>()
        val expected = listOf(1, 2, 3, 4)

        val original = GlobalScope.launch {
            flow {
                println("Emitting 1")
                emit(1)
                delay(100)
                println("Emitting 2")
                emit(2)
                delay(100)
                println("Emitting 3")
                emit(3)
                println("Emitting 4")
                emit(4)
            }.collect(replay)
        }



        runBlocking {
            launch(Dispatchers.Unconfined) {
                delay(70) // Misses the first emission
                val emissions1 = replay.toList()
                Assert.assertEquals(expected, emissions1)
            }

            launch(Dispatchers.IO) {
                delay(150)  // Misses the first and second emission
                val emissions2 = replay.toList()
                Assert.assertEquals(expected, emissions2)
            }
        }
    }
}