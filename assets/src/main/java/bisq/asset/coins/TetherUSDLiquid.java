package bisq.asset.coins;

import bisq.asset.Coin;
import bisq.asset.LiquidBitcoinAddressValidator;

public class TetherUSDLiquid extends Coin {
    public TetherUSDLiquid() {
        // If you add a new USDT variant or want to change this ticker symbol you should also look here:
        // core/src/main/java/bisq/core/provider/price/PriceProvider.java:getAll()
        super("Tether USD (Liquid)", "L-USDT", new LiquidBitcoinAddressValidator());
    }
}
