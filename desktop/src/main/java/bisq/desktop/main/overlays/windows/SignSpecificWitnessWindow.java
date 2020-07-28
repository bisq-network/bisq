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

import bisq.core.account.witness.AccountAgeWitness;
import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.locale.Res;
import bisq.core.support.dispute.arbitration.arbitrator.ArbitratorManager;

import bisq.common.util.Utilities;

import org.bitcoinj.core.Utils;

import javax.inject.Inject;

import com.jfoenix.controls.JFXAutoCompletePopup;

import javafx.scene.control.DatePicker;
import javafx.scene.control.ListCell;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

import javafx.geometry.VPos;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import lombok.extern.slf4j.Slf4j;

import static bisq.desktop.util.FormBuilder.*;

@Slf4j
public class SignSpecificWitnessWindow extends Overlay<SignSpecificWitnessWindow> {

    private InputTextField searchTextField;
    private JFXAutoCompletePopup<AccountAgeWitness> searchAutoComplete;
    private AccountAgeWitness selectedWitness;
    private DatePicker datePicker;
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
        searchTextField = addInputTextField(gridPane, ++rowIndex,
                Res.get("popup.accountSigning.singleAccountSelect.description"));

        searchAutoComplete = new JFXAutoCompletePopup<>();
        searchAutoComplete.setPrefWidth(400);
        searchAutoComplete.getSuggestions().addAll(accountAgeWitnessService.getOrphanSignedWitnesses());
        searchAutoComplete.setSuggestionsCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(AccountAgeWitness item, boolean empty) {
                super.updateItem(item, empty);
                if (item != null) {
                    setText(Utilities.bytesAsHexString(item.getHash()));
                } else {
                    setText("");
                }
            }
        });
        searchAutoComplete.setSelectionHandler(event -> {
            searchTextField.setText(Utilities.bytesAsHexString(event.getObject().getHash()));
            selectedWitness = event.getObject();
            if (selectedWitness != null) {
                datePicker.setValue(Instant.ofEpochMilli(selectedWitness.getDate()).atZone(
                        ZoneId.systemDefault()).toLocalDate());
            }
        });

        searchTextField.textProperty().addListener(observable -> {
            searchAutoComplete.filter(witness -> Utilities.bytesAsHexString(witness.getHash()).startsWith(
                    searchTextField.getText().toLowerCase()));
            if (searchAutoComplete.getFilteredSuggestions().isEmpty()) {
                searchAutoComplete.hide();
            } else {
                searchAutoComplete.show(searchTextField);
            }
        });

        datePicker = addTopLabelDatePicker(gridPane, ++rowIndex,
                Res.get("popup.accountSigning.singleAccountSelect.datePicker"),
                0).second;
        datePicker.setOnAction(e -> updateWitnessSelectionState());
    }

    private void addECKeyField() {
        privateKey = addInputTextField(gridPane, ++rowIndex, Res.get("popup.accountSigning.signAccounts.ECKey"));
        GridPane.setVgrow(privateKey, Priority.ALWAYS);
        GridPane.setValignment(privateKey, VPos.TOP);
    }

    private void updateWitnessSelectionState() {
        actionButton.setDisable(selectedWitness == null || datePicker.getValue() == null);
    }

    private void removeContent() {
        removeRowsFromGridPane(gridPane, 1, 3);
        rowIndex = 1;
    }

    private void selectAccountAgeWitness() {
        removeContent();
        headLineLabel.setText(Res.get("popup.accountSigning.confirmSingleAccount.headline"));
        var selectedWitnessTextField = addTopLabelTextField(gridPane, ++rowIndex,
                Res.get("popup.accountSigning.confirmSingleAccount.selectedHash")).second;
        selectedWitnessTextField.setText(Utilities.bytesAsHexString(selectedWitness.getHash()));
        addECKeyField();
        ((AutoTooltipButton) actionButton).updateText(Res.get("popup.accountSigning.confirmSingleAccount.button"));
        actionButton.setOnAction(a -> {
            var arbitratorKey = arbitratorManager.getRegistrationKey(privateKey.getText());
            if (arbitratorKey != null) {
                var arbitratorPubKeyAsHex = Utils.HEX.encode(arbitratorKey.getPubKey());
                var isKeyValid = arbitratorManager.isPublicKeyInList(arbitratorPubKeyAsHex);
                if (isKeyValid) {
                    var result = accountAgeWitnessService.arbitratorSignOrphanWitness(selectedWitness,
                            arbitratorKey,
                            datePicker.getValue().atStartOfDay().toEpochSecond(ZoneOffset.UTC) * 1000);
                    if (result.isEmpty()) {
                        addSuccessContent();
                    } else {
                        new Popup().error(Res.get("popup.accountSigning.successSingleAccount.signError", result))
                                .onClose(this::hide).show();
                    }
                }
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
                        Utilities.bytesAsHexString(selectedWitness.getHash())));
        GridPane.setVgrow(descriptionLabel, Priority.ALWAYS);
        GridPane.setValignment(descriptionLabel, VPos.TOP);
        ((AutoTooltipButton) actionButton).updateText(Res.get("shared.ok"));
        actionButton.setOnAction(a -> hide());
    }

    @Override
    protected void addButtons() {
        var buttonTuple = add2ButtonsAfterGroup(gridPane, ++rowIndex + 1,
                Res.get("popup.accountSigning.singleAccountSelect.headline"), Res.get("shared.cancel"));

        actionButton = buttonTuple.first;
        actionButton.setDisable(true);
        actionButton.setOnAction(e -> selectAccountAgeWitness());

        closeButton = (AutoTooltipButton) buttonTuple.second;
        closeButton.setOnAction(e -> hide());

    }
}
