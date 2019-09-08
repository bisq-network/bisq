package bisq.core.util.coin;

import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.MonetaryFormat;

import com.natpryce.makeiteasy.Instantiator;
import com.natpryce.makeiteasy.Property;

import org.junit.Test;

import static com.natpryce.makeiteasy.MakeItEasy.a;
import static com.natpryce.makeiteasy.MakeItEasy.make;
import static com.natpryce.makeiteasy.MakeItEasy.with;
import static org.bitcoinj.core.Coin.valueOf;
import static org.junit.Assert.assertEquals;

public class ImmutableCoinFormatterTest {
    private static final Property<org.bitcoinj.core.Coin, Long> satoshis = new Property<>();
    private static final Instantiator<Coin> Coin = lookup ->
            valueOf(lookup.valueOf(satoshis, 100000000L));
    private static final Coin oneBitcoin = make(a(Coin));

    private final CoinFormatter formatter = new ImmutableCoinFormatter(MonetaryFormat.BTC);

    @Test
    public void testFormatCoin() {
        assertEquals("1.00", formatter.formatCoin(oneBitcoin));
        assertEquals("1.0000", formatter.formatCoin(oneBitcoin, 4));
        assertEquals("1.00", formatter.formatCoin(oneBitcoin, 5));
        assertEquals("0.000001", formatter.formatCoin(make(a(Coin).but(with(satoshis, 100L)))));
        assertEquals("0.00000001", formatter.formatCoin(make(a(Coin).but(with(satoshis, 1L)))));
    }
}
