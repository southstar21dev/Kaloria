package dev.southstar21.kaloria.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Runs the auto-migrations against the exported schemas (added to the test assets in build.gradle.kts). */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        KaloriaDatabase::class.java,
        listOf(KaloriaDatabase.RemoveWater()),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun `migrates from 1 to 4 keeping the data`() {
        helper.createDatabase(DB, 1).use { db ->
            db.execSQL(
                """INSERT INTO foods (id, name, brand, barcode, source, sourceRef, unit, energyKcal, protein, carbohydrates,
                   sugars, fat, saturatedFat, fiber, salt, micros, servings, favorite, updatedAt, deleted, dirty)
                   VALUES ('f1', 'Oats', NULL, '123', 'Manual', NULL, 'Gram', 372, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
                   '{}', '[]', 1, 10, 0, 1)""",
            )
            db.execSQL("INSERT INTO weight_entries (id, kilograms, loggedAt, updatedAt, deleted, dirty) VALUES ('w1', 80.5, 100, 100, 0, 0)")
            db.execSQL("INSERT INTO water_entries (id, millilitres, loggedAt, updatedAt, deleted, dirty) VALUES ('wa', 250, 100, 100, 0, 0)")
            db.execSQL(
                """INSERT INTO goals (id, energyKcal, protein, carbohydrates, fat, fiber, waterMl, steps, micros, updatedAt, deleted, dirty)
                   VALUES ('g1', 2000, NULL, NULL, NULL, NULL, 2500, 9000, '{}', 5, 0, 0)""",
            )
        }

        // Validates the result against schemas/.../4.json, so the dropped water table and column are checked too.
        helper.runMigrationsAndValidate(DB, 4, true).use { db ->
            db.query("SELECT name, barcode, favorite FROM foods WHERE id = 'f1'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Oats", c.getString(0))
                assertEquals("123", c.getString(1))
                assertEquals(1, c.getInt(2))
            }
            db.query("SELECT kilograms, healthConnectId FROM weight_entries WHERE id = 'w1'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(80.5, c.getDouble(0), 0.0)
                assertTrue(c.isNull(1))
            }
            db.query("SELECT energyKcal, steps FROM goals WHERE id = 'g1'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(2000, c.getInt(0))
                assertEquals(9000, c.getInt(1))
            }
            db.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'water_entries'").use { c ->
                assertFalse(c.moveToFirst())
            }
        }
    }

    @Test
    fun `each version step validates`() {
        helper.createDatabase(DB, 1).close()
        helper.runMigrationsAndValidate(DB, 2, true).close()
        helper.runMigrationsAndValidate(DB, 3, true).close()
        helper.runMigrationsAndValidate(DB, 4, true).close()
    }

    private companion object {
        const val DB = "migration-test"
    }
}
