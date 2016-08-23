package io.bitsquare.gui.main.offer.createoffer.monetary;

import org.bitcoinj.core.Coin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.math.BigInteger;

import static com.google.common.base.Preconditions.checkArgument;

// Cloned from ExchangeRate. Use Altcoin instead of Fiat.
public class AltcoinExchangeRate implements Serializable {
    private static final Logger log = LoggerFactory.getLogger(AltcoinExchangeRate.class);

    /**
     * An exchange rate is expressed as a ratio of a {@link Coin} and a {@link Altcoin} amount.
     */

    public final Coin coin;
    public final Altcoin altcoin;

    /**
     * Construct exchange rate. This amount of coin is worth that amount of altcoin.
     */
    public AltcoinExchangeRate(Coin coin, Altcoin altcoin) {
        checkArgument(coin.isPositive());
        checkArgument(altcoin.isPositive());
        checkArgument(altcoin.currencyCode != null, "currency code required");
        this.coin = coin;
        this.altcoin = altcoin;
    }

    /**
     * Construct exchange rate. One coin is worth this amount of altcoin.
     */
    public AltcoinExchangeRate(Altcoin altcoin) {
        this(Coin.COIN, altcoin);
    }

    /**
     * Convert a coin amount to a altcoin amount using this exchange rate.
     *
     * @throws ArithmeticException if the converted altcoin amount is too high or too low.
     */
    public Altcoin coinToAltcoin(Coin convertCoin) {
        // Use BigInteger because it's much easier to maintain full precision without overflowing.
        final BigInteger converted = BigInteger.valueOf(convertCoin.value).multiply(BigInteger.valueOf(altcoin.value))
                .divide(BigInteger.valueOf(coin.value));
        if (converted.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0
                || converted.compareTo(BigInteger.valueOf(Long.MIN_VALUE)) < 0)
            throw new ArithmeticException("Overflow");
        return Altcoin.valueOf(altcoin.currencyCode, converted.longValue());
    }

    /**
     * Convert a altcoin amount to a coin amount using this exchange rate.
     *
     * @throws ArithmeticException if the converted coin amount is too high or too low.
     */
    public Coin altcoinToCoin(Altcoin convertAltcoin) {
        checkArgument(convertAltcoin.currencyCode.equals(altcoin.currencyCode), "Currency mismatch: %s vs %s",
                convertAltcoin.currencyCode, altcoin.currencyCode);
        // Use BigInteger because it's much easier to maintain full precision without overflowing.
        final BigInteger converted = BigInteger.valueOf(convertAltcoin.value).multiply(BigInteger.valueOf(coin.value))
                .divide(BigInteger.valueOf(altcoin.value));
        if (converted.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0
                || converted.compareTo(BigInteger.valueOf(Long.MIN_VALUE)) < 0)
            throw new ArithmeticException("Overflow");
        try {
            return Coin.valueOf(converted.longValue());
        } catch (IllegalArgumentException x) {
            throw new ArithmeticException("Overflow: " + x.getMessage());
        }
    }
}
