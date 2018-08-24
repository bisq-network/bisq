package bisq.httpapi.model;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class Balances {
    public long availableBalance;
    public long reservedBalance;
    public long lockedBalance;
}
