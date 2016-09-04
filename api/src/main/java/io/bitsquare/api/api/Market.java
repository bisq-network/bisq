package io.bitsquare.api.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Market {
    @JsonProperty
    String pair;
    @JsonProperty
    String lsymbol;
    @JsonProperty
    String rsymbol;

    public Market(String lsymbol, String rsymbol) {
        this.pair = lsymbol.toLowerCase() + "_" + rsymbol.toLowerCase();
        this.lsymbol = lsymbol;
        this.rsymbol = rsymbol;
    }
}
