package com.moneymatters.app.core.ledger

class UnbalancedTransactionException(message: String) : IllegalArgumentException(message)

class IllegalLifecycleTransitionException(message: String) : IllegalStateException(message)

/** A new entry was dated inside a month that has been closed; closed months only take prior-period adjustments. */
class ClosedPeriodException(message: String) : IllegalStateException(message)
