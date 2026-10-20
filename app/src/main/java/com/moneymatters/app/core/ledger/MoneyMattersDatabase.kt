package com.moneymatters.app.core.ledger

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class LedgerConverters {
    @TypeConverter fun fromAccountType(value: AccountType) = value.name
    @TypeConverter fun toAccountType(value: String) = AccountType.valueOf(value)

    @TypeConverter fun fromTransactionStatus(value: TransactionStatus) = value.name
    @TypeConverter fun toTransactionStatus(value: String) = TransactionStatus.valueOf(value)

    @TypeConverter fun fromAccountKind(value: AccountKind?) = value?.name
    @TypeConverter fun toAccountKind(value: String?) = value?.let(AccountKind::valueOf)

    @TypeConverter fun fromDecisionKind(value: DecisionKind?) = value?.name
    @TypeConverter fun toDecisionKind(value: String?) = value?.let(DecisionKind::valueOf)

    @TypeConverter fun fromPlanKind(value: PlanKind?) = value?.name
    @TypeConverter fun toPlanKind(value: String?) = value?.let(PlanKind::valueOf)

    @TypeConverter fun fromPaymentMethod(value: PaymentMethod?) = value?.name
    @TypeConverter fun toPaymentMethod(value: String?) = value?.let(PaymentMethod::valueOf)
}

@Database(
    entities = [AccountEntity::class, TransactionEntity::class, PostingEntity::class, AuditLogEntity::class],
    version = 2,
    autoMigrations = [
        // v2: account kind; decision, plan and payment-method tags on transactions. All nullable.
        AutoMigration(from = 1, to = 2),
    ],
)
@TypeConverters(LedgerConverters::class)
abstract class MoneyMattersDatabase : RoomDatabase() {
    abstract fun ledgerDao(): LedgerDao
}
