package network.bisq.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Currency {
    @JsonProperty
    public String code;
    @JsonProperty
    public String name;
    @JsonProperty
    public String type;

    public Currency() {
    }

    Currency(String code, String name, String type) {
        this.code = code;
        this.name = name;
        this.type = type;
    }
}
