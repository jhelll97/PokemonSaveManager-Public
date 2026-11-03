package com.example.pokemonsavemanager

import org.junit.Assert.*
import org.junit.Test

class EventScreenBoundaryTest {
    @Test fun successfulScreenRendersOnce(){
        var renders=0;var failures=0
        EventScreenBoundary.open({renders++},{failures++})
        assertEquals(1,renders);assertEquals(0,failures)
    }
    @Test fun readAndRenderFailuresAreReportedWithoutEscaping(){
        for(error in listOf(IllegalArgumentException("invalid event data"),IllegalStateException("view attachment failed"))){
            var reported:Exception?=null
            EventScreenBoundary.open({throw error},{reported=it})
            assertSame(error,reported)
            assertTrue(EventScreenBoundary.diagnostic(error).contains(error.message!!))
        }
    }
    @Test fun diagnosticsKeepOriginalAppLocation(){
        val error=IllegalStateException("event screen failed")
        error.stackTrace=arrayOf(StackTraceElement("com.example.pokemonsavemanager.MainActivity","renderGen2Events","MainActivity.kt",5500))
        assertTrue(EventScreenBoundary.diagnostic(error).contains("MainActivity.kt:5500"))
    }
}
