package bisq.httpapi.model;

import bisq.core.btc.model.AddressEntry;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@NoArgsConstructor
@AllArgsConstructor
public class WalletAddress {

    public String address;

    public long balance;

    public int confirmations;

    public AddressEntry.Context context;

    public String offerId;

}
