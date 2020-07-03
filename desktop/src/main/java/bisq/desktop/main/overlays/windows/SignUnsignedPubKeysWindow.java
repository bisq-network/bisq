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
import bisq.desktop.components.InputTextField;
import bisq.desktop.main.overlays.Overlay;
import bisq.desktop.main.overlays.popups.Popup;

import bisq.core.account.sign.SignedWitness;
import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.locale.Res;
import bisq.core.support.dispute.arbitration.arbitrator.ArbitratorManager;

import bisq.common.crypto.Hash;
import bisq.common.util.Tuple3;
import bisq.common.util.Utilities;

import org.bitcoinj.core.Utils;

import javax.inject.Inject;

import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import javafx.geometry.VPos;

import javafx.collections.FXCollections;

import javafx.util.Callback;

import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

import static bisq.desktop.util.FormBuilder.add2ButtonsAfterGroup;
import static bisq.desktop.util.FormBuilder.addInputTextField;
import static bisq.desktop.util.FormBuilder.addTopLabelListView;
import static bisq.desktop.util.FormBuilder.removeRowsFromGridPane;

@Slf4j
public class SignUnsignedPubKeysWindow extends Overlay<SignUnsignedPubKeysWindow> {

    private ListView<SignedWitness> unsignedPubKeys = new ListView<>();
    private InputTextField privateKey;
    private final AccountAgeWitnessService accountAgeWitnessService;
    private final ArbitratorManager arbitratorManager;
    private List<SignedWitness> signedWitnessList = new ArrayList<>();
    private List<String> failed = new ArrayList<>();
    private Callback<ListView<SignedWitness>, ListCell<SignedWitness>> signedWitnessCellFactory;

    @Inject
    public SignUnsignedPubKeysWindow(AccountAgeWitnessService accountAgeWitnessService,
                                     ArbitratorManager arbitratorManager) {
        this.accountAgeWitnessService = accountAgeWitnessService;
        this.arbitratorManager = arbitratorManager;

        signedWitnessCellFactory = new Callback<>() {
            @Override
            public ListCell<SignedWitness> call(
                    ListView<SignedWitness> param) {
                return new ListCell<>() {
                    @Override
                    protected void updateItem(SignedWitness item, boolean empty) {
                        super.updateItem(item, empty);

                        if (item != null && !empty) {
                            setText(Utilities.bytesAsHexString(Hash.getRipemd160hash(item.getSignerPubKey())));
                        } else {
                            setText(null);
                        }
                    }
                };
            }
        };
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
        addUnsignedPubKeysContent();
        addECKeyField();
        addButtons();
        applyStyles();

        display();
    }

    private void addUnsignedPubKeysContent() {
        Tuple3<Label, ListView<SignedWitness>, VBox> unsignedPubKeysTuple = addTopLabelListView(gridPane, ++rowIndex,
                Res.get("popup.accountSigning.unsignedPubKeys.headline"));
        unsignedPubKeys = unsignedPubKeysTuple.second;
        unsignedPubKeys.setCellFactory(signedWitnessCellFactory);
        unsignedPubKeys.setItems(FXCollections.observableArrayList(
                accountAgeWitnessService.getUnsignedSignerPubKeys()));
    }

    private void addECKeyField() {
        privateKey = addInputTextField(gridPane, ++rowIndex, Res.get("popup.accountSigning.signAccounts.ECKey"));
        GridPane.setVgrow(privateKey, Priority.ALWAYS);
        GridPane.setValignment(privateKey, VPos.TOP);
    }

    private void removeContent() {
        removeRowsFromGridPane(gridPane, 1, 3);
        rowIndex = 1;
    }

    private void signPubKeys() {
        removeContent();
        headLineLabel.setText(Res.get("popup.accountSigning.unsignedPubKeys.signed"));
        var arbitratorKey = arbitratorManager.getRegistrationKey(privateKey.getText());
        if (arbitratorKey != null) {
            var arbitratorPubKeyAsHex = Utils.HEX.encode(arbitratorKey.getPubKey());
            var isKeyValid = arbitratorManager.isPublicKeyInList(arbitratorPubKeyAsHex);
            failed.clear();
            if (isKeyValid) {
                unsignedPubKeys.getItems().forEach(signedWitness -> {
                    var result = accountAgeWitnessService.arbitratorSignOrphanPubKey(arbitratorKey,
                            signedWitness.getSignerPubKey(), signedWitness.getDate());
                    if (result.isEmpty()) {
                        signedWitnessList.add(signedWitness);
                    } else {
                        failed.add("Signing pubkey " + Utilities.bytesAsHexString(Hash.getRipemd160hash(
                                signedWitness.getSignerPubKey())) + " failed with error " + result);
                    }
                });
                showResult();
            }
        } else {
            new Popup().error(Res.get("popup.accountSigning.signAccounts.ECKey.error")).onClose(this::hide).show();
        }
    }

    private void showResult() {
        removeContent();
        closeButton.setVisible(false);
        closeButton.setManaged(false);

        Tuple3<Label, ListView<SignedWitness>, VBox> signedTuple = addTopLabelListView(gridPane, ++rowIndex,
                Res.get("popup.accountSigning.unsignedPubKeys.result.signed"));
        ListView<SignedWitness> signedWitnessListView = signedTuple.second;
        signedWitnessListView.setCellFactory(signedWitnessCellFactory);
        signedWitnessListView.setItems(FXCollections.observableArrayList(signedWitnessList));
        Tuple3<Label, ListView<String>, VBox> failedTuple = addTopLabelListView(gridPane, ++rowIndex,
                Res.get("popup.accountSigning.unsignedPubKeys.result.failed"));
        ListView<String> failedView = failedTuple.second;
        failedView.setItems(FXCollections.observableArrayList(failed));

        ((AutoTooltipButton) actionButton).updateText(Res.get("shared.ok"));
        actionButton.setOnAction(a -> hide());
    }

    @Override
    protected void addButtons() {
        var buttonTuple = add2ButtonsAfterGroup(gridPane, ++rowIndex + 1,
                Res.get("popup.accountSigning.unsignedPubKeys.sign"), Res.get("shared.cancel"));

        actionButton = buttonTuple.first;
        actionButton.setDisable(unsignedPubKeys.getItems().size() == 0);
        actionButton.setOnAction(e -> signPubKeys());

        closeButton = (AutoTooltipButton) buttonTuple.second;
        closeButton.setOnAction(e -> hide());

    }
}
