package network.bisq.httpapi.model;

import bisq.core.btc.AddressEntry;

import com.fasterxml.jackson.annotation.JsonInclude;

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
