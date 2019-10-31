# Flow Emission Recorder

This library helps to record emissions of kotlin `Flow` type over time.
No need to use `runTestBlocking { ... }` or apply any synchronized dispatchers. 

What this library will do is: 
1. Launch your `Flow` that you want to test.
2. Keep recording all emissions of the `Flow` under test.
3. If it takes some time to get the emissions because the `Flow` runs async operations that take some time to execute, the test will stop and wait for the emission to come (or until a timeout happens).

    ```kotlin
    @Test
    fun shouldEmit_1_2_3(){
        val flow = flow {
            emit(1)
            emit(2)
            delay(1000)
            emit(3)
        }
        
        val emissions = flow.record() // launches the flow and starts recording
        
        emissions shouldEmitNext 1
        emissions shouldEmitNext 2
        emissions shouldEmitNext 3 // although this emission happens later, the test execution will wait here until next emission (or fails with timeout)
    }
    ```

4. But it works also the other way around: if the emissions are faster than the test thread for whatever reason:

    ```kotlin
    @Test
    fun shouldEmit_1_2_3(){
        val flow = flow {
            emit(1)
            emit(2)
            emit(3)
        }
        
        val emissions = flow.record() // launches the flow and starts recording
        
        emissions shouldEmitNext 1
        // for whatever reason test thread would take longer, 
        // we don't miss any emissions that happened async. in the meantime. 
        Thread.sleep(1000)
        emissions shouldEmitNext 2
        emissions shouldEmitNext 3 
    }
    ```

## Customization

```kotlin
@Test
fun shouldEmit_1_2_3(){
    val flow = flow {
        emit(1)
        emit(2)
        emit(3)
    }
    
    val emissions = flow.record(
        emissionTimeoutMilliseconds = 10_000, // specify timeout
        coroutineScopeToLaunchFlowIn = MyCoroutineScope // specify scope to launch Flow in
    )
    
    emissions.shouldEmitNext(1,2,3) // you can also use this instead of infix
}
```


## Multiplatform
Coming soon.

## Get it

On maven central:

```gradle
implementation '"'com.freeletics.flow.test:record:0.1.0'
```

## Snapshot
Latest development snapshot (whatever is on master is published as snapshot):

```gradle
implementation 'com.freeletics.flow.test:record:0.1.1-SNAPSHOT'
```