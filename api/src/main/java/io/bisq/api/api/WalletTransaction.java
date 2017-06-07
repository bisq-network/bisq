package io.bisq.api.api;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class WalletTransaction {
    long amount;
    TransactionType transactionType;
    String address;
    long timestamp;
    long confirmations;
}
