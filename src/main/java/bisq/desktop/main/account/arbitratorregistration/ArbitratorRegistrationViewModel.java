/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.desktop.main.account.arbitratorregistration;

import bisq.desktop.common.model.ActivatableViewModel;

import bisq.core.arbitration.Arbitrator;
import bisq.core.arbitration.ArbitratorManager;
import bisq.core.btc.AddressEntry;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.user.User;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;

import bisq.common.crypto.KeyRing;
import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ResultHandler;
import bisq.common.locale.LanguageUtil;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Utils;

import com.google.inject.Inject;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableList;

import java.util.ArrayList;
import java.util.Date;

class ArbitratorRegistrationViewModel extends ActivatableViewModel {
    private final ArbitratorManager arbitratorManager;
    private final User user;
    private final P2PService p2PService;
    private final BtcWalletService walletService;
    private final KeyRing keyRing;

    final BooleanProperty registrationEditDisabled = new SimpleBooleanProperty(true);
    final BooleanProperty revokeButtonDisabled = new SimpleBooleanProperty(true);
    final ObjectProperty<Arbitrator> myArbitratorProperty = new SimpleObjectProperty<>();

    final ObservableList<String> languageCodes = FXCollections.observableArrayList(LanguageUtil.getDefaultLanguageLocaleAsCode());
    final ObservableList<String> allLanguageCodes = FXCollections.observableArrayList(LanguageUtil.getAllLanguageCodes());
    private boolean allDataValid;
    private final MapChangeListener<NodeAddress, Arbitrator> arbitratorMapChangeListener;
    private ECKey registrationKey;
    final StringProperty registrationPubKeyAsHex = new SimpleStringProperty();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public ArbitratorRegistrationViewModel(ArbitratorManager arbitratorManager,
                                           User user,
                                           P2PService p2PService,
                                           BtcWalletService walletService,
                                           KeyRing keyRing) {
        this.arbitratorManager = arbitratorManager;
        this.user = user;
        this.p2PService = p2PService;
        this.walletService = walletService;
        this.keyRing = keyRing;

        arbitratorMapChangeListener = new MapChangeListener<NodeAddress, Arbitrator>() {
            @Override
            public void onChanged(Change<? extends NodeAddress, ? extends Arbitrator> change) {
                Arbitrator myRegisteredArbitrator = user.getRegisteredArbitrator();
                myArbitratorProperty.set(myRegisteredArbitrator);

                // We don't reset the languages in case of revocation, as its likely that the arbitrator will use the same again when he re-activate
                // registration later
                if (myRegisteredArbitrator != null)
                    languageCodes.setAll(myRegisteredArbitrator.getLanguageCodes());

                updateDisableStates();
            }
        };
    }

    @Override
    protected void activate() {
        arbitratorManager.getArbitratorsObservableMap().addListener(arbitratorMapChangeListener);
        Arbitrator myRegisteredArbitrator = user.getRegisteredArbitrator();
        myArbitratorProperty.set(myRegisteredArbitrator);
        updateDisableStates();
    }

    @Override
    protected void deactivate() {
        arbitratorManager.getArbitratorsObservableMap().removeListener(arbitratorMapChangeListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions
    ///////////////////////////////////////////////////////////////////////////////////////////

    void onAddLanguage(String code) {
        if (code != null && !languageCodes.contains(code))
            languageCodes.add(code);

        updateDisableStates();
    }

    void onRemoveLanguage(String code) {
        if (code != null && languageCodes.contains(code))
            languageCodes.remove(code);

        updateDisableStates();
    }

    boolean setPrivKeyAndCheckPubKey(String privKeyString) {
        ECKey _registrationKey = arbitratorManager.getRegistrationKey(privKeyString);
        if (_registrationKey != null) {
            String _registrationPubKeyAsHex = Utils.HEX.encode(_registrationKey.getPubKey());
            boolean isKeyValid = arbitratorManager.isPublicKeyInList(_registrationPubKeyAsHex);
            if (isKeyValid) {
                registrationKey = _registrationKey;
                registrationPubKeyAsHex.set(_registrationPubKeyAsHex);
            }
            updateDisableStates();
            return isKeyValid;
        } else {
            updateDisableStates();
            return false;
        }
    }

    void onRegister(ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        updateDisableStates();
        if (allDataValid) {
            AddressEntry arbitratorDepositAddressEntry = walletService.getOrCreateAddressEntry(AddressEntry.Context.ARBITRATOR);
            String registrationSignature = arbitratorManager.signStorageSignaturePubKey(registrationKey);
            // TODO not impl in UI
            String emailAddress = null;
            @SuppressWarnings("ConstantConditions")
            Arbitrator arbitrator = new Arbitrator(
                    p2PService.getAddress(),
                    arbitratorDepositAddressEntry.getPubKey(),
                    arbitratorDepositAddressEntry.getAddressString(),
                    keyRing.getPubKeyRing(),
                    new ArrayList<>(languageCodes),
                    new Date().getTime(),
                    registrationKey.getPubKey(),
                    registrationSignature,
                    emailAddress,
                    null,
                    null
            );

            arbitratorManager.addArbitrator(arbitrator,
                    () -> {
                        updateDisableStates();
                        resultHandler.handleResult();
                    },
                    (errorMessage) -> {
                        updateDisableStates();
                        errorMessageHandler.handleErrorMessage(errorMessage);
                    });
        }
    }


    void onRevoke(ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        arbitratorManager.removeArbitrator(
                () -> {
                    updateDisableStates();
                    resultHandler.handleResult();
                },
                (errorMessage) -> {
                    updateDisableStates();
                    errorMessageHandler.handleErrorMessage(errorMessage);
                });
    }

    private void updateDisableStates() {
        allDataValid = languageCodes != null && languageCodes.size() > 0 && registrationKey != null && registrationPubKeyAsHex.get() != null;
        registrationEditDisabled.set(!allDataValid || myArbitratorProperty.get() != null);
        revokeButtonDisabled.set(!allDataValid || myArbitratorProperty.get() == null);
    }

    boolean isBootstrapped() {
        return p2PService.isBootstrapped();
    }
}
