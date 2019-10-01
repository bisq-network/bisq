package bisq.core.btc.wallet;

import bisq.core.btc.exceptions.BsqChangeBelowDustException;
import bisq.core.btc.exceptions.InsufficientBsqException;
import bisq.core.btc.exceptions.TransactionVerificationException;
import bisq.core.btc.exceptions.WalletException;
import bisq.core.btc.setup.WalletsSetup;
import bisq.core.dao.DaoKillSwitch;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.unconfirmed.UnconfirmedBsqChangeOutputListService;
import bisq.core.provider.fee.FeeService;
import bisq.core.user.Preferences;
import bisq.core.util.ParsingUtils;

import org.bitcoinj.core.Coin;
import org.bitcoinj.params.UnitTestParams;
import org.bitcoinj.utils.MonetaryFormat;
import org.bitcoinj.wallet.Wallet;

import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BsqWalletServiceTest {
    @Test(expected = InsufficientBsqException.class)
    public void throwsExceptionWhenFundsAreInsufficient() throws TransactionVerificationException, WalletException, BsqChangeBelowDustException, InsufficientBsqException {
        WalletsSetup walletsSetup = mock(WalletsSetup.class);
        UnitTestParams params = new UnitTestParams();
        Wallet bsqWallet = new Wallet(params);
        when(walletsSetup.getBsqWallet()).thenReturn(bsqWallet);
        when(walletsSetup.getParams()).thenReturn(params);
        FeeService feeService = mock(FeeService.class);
        DaoStateService daoStateService = mock(DaoStateService.class);
        BsqCoinSelector bsqSelector = new BsqCoinSelector(daoStateService, mock(UnconfirmedBsqChangeOutputListService.class));
        BsqWalletService bsqWalletService = new BsqWalletService(walletsSetup, bsqSelector, mock(NonBsqCoinSelector.class), daoStateService, mock(UnconfirmedBsqChangeOutputListService.class), mock(Preferences.class), feeService, mock(DaoKillSwitch.class));
        bsqWalletService.setupComplete();
        Coin receiverAmount = ParsingUtils.parseToCoin("10", MonetaryFormat.BTC);


        bsqWalletService.getPreparedSendBsqTx(bsqWallet.freshReceiveAddress().toString(), receiverAmount);
    }

}
