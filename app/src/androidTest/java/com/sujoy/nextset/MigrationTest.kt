package com.sujoy.nextset

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sujoy.nextset.data.MIGRATION_1_2
import com.sujoy.nextset.data.NextSetDatabase
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            NextSetDatabase::class.java,
            emptyList(),
            FrameworkSQLiteOpenHelperFactory(),
        )

    @Test
    fun upgradePreservesTemplatesScheduleAndSessions() {
        helper.createDatabase("migration-test", 1).apply {
            execSQL("INSERT INTO templates VALUES ('t', 'Push', 0)")
            execSQL("INSERT INTO schedule VALUES (1, 't')")
            execSQL("INSERT INTO sessions VALUES ('s', 'snapshot', 1000, 2000, 'ABANDONED')")
            close()
        }
        helper.runMigrationsAndValidate("migration-test", 2, true, MIGRATION_1_2).apply {
            query("SELECT name FROM templates WHERE id = 't'").use {
                assertTrue(it.moveToFirst())
                assertEquals("Push", it.getString(0))
            }
            query("SELECT templateId FROM schedule WHERE day = 1").use {
                assertTrue(it.moveToFirst())
                assertEquals("t", it.getString(0))
            }
            query("SELECT status FROM sessions WHERE id = 's'").use {
                assertTrue(it.moveToFirst())
                assertEquals("ABANDONED", it.getString(0))
            }
            close()
        }
    }

    @Test
    fun executionUpgradePreservesExistingHistoryAndValidatesNewSchema() {
        helper.createDatabase("execution-migration-test", 2).apply {
            execSQL("INSERT INTO templates VALUES ('t','Push',0)")
            execSQL("INSERT INTO sessions VALUES ('s','snapshot',1000,2000,'ABANDONED')")
            close()
        }
        helper
            .runMigrationsAndValidate(
                "execution-migration-test",
                3,
                true,
                com.sujoy.nextset.data.MIGRATION_2_3,
            )
            .apply {
                query("SELECT elapsedMillis, status FROM sessions WHERE id = 's'").use {
                    assertTrue(it.moveToFirst())
                    assertTrue(it.isNull(0))
                    assertEquals("ABANDONED", it.getString(1))
                }
                close()
            }
    }
}
