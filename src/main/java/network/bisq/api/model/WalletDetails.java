package network.bisq.api.model;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class WalletDetails {

    public long availableBalance;
    public long reservedBalance;
    public long lockedBalance;

}
