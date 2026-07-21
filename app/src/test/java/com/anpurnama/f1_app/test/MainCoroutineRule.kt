package com.anpurnama.f1_app.test

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Sets [Dispatchers.Main] to a shared [TestDispatcher] for the duration of
 * each test and resets it afterwards.
 *
 * This is required for tests that exercise Android `ViewModel.viewModelScope`,
 * which is hard-wired to [Dispatchers.Main]. Without this rule, launches from
 * the test body are posted to the missing Main dispatcher and never run before
 * assertions.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainCoroutineRule(
    private val dispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
