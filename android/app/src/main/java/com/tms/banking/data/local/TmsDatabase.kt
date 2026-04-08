package com.tms.banking.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.tms.banking.data.local.dao.AccountDao
import com.tms.banking.data.local.dao.CategoryDao
import com.tms.banking.data.local.dao.TransactionDao
import com.tms.banking.data.local.entity.AccountEntity
import com.tms.banking.data.local.entity.CategoryEntity
import com.tms.banking.data.local.entity.TransactionEntity

@Database(
    entities = [AccountEntity::class, TransactionEntity::class, CategoryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class TmsDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        @Volatile
        private var INSTANCE: TmsDatabase? = null

        fun getInstance(context: Context): TmsDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    TmsDatabase::class.java,
                    "tms_banking.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
