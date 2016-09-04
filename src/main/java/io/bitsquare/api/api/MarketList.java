package io.bitsquare.api.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

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

