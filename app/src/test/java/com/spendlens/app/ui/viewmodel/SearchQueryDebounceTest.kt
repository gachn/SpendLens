package com.spendlens.app.ui.viewmodel

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies that the debounced search query pattern behaves correctly:
 *   - displayQuery (raw StateFlow) updates immediately on every keystroke
 *   - the debounced query (used for filtering the list) only emits after 300ms of no new input
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SearchQueryDebounceTest {

    @Test
    fun `displayQuery updates immediately on each keystroke`() = runTest {
        val rawQuery = MutableStateFlow("")
        val displayQuery: StateFlow<String> = rawQuery.asStateFlow()

        rawQuery.value = "a"
        assertEquals("a", displayQuery.value)

        rawQuery.value = "ab"
        assertEquals("ab", displayQuery.value)

        rawQuery.value = "abc"
        assertEquals("abc", displayQuery.value)
    }

    @Test
    fun `debouncedQuery does not emit while typing within debounce window`() = runTest {
        val rawQuery = MutableStateFlow("")
        val emitted = mutableListOf<String>()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            rawQuery.debounce(300L).collect { emitted.add(it) }
        }

        rawQuery.value = "a"
        advanceTimeBy(100L)
        rawQuery.value = "ab"
        advanceTimeBy(100L)
        rawQuery.value = "abc"
        // Only 200ms have elapsed since last keystroke — debounce window not reached
        advanceTimeBy(200L)

        // The debounced value "abc" must not have been emitted yet
        assertFalse("abc should not be emitted within debounce window", emitted.contains("abc"))

        job.cancel()
    }

    @Test
    fun `debouncedQuery emits after 300ms of inactivity`() = runTest {
        val rawQuery = MutableStateFlow("")
        val emitted = mutableListOf<String>()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            rawQuery.debounce(300L).collect { emitted.add(it) }
        }

        rawQuery.value = "a"
        advanceTimeBy(100L)
        rawQuery.value = "ab"
        advanceTimeBy(100L)
        rawQuery.value = "abc"
        // Advance past the 300ms debounce window
        advanceTimeBy(301L)

        assertTrue("abc should be emitted after debounce window", emitted.contains("abc"))

        job.cancel()
    }

    @Test
    fun `displayQuery and debouncedQuery match after debounce settles`() = runTest {
        val rawQuery = MutableStateFlow("")
        val displayQuery: StateFlow<String> = rawQuery.asStateFlow()
        val emitted = mutableListOf<String>()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            rawQuery.debounce(300L).collect { emitted.add(it) }
        }

        rawQuery.value = "hello"
        advanceTimeBy(301L)

        assertEquals("hello", displayQuery.value)
        assertEquals("hello", emitted.last())

        job.cancel()
    }

    @Test
    fun `clearing the query updates displayQuery immediately`() = runTest {
        val rawQuery = MutableStateFlow("some text")
        val displayQuery: StateFlow<String> = rawQuery.asStateFlow()

        rawQuery.value = ""

        assertEquals("", displayQuery.value)
    }
}
