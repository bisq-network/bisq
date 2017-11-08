package io.bisq.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import org.bitcoinj.wallet.Wallet;

@AllArgsConstructor
public class WalletTransaction {
    @JsonProperty
    long version;
    @JsonProperty
    String hash;
    @JsonProperty
    String confidence;
    @JsonProperty
    String params;
    @JsonProperty
    long fee;
    @JsonProperty
    long value;
    @JsonProperty
    long valueSentToMe;
    @JsonProperty
    long valueSentFromMe;
    @JsonProperty
    String memo;

    public WalletTransaction(org.bitcoinj.core.Transaction transaction, Wallet wallet) {
        this.version = transaction.getVersion();
        this.hash = transaction.getHashAsString();
        this.confidence = transaction.getConfidence().toString();
        this.params = transaction.getParams().getId();
        this.fee = (transaction.getFee() == null) ? -1 : transaction.getFee().value;
        this.value = transaction.getValue(wallet).value;
        this.valueSentFromMe = transaction.getValueSentFromMe(wallet).value;
        this.valueSentToMe = transaction.getValueSentToMe(wallet).value;
        this.memo = transaction.getMemo();
    }
}
