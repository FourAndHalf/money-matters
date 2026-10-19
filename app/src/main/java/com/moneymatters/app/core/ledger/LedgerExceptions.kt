package com.moneymatters.app.core.ledger

class UnbalancedTransactionException(message: String) : IllegalArgumentException(message)

class IllegalLifecycleTransitionException(message: String) : IllegalStateException(message)
