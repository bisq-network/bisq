/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.gui.main.account.content.altcoinaccounts;

import io.bitsquare.common.UserThread;
import io.bitsquare.common.util.Tuple2;
import io.bitsquare.common.util.Tuple3;
import io.bitsquare.gui.common.view.ActivatableViewAndModel;
import io.bitsquare.gui.common.view.FxmlView;
import io.bitsquare.gui.components.TitledGroupBg;
import io.bitsquare.gui.components.paymentmethods.CryptoCurrencyForm;
import io.bitsquare.gui.components.paymentmethods.PaymentMethodForm;
import io.bitsquare.gui.main.overlays.popups.Popup;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.gui.util.FormBuilder;
import io.bitsquare.gui.util.ImageUtil;
import io.bitsquare.gui.util.Layout;
import io.bitsquare.gui.util.validation.*;
import io.bitsquare.locale.CryptoCurrency;
import io.bitsquare.locale.TradeCurrency;
import io.bitsquare.payment.PaymentAccount;
import io.bitsquare.payment.PaymentAccountFactory;
import io.bitsquare.payment.PaymentMethod;
import javafx.beans.value.ChangeListener;
import javafx.geometry.VPos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.util.Callback;

import javax.inject.Inject;
import java.util.concurrent.TimeUnit;

import static io.bitsquare.gui.util.FormBuilder.*;

@FxmlView
public class AltCoinAccountsView extends ActivatableViewAndModel<GridPane, AltCoinAccountsViewModel> {

    private ListView<PaymentAccount> paymentAccountsListView;

    private final IBANValidator ibanValidator;
    private final BICValidator bicValidator;
    private final InputValidator inputValidator;
    private final OKPayValidator okPayValidator;
    private final AliPayValidator aliPayValidator;
    private final PerfectMoneyValidator perfectMoneyValidator;
    private final SwishValidator swishValidator;
    private final AltCoinAddressValidator altCoinAddressValidator;
    private BSFormatter formatter;

    private PaymentMethodForm paymentMethodForm;
    private TitledGroupBg accountTitledGroupBg;
    private Button addAccountButton, saveNewAccountButton, exportButton, importButton;
    private int gridRow = 0;
    private ChangeListener<PaymentAccount> paymentAccountChangeListener;

    @Inject
    public AltCoinAccountsView(AltCoinAccountsViewModel model,
                               IBANValidator ibanValidator,
                               BICValidator bicValidator,
                               InputValidator inputValidator,
                               OKPayValidator okPayValidator,
                               AliPayValidator aliPayValidator,
                               PerfectMoneyValidator perfectMoneyValidator,
                               SwishValidator swishValidator,
                               AltCoinAddressValidator altCoinAddressValidator,
                               BSFormatter formatter) {
        super(model);

        this.ibanValidator = ibanValidator;
        this.bicValidator = bicValidator;
        this.inputValidator = inputValidator;
        this.okPayValidator = okPayValidator;
        this.aliPayValidator = aliPayValidator;
        this.perfectMoneyValidator = perfectMoneyValidator;
        this.swishValidator = swishValidator;
        this.altCoinAddressValidator = altCoinAddressValidator;
        this.formatter = formatter;
    }

    @Override
    public void initialize() {
        buildForm();
        paymentAccountChangeListener = (observable, oldValue, newValue) -> {
            if (newValue != null)
                onSelectAccount(newValue);
        };
        Label placeholder = new Label("There are no accounts set up yet");
        placeholder.setWrapText(true);
        paymentAccountsListView.setPlaceholder(placeholder);
    }

    @Override
    protected void activate() {
        paymentAccountsListView.setItems(model.getPaymentAccounts());
        paymentAccountsListView.getSelectionModel().selectedItemProperty().addListener(paymentAccountChangeListener);
        addAccountButton.setOnAction(event -> addNewAccount());
        exportButton.setOnAction(event -> model.dataModel.exportAccounts());
        importButton.setOnAction(event -> model.dataModel.importAccounts());
    }

    @Override
    protected void deactivate() {
        paymentAccountsListView.getSelectionModel().selectedItemProperty().removeListener(paymentAccountChangeListener);
        addAccountButton.setOnAction(null);
        exportButton.setOnAction(null);
        importButton.setOnAction(null);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onSaveNewAccount(PaymentAccount paymentAccount) {
        TradeCurrency selectedTradeCurrency = paymentAccount.getSelectedTradeCurrency();
        String code = selectedTradeCurrency.getCode();
        if (selectedTradeCurrency instanceof CryptoCurrency && ((CryptoCurrency) selectedTradeCurrency).isAsset()) {
            new Popup().information("Please be sure that you follow the requirements for the usage of " +
                    selectedTradeCurrency.getCodeAndName() + " wallets as described on the " +
                    selectedTradeCurrency.getName() + " web page.\n" +
                    "Using wallets from centralized exchanges where you don't have your keys under your control or " +
                    "using a not compatible wallet software can lead to loss of the traded funds!\n" +
                    "The arbitrator is not a " + selectedTradeCurrency.getName() + " specialist and cannot help in such cases.")
                    .closeButtonText("I understand and confirm that I know which wallet I need to use.")
                    .show();
        }

        if (code.equals("XMR")) {
            new Popup().information("If you want to trade XMR on Bitsquare please be sure you understand and fulfill " +
                    "the following requirements:\n\n" +
                    "For sending XMR you need to use the Monero simple wallet with the " +
                    "store-tx-info flag enabled (default in new versions).\n" +
                    "Please be sure that you can access the tx key (use the get_tx_key command in simplewallet) as that " +
                    "would be required in case of a dispute to enable the arbitrator to verify the XMR transfer with " +
                    "the XMR checktx tool (http://xmr.llcoins.net/checktx.html).\n" +
                    "At normal block explorers the transfer is not verifiable.\n\n" +
                    "You need to provide the arbitrator the following data in case of a dispute:\n" +
                    "- The tx private key\n" +
                    "- The transaction hash\n" +
                    "- The recipient's public address\n\n" +
                    "If you cannot provide the above data or if you used an incompatible wallet it would result in losing the " +
                    "dispute case. The XMR sender is responsible to be able to verify the XMR transfer to the " +
                    "arbitrator in case of a dispute.\n\n" +
                    "There is no payment ID required, just the normal public address.\n\n" +
                    "If you are not sure about that process visit the Monero forum (https://forum.getmonero.org) to find more information.")
                    .closeButtonText("I understand")
                    .show();
        }

        if (!model.getPaymentAccounts().stream().filter(e -> {
            if (e.getAccountName() != null)
                return e.getAccountName().equals(paymentAccount.getAccountName());
            else
                return false;
        }).findAny().isPresent()) {
            model.onSaveNewAccount(paymentAccount);
            removeNewAccountForm();
        } else {
            new Popup().warning("That account name is already used in a saved account.\n" +
                    "Please use another name.").show();
        }
    }

    private void onCancelNewAccount() {
        removeNewAccountForm();
    }

    private void onDeleteAccount(PaymentAccount paymentAccount) {
        new Popup().warning("Do you really want to delete the selected account?")
                .actionButtonText("Yes")
                .onAction(() -> {
                    boolean isPaymentAccountUsed = model.onDeleteAccount(paymentAccount);
                    if (!isPaymentAccountUsed)
                        removeSelectAccountForm();
                    else
                        UserThread.runAfter(() -> {
                            new Popup().warning("You cannot delete that account because it is used in an " +
                                    "open offer or in a trade.").show();
                        }, 100, TimeUnit.MILLISECONDS);
                })
                .closeButtonText("Cancel")
                .show();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Base form
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void buildForm() {
        addTitledGroupBg(root, gridRow, 1, "Manage accounts");

        Tuple2<Label, ListView> tuple = addLabelListView(root, gridRow, "Your altcoin accounts:", Layout.FIRST_ROW_DISTANCE);
        GridPane.setValignment(tuple.first, VPos.TOP);
        paymentAccountsListView = tuple.second;
        paymentAccountsListView.setPrefHeight(2 * Layout.LIST_ROW_HEIGHT + 14);
        paymentAccountsListView.setCellFactory(new Callback<ListView<PaymentAccount>, ListCell<PaymentAccount>>() {
            @Override
            public ListCell<PaymentAccount> call(ListView<PaymentAccount> list) {
                return new ListCell<PaymentAccount>() {
                    final Label label = new Label();
                    final ImageView icon = ImageUtil.getImageViewById(ImageUtil.REMOVE_ICON);
                    final Button removeButton = new Button("", icon);
                    final AnchorPane pane = new AnchorPane(label, removeButton);

                    {
                        label.setLayoutY(5);
                        removeButton.setId("icon-button");
                        AnchorPane.setRightAnchor(removeButton, 0d);
                    }

                    @Override
                    public void updateItem(final PaymentAccount item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            label.setText(item.getAccountName());
                            removeButton.setOnAction(e -> onDeleteAccount(item));
                            setGraphic(pane);
                        } else {
                            setGraphic(null);
                        }
                    }
                };
            }
        });

        Tuple3<Button, Button, Button> tuple3 = add3ButtonsAfterGroup(root, ++gridRow, "Add new account", "Export Accounts", "Import Accounts");
        addAccountButton = tuple3.first;
        exportButton = tuple3.second;
        importButton = tuple3.third;
    }

    // Add new account form
    private void addNewAccount() {
        paymentAccountsListView.getSelectionModel().clearSelection();
        removeAccountRows();
        addAccountButton.setDisable(true);
        accountTitledGroupBg = addTitledGroupBg(root, ++gridRow, 1, "Create new account", Layout.GROUP_DISTANCE);

        if (paymentMethodForm != null) {
            FormBuilder.removeRowsFromGridPane(root, 3, paymentMethodForm.getGridRow() + 1);
            GridPane.setRowSpan(accountTitledGroupBg, paymentMethodForm.getRowSpan() + 1);
        }
        gridRow = 2;
        paymentMethodForm = getPaymentMethodForm(PaymentMethod.BLOCK_CHAINS);
        if (paymentMethodForm != null) {
            paymentMethodForm.addFormForAddAccount();
            gridRow = paymentMethodForm.getGridRow();
            Tuple2<Button, Button> tuple2 = add2ButtonsAfterGroup(root, ++gridRow, "Save new account", "Cancel");
            saveNewAccountButton = tuple2.first;
            saveNewAccountButton.setOnAction(event -> onSaveNewAccount(paymentMethodForm.getPaymentAccount()));
            saveNewAccountButton.disableProperty().bind(paymentMethodForm.allInputsValidProperty().not());
            Button cancelButton = tuple2.second;
            cancelButton.setOnAction(event -> onCancelNewAccount());
            GridPane.setRowSpan(accountTitledGroupBg, paymentMethodForm.getRowSpan() + 1);
        }
    }

    // Select account form
    private void onSelectAccount(PaymentAccount paymentAccount) {
        removeAccountRows();
        addAccountButton.setDisable(false);
        accountTitledGroupBg = addTitledGroupBg(root, ++gridRow, 1, "Selected account", Layout.GROUP_DISTANCE);
        paymentMethodForm = getPaymentMethodForm(paymentAccount);
        if (paymentMethodForm != null) {
            paymentMethodForm.addFormForDisplayAccount();
            gridRow = paymentMethodForm.getGridRow();
            Tuple2<Button, Button> tuple = add2ButtonsAfterGroup(root, ++gridRow, "Delete account", "Cancel");
            Button deleteAccountButton = tuple.first;
            deleteAccountButton.setOnAction(event -> onDeleteAccount(paymentMethodForm.getPaymentAccount()));
            Button cancelButton = tuple.second;
            cancelButton.setOnAction(event -> removeSelectAccountForm());
            GridPane.setRowSpan(accountTitledGroupBg, paymentMethodForm.getRowSpan());
            model.onSelectAccount(paymentAccount);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    private PaymentMethodForm getPaymentMethodForm(PaymentMethod paymentMethod) {
        return getPaymentMethodForm(PaymentAccountFactory.getPaymentAccount(paymentMethod));
    }

    private PaymentMethodForm getPaymentMethodForm(PaymentAccount paymentAccount) {
        return new CryptoCurrencyForm(paymentAccount, altCoinAddressValidator, inputValidator, root, gridRow, formatter);
    }

    private void removeNewAccountForm() {
        saveNewAccountButton.disableProperty().unbind();
        removeAccountRows();
        addAccountButton.setDisable(false);
    }

    private void removeSelectAccountForm() {
        FormBuilder.removeRowsFromGridPane(root, 2, gridRow);
        gridRow = 1;
        addAccountButton.setDisable(false);
        paymentAccountsListView.getSelectionModel().clearSelection();
    }


    private void removeAccountRows() {
        FormBuilder.removeRowsFromGridPane(root, 2, gridRow);
        gridRow = 1;
    }

}

