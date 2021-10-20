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

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.locale.Res;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.support.dispute.Dispute;
import bisq.core.support.dispute.arbitration.ArbitrationManager;
import bisq.core.support.dispute.arbitration.TraderDataItem;
import bisq.core.support.dispute.arbitration.arbitrator.ArbitratorManager;
import bisq.core.support.dispute.mediation.MediationManager;
import bisq.core.util.FormattingUtils;

import bisq.common.config.Config;
import bisq.common.util.Tuple2;
import bisq.common.util.Tuple3;
import bisq.common.util.Utilities;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Utils;

import javax.inject.Inject;
import javax.inject.Named;

import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import javafx.geometry.HPos;
import javafx.geometry.VPos;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import javafx.util.Callback;
import javafx.util.StringConverter;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import static bisq.desktop.util.FormBuilder.*;

@Slf4j
public class SignPaymentAccountsWindow extends Overlay<SignPaymentAccountsWindow> {

    private Label descriptionLabel;
    private ComboBox<PaymentMethod> paymentMethodComboBox;
    private CheckBox signAllCheckbox;
    private DatePicker datePicker;
    private InputTextField privateKey;
    private ListView<TraderDataItem> selectedPaymentAccountsList = new ListView<>();
    private final AccountAgeWitnessService accountAgeWitnessService;
    private final ArbitratorManager arbitratorManager;
    private final ArbitrationManager arbitrationManager;
    private final MediationManager mediationManager;
    private final String appName;
    private final boolean useDevPrivilegeKeys;


    @Inject
    public SignPaymentAccountsWindow(AccountAgeWitnessService accountAgeWitnessService,
                                     ArbitratorManager arbitratorManager,
                                     ArbitrationManager arbitrationManager,
                                     MediationManager mediationManager,
                                     @Named(Config.APP_NAME) String appName,
                                     @Named(Config.USE_DEV_PRIVILEGE_KEYS) boolean useDevPrivilegeKeys) {
        this.accountAgeWitnessService = accountAgeWitnessService;
        this.arbitratorManager = arbitratorManager;
        this.arbitrationManager = arbitrationManager;
        this.mediationManager = mediationManager;
        this.appName = appName;
        this.useDevPrivilegeKeys = useDevPrivilegeKeys;
    }

    @Override
    public void show() {
        width = 1000;
        rowIndex = -1;
        createGridPane();

        // We want to have more space to read list entries... initial screen does not look so nice now, but
        // dynamically updating height of window is a bit tricky.... @christoph feel free to improve if you like...
        gridPane.setPrefHeight(600);

        gridPane.getColumnConstraints().get(1).setHgrow(Priority.NEVER);

        headLine(Res.get("popup.accountSigning.selectAccounts.headline"));
        type = Type.Attention;

        addHeadLine();
        addSelectAccountsContent();
        addButtons();
        applyStyles();

        display();
    }

    private void addSelectAccountsContent() {
        descriptionLabel = addMultilineLabel(gridPane, ++rowIndex,
                Res.get("popup.accountSigning.selectAccounts.description"));

        paymentMethodComboBox = addComboBox(gridPane, ++rowIndex, Res.get("shared.selectPaymentMethod"));
        paymentMethodComboBox.setVisibleRowCount(11);
        paymentMethodComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(PaymentMethod paymentMethod) {
                return paymentMethod != null ? Res.get(paymentMethod.getId()) : "";
            }

            @Override
            public PaymentMethod fromString(String s) {
                return null;
            }
        });


        paymentMethodComboBox.setItems(FXCollections.observableArrayList(getPaymentMethods()));
        paymentMethodComboBox.setOnAction(e -> updateAccountSelectionState());

        signAllCheckbox = addLabelCheckBox(gridPane, ++rowIndex, Res.get("popup.accountSigning.selectAccounts.signAll"));
        GridPane.setHalignment(signAllCheckbox, HPos.LEFT);
        signAllCheckbox.selectedProperty().addListener((observable, oldValue, newValue) -> {
            paymentMethodComboBox.setDisable(newValue);
            updateAccountSelectionState();
        });

        datePicker = addTopLabelDatePicker(gridPane, ++rowIndex,
                Res.get("popup.accountSigning.selectAccounts.datePicker"),
                0).second;
        datePicker.setOnAction(e -> updateAccountSelectionState());
        datePicker.setValue(Instant.ofEpochMilli(new Date().getTime()).minus(60, ChronoUnit.DAYS)
                .atZone(ZoneId.systemDefault()).toLocalDate());
    }

    private List<PaymentMethod> getPaymentMethods() {
        return PaymentMethod.getPaymentMethods().stream()
                .filter(PaymentMethod::isFiat)
                .filter(PaymentMethod::hasChargebackRisk)
                .collect(Collectors.toList());
    }

    private void addECKeyField() {
        privateKey = addInputTextField(gridPane, ++rowIndex, Res.get("popup.accountSigning.signAccounts.ECKey"));
        GridPane.setVgrow(privateKey, Priority.ALWAYS);
        GridPane.setValignment(privateKey, VPos.TOP);
    }

    private void updateAccountSelectionState() {
        actionButton.setDisable((!signAllCheckbox.isSelected() && paymentMethodComboBox.getSelectionModel().isEmpty()) ||
                datePicker.getValue() == null
        );
    }

    private void removeContent() {
        removeRowsFromGridPane(gridPane, 2, 3);
        rowIndex = 1;
    }

    private void addSelectedAccountsContent() {
        removeContent();
        Tuple3<Label, ListView<TraderDataItem>, VBox> selectedPaymentAccountsTuple =
                addTopLabelListView(gridPane,
                        ++rowIndex, Res.get("popup.accountSigning.confirmSelectedAccounts.headline"));
        GridPane.setRowSpan(selectedPaymentAccountsTuple.third, 2);
        selectedPaymentAccountsList = selectedPaymentAccountsTuple.second;
        ObservableList<Dispute> disputesAsObservableList = useDevPrivilegeKeys ?
                mediationManager.getDisputesAsObservableList()
                : arbitrationManager.getDisputesAsObservableList();
        long safeDate = datePicker.getValue().atStartOfDay().toEpochSecond(ZoneOffset.UTC) * 1000;
        List<TraderDataItem> traderDataItemList;
        StringBuilder sb = new StringBuilder("Summary for ").append(appName).append("\n");
        if (signAllCheckbox.isSelected()) {
            traderDataItemList = new ArrayList<>();
            getPaymentMethods().forEach(paymentMethod -> {
                List<TraderDataItem> list = accountAgeWitnessService.getTraderPaymentAccounts(
                        safeDate,
                        paymentMethod,
                        disputesAsObservableList);
                traderDataItemList.addAll(list);

                sb.append("\nPayment method: ").append(Res.get(paymentMethod.getId()))
                        .append(" (No. of signed accounts: ").append(list.size()).append(")\n");
                list.forEach(traderDataItem -> {
                    sb.append("Account created: ")
                            .append(FormattingUtils.formatDateTime(new Date(traderDataItem.getAccountAgeWitness().getDate()), true))
                            .append(" Account: ")
                            .append(traderDataItem.getPaymentAccountPayload().getPaymentDetails()).append("\n");
                });
            });
            sb.append("\nTotal accounts signed: ").append(traderDataItemList.size());
        } else {
            PaymentMethod paymentMethod = paymentMethodComboBox.getSelectionModel().getSelectedItem();
            traderDataItemList = accountAgeWitnessService.getTraderPaymentAccounts(
                    safeDate,
                    paymentMethod,
                    disputesAsObservableList);
            sb.append("\nPayment method: ").append(Res.get(paymentMethod.getId()))
                    .append(" (No. of signed accounts: ").append(traderDataItemList.size()).append(")\n");
            traderDataItemList.forEach(traderDataItem -> {
                sb.append("Account created: ")
                        .append(FormattingUtils.formatDateTime(new Date(traderDataItem.getAccountAgeWitness().getDate()), true))
                        .append(" Account: ")
                        .append(traderDataItem.getPaymentAccountPayload().getPaymentDetails()).append("\n");
            });
        }
        log.info(sb.toString());
        Utilities.copyToClipboard(sb.toString());

        selectedPaymentAccountsList.setItems(FXCollections.observableArrayList(traderDataItemList));

        headLineLabel.setText(Res.get("popup.accountSigning.confirmSelectedAccounts.headline"));
        descriptionLabel.setText(Res.get("popup.accountSigning.confirmSelectedAccounts.description",
                selectedPaymentAccountsList.getItems().size()));
        ((AutoTooltipButton) actionButton).updateText(Res.get("popup.accountSigning.confirmSelectedAccounts.button"));

        updateAccountSelectionState();

        actionButton.setOnAction(e -> addAccountsToSignContent());

        selectedPaymentAccountsList.setCellFactory(new Callback<>() {
            @Override
            public ListCell<TraderDataItem> call(
                    ListView<TraderDataItem> param) {
                return new ListCell<>() {
                    @Override
                    protected void updateItem(TraderDataItem item, boolean empty) {
                        super.updateItem(item, empty);

                        if (item != null && !empty) {
                            setText(item.getPaymentAccountPayload().getPaymentDetails());
                        } else {
                            setText(null);
                        }
                    }
                };
            }
        });
    }

    private void addAccountsToSignContent() {
        removeContent();
        addECKeyField();

        headLineLabel.setText(Res.get("popup.accountSigning.signAccounts.headline"));
        descriptionLabel.setText(Res.get("popup.accountSigning.signAccounts.description", selectedPaymentAccountsList.getItems().size()));
        ((AutoTooltipButton) actionButton).updateText(Res.get("popup.accountSigning.signAccounts.button"));
        actionButton.setOnAction(a -> {
            ECKey arbitratorKey = arbitratorManager.getRegistrationKey(privateKey.getText());
            if (arbitratorKey != null) {
                String arbitratorPubKeyAsHex = Utils.HEX.encode(arbitratorKey.getPubKey());
                boolean isKeyValid = arbitratorManager.isPublicKeyInList(arbitratorPubKeyAsHex);
                if (isKeyValid) {
                    selectedPaymentAccountsList.getItems().forEach(item ->
                            accountAgeWitnessService.arbitratorSignAccountAgeWitness(item.getTradeAmount(),
                                    item.getAccountAgeWitness(),
                                    arbitratorKey,
                                    item.getPeersPubKey()));
                    addSuccessContent();
                }
            } else {
                new Popup().error(Res.get("popup.accountSigning.signAccounts.ECKey.error")).onClose(this::hide).show();
            }

        });
    }

    private void addSuccessContent() {
        removeContent();
        GridPane.setVgrow(descriptionLabel, Priority.ALWAYS);
        GridPane.setValignment(descriptionLabel, VPos.TOP);

        closeButton.setVisible(false);
        closeButton.setManaged(false);
        headLineLabel.setText(Res.get("popup.accountSigning.success.headline"));
        descriptionLabel.setText(Res.get("popup.accountSigning.success.description", selectedPaymentAccountsList.getItems().size()));
        ((AutoTooltipButton) actionButton).updateText(Res.get("shared.ok"));
        actionButton.setOnAction(a -> hide());
    }

    @Override
    protected void addButtons() {

        Tuple2<Button, Button> buttonTuple = add2ButtonsAfterGroup(gridPane, ++rowIndex,
                Res.get("popup.accountSigning.selectAccounts.headline"), Res.get("shared.cancel"));

        actionButton = buttonTuple.first;
        actionButton.setDisable(true);
        actionButton.setOnAction(e -> addSelectedAccountsContent());

        closeButton = (AutoTooltipButton) buttonTuple.second;
        closeButton.setOnAction(e -> hide());

    }
}
