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
import bisq.core.account.sign.SignedWitnessService;
import bisq.core.arbitration.ArbitratorManager;
import bisq.core.arbitration.BuyerDataItem;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.FiatCurrency;
import bisq.core.locale.Res;
import bisq.core.payment.payload.PaymentMethod;

import bisq.common.util.Tuple2;
import bisq.common.util.Tuple3;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Utils;

import javax.inject.Inject;

import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import javafx.collections.FXCollections;

import javafx.util.Callback;
import javafx.util.StringConverter;

import java.time.ZoneOffset;

import java.util.List;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import static bisq.desktop.util.FormBuilder.*;

@Slf4j
public class SignPaymentAccountsWindow extends Overlay<SignPaymentAccountsWindow> {

    private Label descriptionLabel;
    private ComboBox<PaymentMethod> paymentMethodComboBox;
    private ComboBox<FiatCurrency> currencyComboBox;
    private DatePicker datePicker;
    private InputTextField privateKey;
    private ListView<BuyerDataItem> selectedPaymentAccountsList = new ListView<>();
    private final SignedWitnessService signedWitnessService;
    private final ArbitratorManager arbitratorManager;


    @Inject
    public SignPaymentAccountsWindow(SignedWitnessService signedWitnessService,
                                     ArbitratorManager arbitratorManager) {
        this.signedWitnessService = signedWitnessService;
        this.arbitratorManager = arbitratorManager;
    }

    @Override
    public void show() {
        rowIndex = -1;
        createGridPane();
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

        List<PaymentMethod> list = PaymentMethod.getPaymentMethods().stream()
                .filter(paymentMethod -> !paymentMethod.isAsset())
                .collect(Collectors.toList());

        paymentMethodComboBox.setItems(FXCollections.observableArrayList(list));
        paymentMethodComboBox.setOnAction(e -> updateAccountSelectionState());

        currencyComboBox = addComboBox(gridPane, ++rowIndex, Res.get("list.currency.select"));
        currencyComboBox.setVisibleRowCount(11);
        currencyComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(FiatCurrency currency) {
                return currency != null ? currency.getNameAndCode() : "";
            }

            @Override
            public FiatCurrency fromString(String string) {
                return null;
            }
        });

        currencyComboBox.setItems(FXCollections.observableArrayList(CurrencyUtil.getAllSortedFiatCurrencies()));
        currencyComboBox.setOnAction(e -> updateAccountSelectionState());

        datePicker = addTopLabelDatePicker(gridPane, ++rowIndex,
                Res.get("popup.accountSigning.selectAccounts.datePicker"),
                0).second;
        datePicker.setOnAction(e -> updateAccountSelectionState());
    }

    private void addECKeyField() {
        privateKey = addInputTextField(gridPane, ++rowIndex, Res.get("popup.accountSigning.signAccounts.ECKey"));
    }

    private void updateAccountSelectionState() {
        actionButton.setDisable(paymentMethodComboBox.getSelectionModel().isEmpty() ||
                currencyComboBox.getSelectionModel().isEmpty() ||
                datePicker.getValue() == null
        );
    }

    private void removeContent() {
        removeRowsFromGridPane(gridPane, 2, 4);
        rowIndex = 1;
    }

    private void addAccountsToSignContent() {
        removeContent();

        // Add payment accounts to sign
        Tuple3<Label, ListView<BuyerDataItem>, VBox> selectedPaymentAccountsTuple =
                addTopLabelListView(gridPane,
                        ++rowIndex,
                        Res.get("popup.accountSigning.selectAccounts.headline"));
        GridPane.setRowSpan(selectedPaymentAccountsTuple.third, 3);
        selectedPaymentAccountsList = selectedPaymentAccountsTuple.second;
        selectedPaymentAccountsList.setItems(FXCollections.observableArrayList(
                signedWitnessService.getBuyerPaymentAccounts(
                        datePicker.getValue().atStartOfDay().toEpochSecond(ZoneOffset.UTC) * 1000,
                        paymentMethodComboBox.getSelectionModel().getSelectedItem())));

        headLineLabel.setText(Res.get("popup.accountSigning.signAccounts.headline"));
        descriptionLabel.setText(Res.get("popup.accountSigning.signAccounts.description",
                selectedPaymentAccountsList.getItems().size()));
        ((AutoTooltipButton) actionButton).updateText(Res.get("popup.accountSigning.signAccounts.button"));

        actionButton.setOnAction(e -> {
            removeContent();
            addECKeyField();
            headLineLabel.setText(Res.get("popup.accountSigning.success.headline"));
            descriptionLabel.setText(Res.get("popup.accountSigning.success.description",
                    selectedPaymentAccountsList.getItems().size()));
            ((AutoTooltipButton) actionButton).updateText(Res.get("shared.ok"));
            actionButton.setOnAction(a -> {
                ECKey arbitratorKey = arbitratorManager.getRegistrationKey(privateKey.getText());
                if (arbitratorKey != null) {
                    String arbitratorPubKeyAsHex = Utils.HEX.encode(arbitratorKey.getPubKey());
                    boolean isKeyValid = arbitratorManager.isPublicKeyInList(arbitratorPubKeyAsHex);
                    if (isKeyValid) {
                        selectedPaymentAccountsList.getItems().forEach(item -> {
                            // Sign accounts
                            SignedWitness signedWitness = signedWitnessService.signAccountAgeWitness(
                                    item.getTradeAmount(),
                                    item.getAccountAgeWitness(),
                                    arbitratorKey,
                                    item.getSellerPubKey());
                            log.info("Signed witness {}", signedWitness.toString());
                        });
                        hide();
                    }
                } else {
                    new Popup<>().error("Bad arbitrator ECKey").show();
                }

            });
        });

        selectedPaymentAccountsList.setCellFactory(new Callback<>() {
            @Override
            public ListCell<BuyerDataItem> call(
                    ListView<BuyerDataItem> param) {
                return new ListCell<>() {
                    @Override
                    protected void updateItem(BuyerDataItem item, boolean empty) {
                        super.updateItem(item, empty);

                        if (item != null && !empty) {
                            setText(item.getPaymentAccountPayload().toString());
                        } else {
                            setText(null);
                        }
                    }
                };
            }
        });
    }

    @Override
    protected void addButtons() {

        Tuple2<Button, Button> buttonTuple = add2ButtonsAfterGroup(gridPane, ++rowIndex,
                Res.get("popup.accountSigning.selectAccounts.headline"), Res.get("shared.cancel"));

        actionButton = buttonTuple.first;
        actionButton.setDisable(true);
        actionButton.setOnAction(e -> {
            //TODO: select accounts to sign
            addAccountsToSignContent();
        });

        Button cancelButton = buttonTuple.second;
        cancelButton.setOnAction(e -> hide());

    }
}
