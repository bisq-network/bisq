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

package bisq.desktop.main.account.register;

import bisq.desktop.common.model.ActivatableViewModel;

import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.dispute.DisputeResolver;
import bisq.core.dispute.DisputeResolverManager;
import bisq.core.locale.LanguageUtil;
import bisq.core.user.User;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;

import bisq.common.crypto.KeyRing;
import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ResultHandler;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Utils;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableList;

public abstract class DisputeResolverRegistrationViewModel<R extends DisputeResolver, T extends DisputeResolverManager<R>> extends ActivatableViewModel {
    private final T disputeResolverManager;
    protected final User user;
    protected final P2PService p2PService;
    protected final BtcWalletService walletService;
    protected final KeyRing keyRing;

    final BooleanProperty registrationEditDisabled = new SimpleBooleanProperty(true);
    final BooleanProperty revokeButtonDisabled = new SimpleBooleanProperty(true);
    final ObjectProperty<R> myDisputeResolverProperty = new SimpleObjectProperty<>();

    protected final ObservableList<String> languageCodes = FXCollections.observableArrayList(LanguageUtil.getDefaultLanguageLocaleAsCode());
    final ObservableList<String> allLanguageCodes = FXCollections.observableArrayList(LanguageUtil.getAllLanguageCodes());
    private boolean allDataValid;
    private final MapChangeListener<NodeAddress, R> mapChangeListener;
    protected ECKey registrationKey;
    final StringProperty registrationPubKeyAsHex = new SimpleStringProperty();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    public DisputeResolverRegistrationViewModel(T disputeResolverManager,
                                                User user,
                                                P2PService p2PService,
                                                BtcWalletService walletService,
                                                KeyRing keyRing) {
        this.disputeResolverManager = disputeResolverManager;
        this.user = user;
        this.p2PService = p2PService;
        this.walletService = walletService;
        this.keyRing = keyRing;

        mapChangeListener = change -> {
            R registeredDisputeResolverFromUser = getRegisteredDisputeResolverFromUser();
            myDisputeResolverProperty.set(registeredDisputeResolverFromUser);

            // We don't reset the languages in case of revocation, as its likely that the disputeResolver will use the
            // same again when he re-activate registration later
            if (registeredDisputeResolverFromUser != null)
                languageCodes.setAll(registeredDisputeResolverFromUser.getLanguageCodes());

            updateDisableStates();
        };
    }

    @Override
    protected void activate() {
        disputeResolverManager.getObservableMap().addListener(mapChangeListener);
        myDisputeResolverProperty.set(getRegisteredDisputeResolverFromUser());
        updateDisableStates();
    }

    protected abstract R getRegisteredDisputeResolverFromUser();

    @Override
    protected void deactivate() {
        disputeResolverManager.getObservableMap().removeListener(mapChangeListener);
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
        ECKey registrationKey = disputeResolverManager.getRegistrationKey(privKeyString);
        if (registrationKey != null) {
            String _registrationPubKeyAsHex = Utils.HEX.encode(registrationKey.getPubKey());
            boolean isKeyValid = disputeResolverManager.isPublicKeyInList(_registrationPubKeyAsHex);
            if (isKeyValid) {
                this.registrationKey = registrationKey;
                registrationPubKeyAsHex.set(_registrationPubKeyAsHex);
            }
            updateDisableStates();
            return isKeyValid;
        } else {
            updateDisableStates();
            return false;
        }
    }

    protected abstract R getDisputeResolver(String registrationSignature, String emailAddress);

    void onRegister(ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        updateDisableStates();
        if (allDataValid) {
            String registrationSignature = disputeResolverManager.signStorageSignaturePubKey(registrationKey);
            // TODO not impl in UI
            String emailAddress = null;
            @SuppressWarnings("ConstantConditions")
            R disputeResolver = getDisputeResolver(registrationSignature, emailAddress);

            disputeResolverManager.addDisputeResolver(disputeResolver,
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
        disputeResolverManager.removeDisputeResolver(
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
        allDataValid = languageCodes.size() > 0 && registrationKey != null && registrationPubKeyAsHex.get() != null;
        registrationEditDisabled.set(!allDataValid || myDisputeResolverProperty.get() != null);
        revokeButtonDisabled.set(!allDataValid || myDisputeResolverProperty.get() == null);
    }

    boolean isBootstrapped() {
        return p2PService.isBootstrapped();
    }
}
