package bisq.asset.coins;

import bisq.asset.Base58BitcoinAddressValidator;
import bisq.asset.Coin;

public class TetherUSDOmni extends Coin {
    public TetherUSDOmni() {
        // If you add a new USDT variant or want to change this ticker symbol you should also look here:
        // core/src/main/java/bisq/core/provider/price/PriceProvider.java:getAll()
        super("Tether USD (Omni)", "USDT-O", new Base58BitcoinAddressValidator());
    }
}
