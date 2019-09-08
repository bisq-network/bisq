package bisq.core.util.coin;

import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.MonetaryFormat;

public interface CoinFormatter {
    MonetaryFormat getMonetaryFormat();

    String formatCoin(Coin coin);

    String formatCoin(Coin coin, int decimalPlaces);

    String formatCoin(Coin coin, int decimalPlaces, boolean decimalAligned, int maxNumberOfDigits);

    String formatCoinWithCode(Coin coin);

    String formatCoinWithCode(long coin);

}
