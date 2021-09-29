package bisq.asset.tokens;

import bisq.asset.Erc20Token;

public class TetherUSDERC20 extends Erc20Token {
    public TetherUSDERC20() {
        // If you add a new USDT variant or want to change this ticker symbol you should also look here:
        // core/src/main/java/bisq/core/provider/price/PriceProvider.java:getAll()
        super("Tether USD (ERC20)", "USDT-E");
    }
}
