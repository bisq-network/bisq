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
    List<WalletTransaction> transactions = new ArrayList<>();

    @JsonValue

    public List<WalletTransaction> getTransactions() {
        return transactions;
    }
}

// TODO see enum in addressentry,
enum TransactionType {
    SEND, RECEIVE
}

