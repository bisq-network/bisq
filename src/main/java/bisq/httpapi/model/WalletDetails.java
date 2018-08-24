package bisq.httpapi.model;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class WalletDetails {

    public long availableBalance;
    public long reservedBalance;
    public long lockedBalance;

}
