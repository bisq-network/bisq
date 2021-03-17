package bisq.core.util.coin;

import org.bitcoinj.core.Coin;

public interface CoinFormatter {
    String formatCoin(Coin coin);

    String formatCoin(Coin coin, boolean appendCode);

    String formatCoin(Coin coin, int decimalPlaces);

    String formatCoin(Coin coin, int decimalPlaces, boolean decimalAligned, int maxNumberOfDigits);

    String formatCoinWithCode(Coin coin);

    String formatCoinWithCode(long value);

    org.bitcoinj.utils.MonetaryFormat getMonetaryFormat();
}
