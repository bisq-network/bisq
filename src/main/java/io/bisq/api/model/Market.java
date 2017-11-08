package io.bisq.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@EqualsAndHashCode
@Getter
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

    public Market(String marketPair) {
        this.pair = marketPair;
        String[] pair = marketPair.split("_");
        this.lsymbol = pair[0];
        this.rsymbol = pair[1];
    }
}
