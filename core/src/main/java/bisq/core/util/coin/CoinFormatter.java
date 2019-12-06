package bisq.core.util.coin;

import org.bitcoinj.core.Coin;

import org.jetbrains.annotations.NotNull;

public interface CoinFormatter {
    String formatCoin(Coin coin);

    @NotNull
    String formatCoin(Coin coin, int decimalPlaces);

    String formatCoin(Coin coin, int decimalPlaces, boolean decimalAligned, int maxNumberOfDigits);

    String formatCoinWithCode(Coin coin);

    String formatCoinWithCode(long value);

    org.bitcoinj.utils.MonetaryFormat getMonetaryFormat();
}
