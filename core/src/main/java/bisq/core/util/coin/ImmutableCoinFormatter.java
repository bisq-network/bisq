package bisq.core.util.coin;

import bisq.core.util.FormattingUtils;

import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.MonetaryFormat;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;

import static bisq.core.util.ParsingUtils.parseToCoin;

@Slf4j
public class ImmutableCoinFormatter implements CoinFormatter {

    // We don't support localized formatting. Format is always using "." as decimal mark and no grouping separator.
    // Input of "," as decimal mark (like in german locale) will be replaced with ".".
    // Input of a group separator (1,123,45) lead to an validation error.
    // Note: BtcFormat was intended to be used, but it lead to many problems (automatic format to mBit,
    // no way to remove grouping separator). It seems to be not optimal for user input formatting.
    @Getter
    private final MonetaryFormat monetaryFormat;

    public ImmutableCoinFormatter(MonetaryFormat coinFormat) {
        this.monetaryFormat = coinFormat;
    }

    public String formatCoin(Coin coin) {
        return formatCoin(coin, -1);
    }

    @NotNull
    public String formatCoin(Coin coin, int decimalPlaces) {
        return formatCoin(coin, decimalPlaces, false, 0);
    }

    public String formatCoin(Coin coin, int decimalPlaces, boolean decimalAligned, int maxNumberOfDigits) {
        return FormattingUtils.formatCoin(coin, decimalPlaces, decimalAligned, maxNumberOfDigits, monetaryFormat);
    }

    public String formatCoinWithCode(Coin coin) {
        return FormattingUtils.formatCoinWithCode(coin, monetaryFormat);
    }

    public String formatCoinWithCode(long value) {
        return FormattingUtils.formatCoinWithCode(Coin.valueOf(value), monetaryFormat);
    }
}
