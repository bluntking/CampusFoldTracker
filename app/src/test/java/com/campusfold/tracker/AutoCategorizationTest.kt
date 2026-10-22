package com.campusfold.tracker

import com.campusfold.tracker.ai.LocalAiManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class AutoCategorizationTest {

    @Test
    fun testAirIndiaCategorizationToTravel() = runBlocking {
        val category = LocalAiManager.predictCategoryForMerchant("Air India")
        assertEquals("Travel", category)
    }

    @Test
    fun testUberCategorizationToTravel() = runBlocking {
        val category = LocalAiManager.predictCategoryForMerchant("Uber India Systems")
        assertEquals("Travel", category)
    }

    @Test
    fun testZomatoCategorizationToFood() = runBlocking {
        val category = LocalAiManager.predictCategoryForMerchant("Zomato Ltd")
        assertEquals("Food & Dining", category)
    }

    @Test
    fun testApolloCategorizationToHealthcare() = runBlocking {
        val category = LocalAiManager.predictCategoryForMerchant("Apollo Pharmacy")
        assertEquals("Healthcare", category)
    }

    @Test
    fun testNetflixCategorizationToEntertainment() = runBlocking {
        val category = LocalAiManager.predictCategoryForMerchant("Netflix Entertainment")
        assertEquals("Entertainment", category)
    }
}
