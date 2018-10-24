package bisq.httpapi.model;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
public class Balances {
    public long availableBalance;
    public long reservedBalance;
    public long lockedBalance;
}
