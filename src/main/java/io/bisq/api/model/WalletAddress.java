package io.bisq.api.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import bisq.core.btc.AddressEntry;
import lombok.AllArgsConstructor;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@AllArgsConstructor
public class WalletAddress {

    public String address;

    public long balance;

    public int confirmations;

    public AddressEntry.Context context;

    public String offerId;

}
