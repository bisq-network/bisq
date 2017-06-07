package io.bitsquare.api.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class WalletAddress {
    @JsonProperty
    private String id;
    @JsonProperty
    private String paymentMethod;
    @JsonProperty
    private String address;
}
