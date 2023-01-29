package edu.oaklandcc.young.kyle1.mymemory

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

val DATABASENAME = "MemoryGame"
val TABLENAME = "HighScores"
val COL_NAME = "Name"
val COL_SCORE = "Score"
val COL_ID = "id"


class DbHelper(var context: Context?) : SQLiteOpenHelper(context, DATABASENAME, null, 1) {
    override fun onCreate(db: SQLiteDatabase?) {
        val createTable =
            "CREATE TABLE " + TABLENAME + " (" + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," + COL_NAME + " VARCHAR(256)," + COL_SCORE + " VARCHAR(256))"
        db?.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        db?.execSQL("DROP TABLE IF EXISTS " + TABLENAME)
        onCreate(db)
    }

    fun insertScore(contentValues: ContentValues): Long {
        val database = this.writableDatabase
        return database.insert(TABLENAME, null, contentValues)
    }

    @SuppressLint("Range")
    fun getScore(): Cursor {

        val db = this.readableDatabase
        val query = "Select * from $TABLENAME where $COL_SCORE = (Select MIN($COL_SCORE) from $TABLENAME)"
        return db.rawQuery(query, null)

    }

}