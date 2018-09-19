package bisq.httpapi.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@EqualsAndHashCode
@Getter
public class Market {
    @JsonProperty
    String pair;
    @JsonProperty
    String lsymbol; // baseCurrencyCode // TODO @bernard should we rename lsymbol?
    @JsonProperty
    String rsymbol; // counterCurrencyCode // TODO @bernard should we rename rsymbol?

    public Market(String lsymbol, String rsymbol) {
        this.lsymbol = lsymbol.toUpperCase();
        this.rsymbol = rsymbol.toUpperCase();
        this.pair = this.lsymbol + "_" + this.rsymbol;
    }

    public Market(String marketPair) {
        this(marketPair.split("_")[0], marketPair.split("_")[1]);
    }
}
