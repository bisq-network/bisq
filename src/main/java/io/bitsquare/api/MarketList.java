package io.bitsquare.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by mike on 31/08/16.
 */
public class MarketList {
    public List<Market> markets = new ArrayList<>();

    public MarketList() {
        // Jackson deserialization
    }

    public void add(String lsymbol, String rsymbol) {
        markets.add(new Market(lsymbol, rsymbol));
    }

    @JsonValue
    public List<Market> getMarkets() {
        return markets;
    }

    public void setMarkets(List<Market> markets) {
        this.markets = markets;
    }

}

class Market {
    @JsonProperty
    String pair;
    @JsonProperty
    String lsymbol;
    @JsonProperty
    String rsymbol;

    Market(String lsymbol, String rsymbol) {
        this.pair = lsymbol.toLowerCase() + "_" + rsymbol.toLowerCase();
        this.lsymbol = lsymbol;
        this.rsymbol = rsymbol;
    }
}