package com.moneymatters.app.feature.books

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymatters.app.core.ledger.LedgerRepository
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Streams the ledger into [BooksState]; rebuilds after every write. Null until the first read lands. */
class BooksViewModel(
    ledger: LedgerRepository,
    monthStartDay: () -> Int,
    today: () -> LocalDate = LocalDate::now,
) : ViewModel() {
    val books: StateFlow<BooksState?> = ledger.snapshot()
        .map { buildBooks(it, today(), monthStartDay()) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
