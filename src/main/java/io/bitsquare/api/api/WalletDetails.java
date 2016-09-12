package io.bitsquare.api.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
public class WalletDetails {
    @JsonProperty
    private long available_balance;
    @JsonProperty
    private long reserved_balance;
//    @JsonProperty
//    private long locked_balance;
}
