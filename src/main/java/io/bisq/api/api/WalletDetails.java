package io.bisq.api.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
public class WalletDetails {
    @JsonProperty
    private String available_balance;
    @JsonProperty
    private String reserved_balance;
//    @JsonProperty
//    private long locked_balance;
}
