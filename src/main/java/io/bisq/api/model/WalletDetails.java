package io.bisq.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class WalletDetails {
    @JsonProperty
    private String available_balance;
    @JsonProperty
    private String reserved_balance;
//    @JsonProperty
//    private long locked_balance;
}
