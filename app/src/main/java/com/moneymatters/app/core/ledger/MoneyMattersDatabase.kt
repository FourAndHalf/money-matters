package com.moneymatters.app.core.ledger

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.moneymatters.app.core.invest.Compounding
import com.moneymatters.app.core.invest.CorporateActionEntity
import com.moneymatters.app.core.invest.CorporateActionKind
import com.moneymatters.app.core.invest.FixedDepositEntity
import com.moneymatters.app.core.invest.FundClass
import com.moneymatters.app.core.invest.HoldingEntity
import com.moneymatters.app.core.invest.InvestDao
import com.moneymatters.app.core.invest.Instrument
import com.moneymatters.app.core.invest.LotConsumptionEntity
import com.moneymatters.app.core.invest.LotEntity
import com.moneymatters.app.core.invest.PriceEntity
import com.moneymatters.app.core.invest.PriceStatus
import com.moneymatters.app.core.invest.SipPlanEntity

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

    @TypeConverter fun fromInstrument(value: Instrument) = value.name
    @TypeConverter fun toInstrument(value: String) = Instrument.valueOf(value)

    @TypeConverter fun fromFundClass(value: FundClass?) = value?.name
    @TypeConverter fun toFundClass(value: String?) = value?.let(FundClass::valueOf)

    @TypeConverter fun fromPriceStatus(value: PriceStatus) = value.name
    @TypeConverter fun toPriceStatus(value: String) = PriceStatus.valueOf(value)

    @TypeConverter fun fromCompounding(value: Compounding) = value.name
    @TypeConverter fun toCompounding(value: String) = Compounding.valueOf(value)

    @TypeConverter fun fromCorporateActionKind(value: CorporateActionKind) = value.name
    @TypeConverter fun toCorporateActionKind(value: String) = CorporateActionKind.valueOf(value)
}

@Database(
    entities = [
        AccountEntity::class, TransactionEntity::class, PostingEntity::class, AuditLogEntity::class,
        HoldingEntity::class, LotEntity::class, LotConsumptionEntity::class, PriceEntity::class,
        FixedDepositEntity::class, SipPlanEntity::class, CorporateActionEntity::class,
    ],
    version = 3,
    autoMigrations = [
        // v2: account kind; decision, plan and payment-method tags on transactions. All nullable.
        AutoMigration(from = 1, to = 2),
        // v3: investments (holdings, lots, prices, FDs, SIP plans, corporate actions), units on postings, accounts' inNetWorth.
        AutoMigration(from = 2, to = 3),
    ],
)
@TypeConverters(LedgerConverters::class)
abstract class MoneyMattersDatabase : RoomDatabase() {
    abstract fun ledgerDao(): LedgerDao
    abstract fun investDao(): InvestDao
}
