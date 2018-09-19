package bisq.httpapi.facade;

import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.WalletsManager;

import bisq.httpapi.exceptions.UnauthorizedException;
import bisq.httpapi.exceptions.WalletNotReadyException;
import bisq.httpapi.model.AuthResult;
import bisq.httpapi.service.auth.TokenRegistry;

import bisq.common.util.Tuple2;

import org.bitcoinj.crypto.KeyCrypterScrypt;

import javax.inject.Inject;

import org.spongycastle.crypto.params.KeyParameter;

public class UserFacade {

    private final BtcWalletService btcWalletService;
    private final TokenRegistry tokenRegistry;
    private final WalletsManager walletsManager;
    private final WalletFacade walletFacade;

    @Inject
    public UserFacade(BtcWalletService btcWalletService,
                      TokenRegistry tokenRegistry,
                      WalletsManager walletsManager,
                      WalletFacade walletFacade) {
        this.btcWalletService = btcWalletService;
        this.tokenRegistry = tokenRegistry;
        this.walletsManager = walletsManager;
        this.walletFacade = walletFacade;
    }

    public AuthResult authenticate(String password) {
        final boolean isPasswordValid = btcWalletService.isWalletReady() && btcWalletService.isEncrypted() && walletFacade.isWalletPasswordValid(password);
        if (isPasswordValid) {
            return new AuthResult(tokenRegistry.generateToken());
        }
        throw new UnauthorizedException();
    }

    public AuthResult changePassword(String oldPassword, String newPassword) {
        if (!btcWalletService.isWalletReady())
            throw new WalletNotReadyException("Wallet not ready yet");
        if (btcWalletService.isEncrypted()) {
            final KeyParameter aesKey = null == oldPassword ? null : walletFacade.getAESKey(oldPassword);
            if (!walletFacade.isWalletPasswordValid(aesKey))
                throw new UnauthorizedException();
            walletsManager.decryptWallets(aesKey);
        }
        if (null != newPassword && newPassword.length() > 0) {
            final Tuple2<KeyParameter, KeyCrypterScrypt> aesKeyAndScrypt = walletFacade.getAESKeyAndScrypt(newPassword);
            walletsManager.encryptWallets(aesKeyAndScrypt.second, aesKeyAndScrypt.first);
            tokenRegistry.clear();
            return new AuthResult(tokenRegistry.generateToken());
        }
        return null;
    }
}
