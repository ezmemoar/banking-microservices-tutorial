package com.ultimatesoftware.banking.account.common.events;

import java.util.UUID;

public class TransferStartedEvent {
    private UUID transactionId;
    private UUID id;
    private double balance;

    public TransferStartedEvent(UUID transactionId, UUID id, double balance) {
        this.transactionId = transactionId;
        this.id = id;
        this.balance = balance;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public UUID getId() {
        return id;
    }

    public double getBalance() {
        return balance;
    }
}
