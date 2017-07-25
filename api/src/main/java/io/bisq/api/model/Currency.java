package io.bisq.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Currency {
    @JsonProperty
    public String symbol;
    @JsonProperty
    public String name;
    @JsonProperty
    public String type;

    Currency(String symbol, String name, String type) {
        this.symbol = symbol;
        this.name = name;
        this.type = type;
    }
}
