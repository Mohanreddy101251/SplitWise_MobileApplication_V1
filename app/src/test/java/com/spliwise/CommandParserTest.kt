package com.spliwise

import com.spliwise.voice.CommandParser
import com.spliwise.voice.CommandParser.Error
import com.spliwise.voice.CommandParser.Expense
import com.spliwise.voice.CommandParser.Split
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandParserTest {
    private fun exp(s: String) = CommandParser.parse(s) as Expense
    private fun split(s: String) = CommandParser.parse(s).also { assertTrue("$s -> $it", it is Split) } as Split
    private fun err(s: String) = CommandParser.parse(s).also { assertTrue("$s -> $it", it is Error) } as Error

    @Test fun expenses() {
        val cases = mapOf(
            "grocery 100 rupees" to ("grocery" to 10000L),
            "Grocery 100 rupees" to ("grocery" to 10000L),
            "100 for petrol" to ("petrol" to 10000L),
            "petrol ₹500" to ("petrol" to 50000L),
            "spent 250 on food" to ("food" to 25000L),
            "swiggy 349" to ("food" to 34900L),
            "uber 180.50" to ("travel" to 18050L),
            "haircut two hundred" to ("salon" to 20000L),
            "saloon 150" to ("salon" to 15000L),
            "gym 1500" to ("gym" to 150000L),
            "milk fifty" to ("grocery" to 5000L),
            "SIP 5000" to ("invest" to 500000L),
            "investment 2k" to ("invest" to 200000L),
            "electricity bill 1,250" to ("bills" to 125000L),
            "mobile recharge 299" to ("bills" to 29900L),
            "movie 400" to ("fun" to 40000L),
            "medicine two fifty" to ("health" to 25000L),
            "rent 15 thousand" to ("rent" to 1500000L),
            "shopping 1.5k" to ("shopping" to 150000L),
            "salary 50000" to ("income" to 5000000L),
            "fruits one fifty five" to ("grocery" to 15500L),
            "add lunch 120" to ("food" to 12000L),
            "200rs auto" to ("travel" to 20000L),
            "two thousand five hundred shopping" to ("shopping" to 250000L),
            "friend transfer 500" to ("friends" to 50000L),
        )
        cases.forEach { (said, want) ->
            val e = exp(said)
            assertEquals(said, want.first, e.category)
            assertEquals(said, want.second, e.amount)
        }
    }

    @Test fun expenseNotes() {
        assertEquals("Swiggy", exp("swiggy 349").note)
        assertEquals("", exp("grocery 100").note)
    }

    @Test fun splitToN() {
        val s = split("split 100 to 3")
        assertEquals(listOf(3334L, 3333L, 3333L), s.shares)
        assertEquals(10000L, s.total)
        assertEquals(listOf("Person 1", "Person 2", "Person 3"), s.labels)
    }

    @Test fun splitNamed() {
        val s = split("split 300 with mohan ravi raju")
        assertEquals(listOf("Mohan", "Ravi", "Raju"), s.names)
        assertEquals(listOf(10000L, 10000L, 10000L), s.shares)
    }

    @Test fun splitEach() {
        val s = split("mohan ravi raju split them 100 each")
        assertEquals(30000L, s.total)
        assertEquals(listOf(10000L, 10000L, 10000L), s.shares)
    }

    @Test fun splitWithMe() {
        val s = split("split 400 between me ravi and raju")
        assertTrue(s.includeMe)
        assertEquals(listOf("Me", "Ravi", "Raju"), s.labels)
        assertEquals(40000L, s.shares.sum())
    }

    @Test fun splitCountImpliesMe() {
        val s = split("split 400 to 4 with mohan ravi raju")
        assertTrue(s.includeMe)
        assertEquals(listOf(10000L, 10000L, 10000L, 10000L), s.shares)
    }

    @Test fun splitWords() {
        val s = split("split three hundred between three people")
        assertEquals(listOf(10000L, 10000L, 10000L), s.shares)
        assertEquals(listOf(5000L, 5000L), split("split dinner 100 2 ways").shares)
        assertEquals("Dinner", split("split dinner 100 2 ways").title)
    }

    @Test fun splitErrors() {
        assertTrue(err("split 300 to 5 with ravi raju").message.contains("5 people"))
        assertTrue(err("split 300 ravi raju 100 each").message.contains("you said"))
        assertTrue(err("split ravi raju").message.contains("How much"))
        assertTrue(err("split 300").message.contains("how many"))
        assertTrue(err("split 100 to 1").message.contains("2 people"))
    }

    @Test fun expenseErrors() {
        assertTrue(err("grocery").message.contains("amount"))
        assertTrue(err("500").message.contains("category"))
        assertTrue(err("").message.isNotBlank())
    }

    @Test fun numberWords() {
        assertEquals("250", CommandParser.wordsToDigits("two fifty"))
        assertEquals("2500", CommandParser.wordsToDigits("two thousand five hundred"))
        assertEquals("25", CommandParser.wordsToDigits("twenty five"))
        assertEquals("1500", CommandParser.wordsToDigits("fifteen hundred"))
        assertEquals("150", CommandParser.wordsToDigits("one hundred and fifty"))
        assertEquals("200000", CommandParser.wordsToDigits("two lakh"))
    }
}
