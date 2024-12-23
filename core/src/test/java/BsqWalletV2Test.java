import bisq.core.btc.wallet.BisqRegtestNetworkParams;
import bisq.core.btc.wallet.BsqCoinSelector;
import bisq.core.btc.wallet.BsqWalletV2;
import bisq.core.btc.wallet.BtcWalletV2;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.wallet.Wallet;

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

public class BsqWalletV2Test {

    @ParameterizedTest
    @MethodSource("invalidCoinsProvider")
    void sendBsqDustAndInvalidAmount(Coin amountToSend) {
        var networkParams = new BisqRegtestNetworkParams();
        var bsqWalletV2 = new BsqWalletV2(networkParams,
                mock(PeerGroup.class),
                mock(BtcWalletV2.class),
                mock(Wallet.class),
                mock(BsqCoinSelector.class));

        var exception = assertThrows(IllegalArgumentException.class, () -> {
            Address receiverAddress = mock(Address.class);
            bsqWalletV2.sendBsq(receiverAddress, amountToSend, Coin.ofSat(10));
        });

        assertTrue(exception.getMessage().contains("dust limit"),
                "BSQ wallet send dust amount. This shouldn't happen!");
    }

    static Stream<Coin> invalidCoinsProvider() {
        var networkParams = new BisqRegtestNetworkParams();
        Coin dustAmount = networkParams.getMinNonDustOutput()
                .minus(Coin.valueOf(1));
        return Stream.of(dustAmount, Coin.ofSat(0), Coin.ofSat(-1));
    }
}
