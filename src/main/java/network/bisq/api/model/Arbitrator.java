package network.bisq.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Arbitrator {

    @JsonProperty
    public String address;

    public Arbitrator() {
    }

    public Arbitrator(String address) {
        this.address = address;
    }
}
