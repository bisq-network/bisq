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
import bisq.desktop.util.GUIUtil;

import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.locale.LanguageUtil;
import bisq.core.support.dispute.agent.DisputeAgent;
import bisq.core.support.dispute.agent.DisputeAgentManager;
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

public abstract class AgentRegistrationViewModel<R extends DisputeAgent, T extends DisputeAgentManager<R>> extends ActivatableViewModel {
    private final T disputeAgentManager;
    protected final User user;
    protected final P2PService p2PService;
    protected final BtcWalletService walletService;
    protected final KeyRing keyRing;

    final BooleanProperty registrationEditDisabled = new SimpleBooleanProperty(true);
    final BooleanProperty revokeButtonDisabled = new SimpleBooleanProperty(true);
    final ObjectProperty<R> myDisputeAgentProperty = new SimpleObjectProperty<>();

    protected final ObservableList<String> languageCodes = FXCollections.observableArrayList(LanguageUtil.getDefaultLanguageLocaleAsCode());
    final ObservableList<String> allLanguageCodes = FXCollections.observableArrayList(LanguageUtil.getAllLanguageCodes());
    private boolean allDataValid;
    private final MapChangeListener<NodeAddress, R> mapChangeListener;
    protected ECKey registrationKey;
    final StringProperty registrationPubKeyAsHex = new SimpleStringProperty();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    public AgentRegistrationViewModel(T disputeAgentManager,
                                      User user,
                                      P2PService p2PService,
                                      BtcWalletService walletService,
                                      KeyRing keyRing) {
        this.disputeAgentManager = disputeAgentManager;
        this.user = user;
        this.p2PService = p2PService;
        this.walletService = walletService;
        this.keyRing = keyRing;

        mapChangeListener = change -> {
            R registeredDisputeAgentFromUser = getRegisteredDisputeAgentFromUser();
            myDisputeAgentProperty.set(registeredDisputeAgentFromUser);

            // We don't reset the languages in case of revocation, as its likely that the disputeAgent will use the
            // same again when he re-activate registration later
            if (registeredDisputeAgentFromUser != null)
                languageCodes.setAll(registeredDisputeAgentFromUser.getLanguageCodes());

            updateDisableStates();
        };
    }

    @Override
    protected void activate() {
        disputeAgentManager.getObservableMap().addListener(mapChangeListener);
        myDisputeAgentProperty.set(getRegisteredDisputeAgentFromUser());
        updateDisableStates();
    }

    protected abstract R getRegisteredDisputeAgentFromUser();

    @Override
    protected void deactivate() {
        disputeAgentManager.getObservableMap().removeListener(mapChangeListener);
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
        ECKey registrationKey = disputeAgentManager.getRegistrationKey(privKeyString);
        if (registrationKey != null) {
            String _registrationPubKeyAsHex = Utils.HEX.encode(registrationKey.getPubKey());
            boolean isKeyValid = disputeAgentManager.isPublicKeyInList(_registrationPubKeyAsHex);
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

    protected abstract R getDisputeAgent(String registrationSignature, String emailAddress);

    void onRegister(ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        updateDisableStates();
        if (allDataValid) {
            String registrationSignature = disputeAgentManager.signStorageSignaturePubKey(registrationKey);
            // TODO not impl in UI
            String emailAddress = null;
            @SuppressWarnings("ConstantConditions")
            R disputeAgent = getDisputeAgent(registrationSignature, emailAddress);

            disputeAgentManager.addDisputeAgent(disputeAgent,
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
        disputeAgentManager.removeDisputeAgent(
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
        registrationEditDisabled.set(!allDataValid || myDisputeAgentProperty.get() != null);
        revokeButtonDisabled.set(!allDataValid || myDisputeAgentProperty.get() == null);
    }

    boolean isBootstrappedOrShowPopup() {
        return GUIUtil.isBootstrappedOrShowPopup(p2PService);
    }
}
