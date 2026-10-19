package com.moneymatters.app.core.ledger

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class LedgerConverters {
    @TypeConverter fun fromAccountType(value: AccountType) = value.name
    @TypeConverter fun toAccountType(value: String) = AccountType.valueOf(value)

    @TypeConverter fun fromTransactionStatus(value: TransactionStatus) = value.name
    @TypeConverter fun toTransactionStatus(value: String) = TransactionStatus.valueOf(value)
}

@Database(
    entities = [AccountEntity::class, TransactionEntity::class, PostingEntity::class, AuditLogEntity::class],
    version = 1,
)
@TypeConverters(LedgerConverters::class)
abstract class MoneyMattersDatabase : RoomDatabase() {
    abstract fun ledgerDao(): LedgerDao
}
