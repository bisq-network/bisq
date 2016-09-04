package io.bitsquare.api.api;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by mike on 02/09/16.
 */
public class WalletTransactions {
    @Setter
    List<Transaction> transactions = new ArrayList<>();

    @JsonValue

    public List<Transaction> getTransactions() {
        return transactions;
    }
}

enum TransactionType {
    SEND, RECEIVE
}

@AllArgsConstructor
class Transaction {
    long amount;
    TransactionType transactionType;
    String address;
    long timestamp;
    long confirmations;
}
