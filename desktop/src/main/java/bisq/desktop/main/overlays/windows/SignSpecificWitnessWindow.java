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

package bisq.desktop.main.overlays.windows;

import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.components.BisqTextArea;
import bisq.desktop.components.InputTextField;
import bisq.desktop.main.overlays.Overlay;
import bisq.desktop.main.overlays.popups.Popup;

import bisq.core.account.witness.AccountAgeWitness;
import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.locale.Res;
import bisq.core.support.dispute.arbitration.arbitrator.ArbitratorManager;

import bisq.common.util.Tuple2;
import bisq.common.util.Utilities;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Utils;

import javax.inject.Inject;

import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

import javafx.geometry.VPos;

import java.util.Date;

import lombok.extern.slf4j.Slf4j;

import static bisq.desktop.util.FormBuilder.*;

@Slf4j
public class SignSpecificWitnessWindow extends Overlay<SignSpecificWitnessWindow> {

    private Tuple2<AccountAgeWitness, byte[]> signInfo;
    private InputTextField privateKey;
    private final AccountAgeWitnessService accountAgeWitnessService;
    private final ArbitratorManager arbitratorManager;


    @Inject
    public SignSpecificWitnessWindow(AccountAgeWitnessService accountAgeWitnessService,
                                     ArbitratorManager arbitratorManager) {
        this.accountAgeWitnessService = accountAgeWitnessService;
        this.arbitratorManager = arbitratorManager;
    }

    @Override
    public void show() {
        width = 1000;
        rowIndex = -1;
        createGridPane();

        gridPane.setPrefHeight(600);
        gridPane.getColumnConstraints().get(1).setHgrow(Priority.NEVER);
        headLine(Res.get("popup.accountSigning.singleAccountSelect.headline"));
        type = Type.Attention;

        addHeadLine();
        addSelectWitnessContent();
        addButtons();
        applyStyles();

        display();
    }

    private void addSelectWitnessContent() {
        TextArea accountInfoText = new BisqTextArea();
        accountInfoText.setPrefHeight(270);
        accountInfoText.setWrapText(true);
        GridPane.setRowIndex(accountInfoText, ++rowIndex);
        gridPane.getChildren().add(accountInfoText);

        accountInfoText.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null || newValue.isEmpty()) {
                return;
            }
            signInfo = accountAgeWitnessService.getSignInfoFromString(newValue);
            if (signInfo == null) {
                actionButton.setDisable(true);
                return;
            }
            actionButton.setDisable(false);
        });
    }

    private void addECKeyField() {
        privateKey = addInputTextField(gridPane, ++rowIndex, Res.get("popup.accountSigning.signAccounts.ECKey"));
        actionButton.setDisable(true);
        GridPane.setVgrow(privateKey, Priority.ALWAYS);
        GridPane.setValignment(privateKey, VPos.TOP);
        privateKey.textProperty().addListener((observable, oldValue, newValue) -> {
            if (checkedArbitratorKey() == null) {
                actionButton.setDisable(true);
                return;
            }
            actionButton.setDisable(false);
        });
    }

    private void removeContent() {
        removeRowsFromGridPane(gridPane, 1, 3);
        rowIndex = 1;
    }

    private void importAccountAgeWitness() {
        removeContent();
        headLineLabel.setText(Res.get("popup.accountSigning.confirmSingleAccount.headline"));
        var selectedWitnessTextField = addTopLabelTextField(gridPane, ++rowIndex,
                Res.get("popup.accountSigning.confirmSingleAccount.selectedHash")).second;
        selectedWitnessTextField.setText(Utilities.bytesAsHexString(signInfo.first.getHash()));
        addECKeyField();
        ((AutoTooltipButton) actionButton).updateText(Res.get("popup.accountSigning.confirmSingleAccount.button"));
        actionButton.setOnAction(a -> {
            var arbitratorKey = checkedArbitratorKey();
            if (arbitratorKey != null) {
                accountAgeWitnessService.arbitratorSignAccountAgeWitness(signInfo.first,
                        arbitratorKey,
                        signInfo.second,
                        new Date().getTime());
                addSuccessContent();
            } else {
                new Popup().error(Res.get("popup.accountSigning.signAccounts.ECKey.error")).onClose(this::hide).show();
            }

        });
    }

    private void addSuccessContent() {
        removeContent();
        closeButton.setVisible(false);
        closeButton.setManaged(false);
        headLineLabel.setText(Res.get("popup.accountSigning.successSingleAccount.success.headline"));
        var descriptionLabel = addMultilineLabel(gridPane, ++rowIndex,
                Res.get("popup.accountSigning.successSingleAccount.description",
                        Utilities.bytesAsHexString(signInfo.first.getHash())));
        GridPane.setVgrow(descriptionLabel, Priority.ALWAYS);
        GridPane.setValignment(descriptionLabel, VPos.TOP);
        ((AutoTooltipButton) actionButton).updateText(Res.get("shared.ok"));
        actionButton.setOnAction(a -> hide());
    }

    @Override
    protected void addButtons() {
        var buttonTuple = add2ButtonsAfterGroup(gridPane, ++rowIndex + 2,
                Res.get("popup.accountSigning.singleAccountSelect.headline"), Res.get("shared.cancel"));

        actionButton = buttonTuple.first;
        actionButton.setDisable(true);
        actionButton.setOnAction(e -> importAccountAgeWitness());

        closeButton = (AutoTooltipButton) buttonTuple.second;
        closeButton.setOnAction(e -> hide());
    }

    private ECKey checkedArbitratorKey() {
        var arbitratorKey = arbitratorManager.getRegistrationKey(privateKey.getText());
        if (arbitratorKey == null) {
            return null;
        }
        var arbitratorPubKeyAsHex = Utils.HEX.encode(arbitratorKey.getPubKey());
        var isKeyValid = arbitratorManager.isPublicKeyInList(arbitratorPubKeyAsHex);
        return isKeyValid ? arbitratorKey : null;
    }
}
