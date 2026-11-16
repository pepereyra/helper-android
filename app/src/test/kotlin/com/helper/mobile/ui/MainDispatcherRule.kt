package com.helper.mobile.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * viewModelScope corre en Dispatchers.Main: en los tests es un dispatcher de prueba con su propio reloj virtual.
 * Los ViewModels tienen ciclos que se repiten para siempre (refrescos cada pocos segundos), así que los tests no
 * usan runTest (esperaría a que terminen): avanzan el tiempo a mano con [runCurrent] y [advanceTimeBy].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule : TestWatcher() {
    val scheduler = TestCoroutineScheduler()
    val dispatcher = StandardTestDispatcher(scheduler)

    fun runCurrent() = scheduler.runCurrent()
    fun advanceTimeBy(millis: Long) {
        scheduler.advanceTimeBy(millis)
        scheduler.runCurrent()
    }

    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}
