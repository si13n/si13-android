package com.si13.app

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskDatabaseMigrationTest {
    @Test
    fun migrationFourToFivePreservesLegacyTaskAndAddsDefaults() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-4-5-${System.nanoTime()}.db"
        open(context, name, 4, create = true).use { helper ->
            helper.writableDatabase.execSQL(
                "INSERT INTO tasks (id,text,completed,createdAt,updatedAt,priority,dueDate,listName) VALUES ('old','Legacy',0,1,2,NULL,NULL,'Personal')"
            )
        }
        open(context, name, 5, create = false).use { helper ->
            helper.writableDatabase.query("SELECT text,note,repeatRule,listName FROM tasks WHERE id='old'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("Legacy", cursor.getString(0))
                assertEquals("", cursor.getString(1))
                assertEquals("none", cursor.getString(2))
                assertEquals("Personal", cursor.getString(3))
            }
        }
        context.deleteDatabase(name)
    }

    private fun open(context: Context, name: String, version: Int, create: Boolean): SupportSQLiteOpenHelper {
        return FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object : SupportSQLiteOpenHelper.Callback(version) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    if (create) db.execSQL("CREATE TABLE IF NOT EXISTS tasks (id TEXT NOT NULL PRIMARY KEY, text TEXT NOT NULL, completed INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, priority TEXT, dueDate TEXT, listName TEXT NOT NULL DEFAULT 'Personal')")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                    TaskDatabase.MIGRATION_4_5.migrate(db)
                }
            }).build()
        )
    }
}
