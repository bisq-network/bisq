package bisq.core.btc.wallet;

import bisq.core.btc.exceptions.BsqChangeBelowDustException;
import bisq.core.btc.exceptions.InsufficientBsqException;
import bisq.core.btc.exceptions.TransactionVerificationException;
import bisq.core.btc.exceptions.WalletException;
import bisq.core.btc.model.AddressEntryList;
import bisq.core.btc.setup.WalletsSetup;
import bisq.core.dao.DaoKillSwitch;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.unconfirmed.UnconfirmedBsqChangeOutputListService;
import bisq.core.provider.fee.FeeService;
import bisq.core.user.Preferences;
import bisq.core.util.BsqFormatter;
import bisq.core.util.ParsingUtils;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Context;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.utils.MonetaryFormat;
import org.bitcoinj.wallet.Wallet;

import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BtcWalletServiceTest {
    @Test
    public void testInstantiating() throws TransactionVerificationException, WalletException, BsqChangeBelowDustException, InsufficientBsqException {
        WalletsSetup walletsSetup = mock(WalletsSetup.class);
        MainNetParams params = new MainNetParams();
        Wallet wallet = new Wallet(params);
        wallet.freshReceiveKey();

        when(walletsSetup.getBtcWallet()).thenReturn(wallet);
        when(walletsSetup.getParams()).thenReturn(params);
        new Context(params);
        FeeService feeService = mock(FeeService.class);
        BsqFormatter formatter = new BsqFormatter();
        BtcWalletService btcWallet = new BtcWalletService(walletsSetup, mock(AddressEntryList.class), mock(Preferences.class), feeService);
        BsqWalletService bsqWalletService = new BsqWalletService(walletsSetup, mock(BsqCoinSelector.class), mock(NonBsqCoinSelector.class), mock(DaoStateService.class), mock(UnconfirmedBsqChangeOutputListService.class), mock(Preferences.class), feeService, mock(DaoKillSwitch.class));
        Coin receiverAmount = ParsingUtils.parseToCoin("10", MonetaryFormat.BTC);
//        bsqWalletService.getPreparedSendBsqTx(formatter.getAddressFromBsqAddress("B172ZxJSqFsuwTU1Cjw4cPWjMZu2R1eHW3i").toString(), receiverAmount);
    }

}
