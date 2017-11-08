package io.bisq.api.model;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

// Copied from AddressEntry
enum TransactionType {
    ARBITRATOR,

    AVAILABLE,

    OFFER_FUNDING,
    RESERVED_FOR_TRADE, //reserved
    MULTI_SIG, //locked
    TRADE_PAYOUT,

    DAO_SHARE,
    DAO_DIVIDEND
}

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


