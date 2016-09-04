package io.bitsquare.api.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
public class WalletDetails {
    @Setter
    @JsonProperty
    private long balance;
}
