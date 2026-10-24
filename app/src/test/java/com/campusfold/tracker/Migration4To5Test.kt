package com.campusfold.tracker

import com.campusfold.tracker.data.AppDatabase
import com.campusfold.tracker.data.entity.SplitDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class Migration4To5Test {

    @Test
    fun testMigrationVersion() {
        val migration = AppDatabase.MIGRATION_4_5
        assertEquals(4, migration.startVersion)
        assertEquals(5, migration.endVersion)
    }

    @Test
    fun testSplitDirectionEnumValues() {
        assertEquals("OWED_TO_ME", SplitDirection.OWED_TO_ME.name)
        assertEquals("OWED_BY_ME", SplitDirection.OWED_BY_ME.name)
        assertEquals(SplitDirection.OWED_TO_ME, SplitDirection.valueOf("OWED_TO_ME"))
        assertEquals(SplitDirection.OWED_BY_ME, SplitDirection.valueOf("OWED_BY_ME"))
    }

    @Test
    fun testDefaultAnchorValues() {
        val defaultOpeningBalancePaise = 0L
        val defaultOpeningOwedPaise = 0L
        val defaultOpeningAsOf = 0L
        val defaultAnchorSet = false

        assertEquals(0L, defaultOpeningBalancePaise)
        assertEquals(0L, defaultOpeningOwedPaise)
        assertEquals(0L, defaultOpeningAsOf)
        assertEquals(false, defaultAnchorSet)
    }
}
