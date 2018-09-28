package bisq.httpapi.facade;

import bisq.core.app.AppOptionKeys;
import bisq.core.arbitration.Arbitrator;
import bisq.core.arbitration.ArbitratorManager;
import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.user.User;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;

import bisq.common.app.DevEnv;
import bisq.common.crypto.KeyRing;

import org.bitcoinj.core.ECKey;

import javax.inject.Inject;
import javax.inject.Named;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;



import bisq.httpapi.exceptions.NotFoundException;
import javax.validation.ValidationException;

public class ArbitratorFacade {

    private final ArbitratorManager arbitratorManager;
    private final BtcWalletService btcWalletService;
    private final User user;
    private final P2PService p2PService;
    private final KeyRing keyRing;
    private final Boolean useDevPrivilegeKeys;

    @Inject
    public ArbitratorFacade(ArbitratorManager arbitratorManager,
                            BtcWalletService btcWalletService,
                            User user,
                            P2PService p2PService,
                            KeyRing keyRing,
                            @Named(AppOptionKeys.USE_DEV_PRIVILEGE_KEYS) Boolean useDevPrivilegeKeys) {
        this.arbitratorManager = arbitratorManager;
        this.btcWalletService = btcWalletService;
        this.user = user;
        this.p2PService = p2PService;
        this.keyRing = keyRing;
        this.useDevPrivilegeKeys = useDevPrivilegeKeys;
    }

    public void registerArbitrator(List<String> languageCodes) {
        //        TODO most of this code is dupplication of ArbitratorRegistrationViewModel.onRegister
        final String privKeyString = useDevPrivilegeKeys ? DevEnv.DEV_PRIVILEGE_PRIV_KEY : null;
        //        TODO hm, are we going to send private key over http?
        if (null == privKeyString) {
            throw new RuntimeException("Missing private key");
        }
        ECKey registrationKey = arbitratorManager.getRegistrationKey(privKeyString);
        if (null == registrationKey) {
            throw new RuntimeException("Missing registration key");
        }
        AddressEntry arbitratorDepositAddressEntry = btcWalletService.getArbitratorAddressEntry();
        String registrationSignature = arbitratorManager.signStorageSignaturePubKey(registrationKey);
        Arbitrator arbitrator = new Arbitrator(
                p2PService.getAddress(),
                arbitratorDepositAddressEntry.getPubKey(),
                arbitratorDepositAddressEntry.getAddressString(),
                keyRing.getPubKeyRing(),
                new ArrayList<>(languageCodes),
                new Date().getTime(),
                registrationKey.getPubKey(),
                registrationSignature,
                null,
                null,
                null
        );
        //        TODO I don't know how to deal with those callbacks in order to send response back
        arbitratorManager.addArbitrator(arbitrator, () -> System.out.println("Arbi registered"), message -> System.out.println("Error when registering arbi: " + message));
    }

    public Collection<Arbitrator> getArbitrators(boolean acceptedOnly) {
        if (acceptedOnly) {
            return user.getAcceptedArbitrators();
        }
        return arbitratorManager.getArbitratorsObservableMap().values();
    }

    public Collection<Arbitrator> selectArbitrator(String arbitratorAddress) {
        final Arbitrator arbitrator = getArbitratorByAddress(arbitratorAddress);
        if (null == arbitrator) {
            throw new NotFoundException("Arbitrator not found: " + arbitratorAddress);
        }
        if (!arbitratorIsTrader(arbitrator)) {
            user.addAcceptedArbitrator(arbitrator);
            user.addAcceptedMediator(ArbitratorManager.getMediator(arbitrator));
            return user.getAcceptedArbitrators();
        }
        throw new ValidationException("You cannot select yourself as an arbitrator");
    }

    public Collection<Arbitrator> deselectArbitrator(String arbitratorAddress) {
        final Arbitrator arbitrator = getArbitratorByAddress(arbitratorAddress);
        if (null == arbitrator) {
            throw new NotFoundException("Arbitrator not found: " + arbitratorAddress);
        }
        user.removeAcceptedArbitrator(arbitrator);
        user.removeAcceptedMediator(ArbitratorManager.getMediator(arbitrator));
        return user.getAcceptedArbitrators();
    }

    private Arbitrator getArbitratorByAddress(String arbitratorAddress) {
        return arbitratorManager.getArbitratorsObservableMap().get(new NodeAddress(arbitratorAddress));
    }

    private boolean arbitratorIsTrader(Arbitrator arbitrator) {
        return keyRing.getPubKeyRing().equals(arbitrator.getPubKeyRing());
    }
}
