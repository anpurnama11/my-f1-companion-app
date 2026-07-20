package com.anpurnama.f1_app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OutcomeTest {

    @Test
    fun `Success carries the data through map`() {
        val out: Outcome<Int> = Outcome.Success(2)
        val mapped = out.map { it * 10 }
        assertTrue(mapped is Outcome.Success<Int>)
        assertEquals(20, (mapped as Outcome.Success<Int>).data)
    }

    @Test
    fun `Failure carries the message through map`() {
        val out: Outcome<Int> = Outcome.Failure("boom")
        val mapped = out.map { it * 10 }
        assertTrue(mapped is Outcome.Failure)
        assertEquals("boom", (mapped as Outcome.Failure).errorMessage)
    }

    @Test
    fun `Loading is unchanged by map`() {
        val out: Outcome<Int> = Outcome.Loading
        val mapped = out.map { it * 10 }
        assertTrue(mapped is Outcome.Loading)
    }

    @Test
    fun `dataOrNull returns data on Success, null otherwise`() {
        assertEquals(7, (Outcome.Success(7) as Outcome<Int>).dataOrNull())
        assertNull((Outcome.Failure("x") as Outcome<Int>).dataOrNull())
        assertNull((Outcome.Loading as Outcome<Int>).dataOrNull())
    }

    @Test
    fun `fold visits exactly one branch per outcome`() {
        val visits = mutableListOf<String>()
        fun visit(o: Outcome<Int>) = o.fold(
            onSuccess = { visits += "s:$it" },
            onFailure = { visits += "f:$it" },
            onLoading = { visits += "l" },
        )
        visit(Outcome.Success(1))
        visit(Outcome.Failure("e"))
        visit(Outcome.Loading)
        assertEquals(listOf("s:1", "f:e", "l"), visits)
    }

    @Test
    fun `Success and Failure compare by value`() {
        // Sanity: sealed-class value equality matters so StateFlow dedup works.
        assertEquals(Outcome.Success(1), Outcome.Success(1))
        assertEquals(Outcome.Failure("x"), Outcome.Failure("x"))
        assertTrue(Outcome.Success(1) != Outcome.Success(2))
    }
}
