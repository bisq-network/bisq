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

package io.bisq.gui.main.account.content.fiataccounts;

import io.bisq.common.UserThread;
import io.bisq.common.locale.Res;
import io.bisq.common.util.Tuple2;
import io.bisq.common.util.Tuple3;
import io.bisq.core.app.BisqEnvironment;
import io.bisq.core.payment.*;
import io.bisq.core.payment.payload.PaymentMethod;
import io.bisq.gui.common.view.ActivatableViewAndModel;
import io.bisq.gui.common.view.FxmlView;
import io.bisq.gui.components.TitledGroupBg;
import io.bisq.gui.components.paymentmethods.*;
import io.bisq.gui.main.overlays.popups.Popup;
import io.bisq.gui.util.BSFormatter;
import io.bisq.gui.util.FormBuilder;
import io.bisq.gui.util.ImageUtil;
import io.bisq.gui.util.Layout;
import io.bisq.gui.util.validation.*;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.geometry.VPos;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.scene.text.TextAlignment;
import javafx.util.Callback;
import javafx.util.StringConverter;

import javax.inject.Inject;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static io.bisq.gui.util.FormBuilder.*;

@FxmlView
public class FiatAccountsView extends ActivatableViewAndModel<GridPane, FiatAccountsViewModel> {

    private ListView<PaymentAccount> paymentAccountsListView;
    private ComboBox<PaymentMethod> paymentMethodComboBox;

    private final IBANValidator ibanValidator;
    private final BICValidator bicValidator;
    private final InputValidator inputValidator;
    private final OKPayValidator okPayValidator;
    private final AliPayValidator aliPayValidator;
    private final PerfectMoneyValidator perfectMoneyValidator;
    private final SwishValidator swishValidator;
    private final ClearXchangeValidator clearXchangeValidator;
    private final ChaseQuickPayValidator chaseQuickPayValidator;
    private final InteracETransferValidator interacETransferValidator;
    private final USPostalMoneyOrderValidator usPostalMoneyOrderValidator;

    private final AccountAgeWitnessService accountAgeWitnessService;
    private final BSFormatter formatter;

    private PaymentMethodForm paymentMethodForm;
    private TitledGroupBg accountTitledGroupBg;
    private Button addAccountButton, saveNewAccountButton, exportButton, importButton;
    private int gridRow = 0;
    private ChangeListener<PaymentAccount> paymentAccountChangeListener;

    @Inject
    public FiatAccountsView(FiatAccountsViewModel model,
                            IBANValidator ibanValidator,
                            BICValidator bicValidator,
                            InputValidator inputValidator,
                            OKPayValidator okPayValidator,
                            AliPayValidator aliPayValidator,
                            PerfectMoneyValidator perfectMoneyValidator,
                            SwishValidator swishValidator,
                            ClearXchangeValidator clearXchangeValidator,
                            ChaseQuickPayValidator chaseQuickPayValidator,
                            InteracETransferValidator interacETransferValidator,
                            USPostalMoneyOrderValidator usPostalMoneyOrderValidator,
                            AccountAgeWitnessService accountAgeWitnessService,
                            BSFormatter formatter) {
        super(model);

        this.ibanValidator = ibanValidator;
        this.bicValidator = bicValidator;
        this.inputValidator = inputValidator;
        this.okPayValidator = okPayValidator;
        this.aliPayValidator = aliPayValidator;
        this.perfectMoneyValidator = perfectMoneyValidator;
        this.swishValidator = swishValidator;
        this.clearXchangeValidator = clearXchangeValidator;
        this.chaseQuickPayValidator = chaseQuickPayValidator;
        this.interacETransferValidator = interacETransferValidator;
        this.usPostalMoneyOrderValidator = usPostalMoneyOrderValidator;
        this.accountAgeWitnessService = accountAgeWitnessService;
        this.formatter = formatter;
    }

    @Override
    public void initialize() {
        buildForm();
        paymentAccountChangeListener = (observable, oldValue, newValue) -> {
            if (newValue != null)
                onSelectAccount(newValue);
        };
        Label placeholder = new Label(Res.get("shared.noAccountsSetupYet"));
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
        final String currencyName = BisqEnvironment.getBaseCurrencyNetwork().getCurrencyName();
        if (paymentAccount instanceof ClearXchangeAccount) {
            new Popup<>().information(Res.get("payment.clearXchange.info", currencyName, currencyName))
                    .width(900)
                    .closeButtonText(Res.get("shared.cancel"))
                    .actionButtonText(Res.get("shared.iConfirm"))
                    .onAction(() -> doSaveNewAccount(paymentAccount))
                    .show();
        } else if (paymentAccount instanceof WesternUnionAccount) {
            new Popup<>().information(Res.get("payment.westernUnion.info", currencyName, currencyName))
                    .width(700)
                    .closeButtonText(Res.get("shared.cancel"))
                    .actionButtonText(Res.get("shared.iUnderstand"))
                    .onAction(() -> doSaveNewAccount(paymentAccount))
                    .show();
        } else {
            doSaveNewAccount(paymentAccount);
        }
    }

    private void doSaveNewAccount(PaymentAccount paymentAccount) {
        if (!model.getPaymentAccounts().stream().filter(e -> e.getAccountName() != null &&
                e.getAccountName().equals(paymentAccount.getAccountName()))
                .findAny()
                .isPresent()) {

            // TODO apply salt if user provided it
            // testing
            // paymentAccount.setSaltAsHex("a25b65f612e49ba6c4ab80a95fc9b723bcff9e7c6bd06f020d4bdffdac060eed");

            model.onSaveNewAccount(paymentAccount);
            removeNewAccountForm();
        } else {
            new Popup<>().warning(Res.get("shared.accountNameAlreadyUsed")).show();
        }
    }

    private void onCancelNewAccount() {
        removeNewAccountForm();
    }

    private void onDeleteAccount(PaymentAccount paymentAccount) {
        new Popup<>().warning(Res.get("shared.askConfirmDeleteAccount"))
                .actionButtonText(Res.get("shared.yes"))
                .onAction(() -> {
                    boolean isPaymentAccountUsed = model.onDeleteAccount(paymentAccount);
                    if (!isPaymentAccountUsed)
                        removeSelectAccountForm();
                    else
                        UserThread.runAfter(() -> new Popup<>().warning(
                                Res.get("shared.cannotDeleteAccount"))
                                .show(), 100, TimeUnit.MILLISECONDS);
                })
                .closeButtonText(Res.get("shared.cancel"))
                .show();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Base form
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void buildForm() {
        addTitledGroupBg(root, gridRow, 1, Res.get("shared.manageAccounts"));

        Tuple2<Label, ListView> tuple = addLabelListView(root, gridRow, Res.get("account.fiat.yourFiatAccounts"), Layout.FIRST_ROW_DISTANCE);
        GridPane.setValignment(tuple.first, VPos.TOP);
        tuple.first.setTextAlignment(TextAlignment.RIGHT);
        //noinspection unchecked
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

        Tuple3<Button, Button, Button> tuple3 = add3ButtonsAfterGroup(root, ++gridRow, Res.get("shared.addNewAccount"),
                Res.get("shared.ExportAccounts"), Res.get("shared.importAccounts"));
        addAccountButton = tuple3.first;
        exportButton = tuple3.second;
        importButton = tuple3.third;
    }

    // Add new account form
    private void addNewAccount() {
        paymentAccountsListView.getSelectionModel().clearSelection();
        removeAccountRows();
        addAccountButton.setDisable(true);
        accountTitledGroupBg = addTitledGroupBg(root, ++gridRow, 1, Res.get("shared.createNewAccount"), Layout.GROUP_DISTANCE);
        //noinspection unchecked
        paymentMethodComboBox = addLabelComboBox(root, gridRow, Res.getWithCol("shared.paymentMethod"), Layout.FIRST_ROW_AND_GROUP_DISTANCE).second;
        paymentMethodComboBox.setPromptText(Res.get("shared.selectPaymentMethod"));
        paymentMethodComboBox.setVisibleRowCount(15);
        paymentMethodComboBox.setPrefWidth(250);
        List<PaymentMethod> list = PaymentMethod.getAllValues().stream()
                .filter(paymentMethod -> !paymentMethod.getId().equals(PaymentMethod.BLOCK_CHAINS_ID))
                .collect(Collectors.toList());
        paymentMethodComboBox.setItems(FXCollections.observableArrayList(list));
        paymentMethodComboBox.setConverter(new StringConverter<PaymentMethod>() {
            @Override
            public String toString(PaymentMethod paymentMethod) {
                return paymentMethod != null ? Res.get(paymentMethod.getId()) : "";
            }

            @Override
            public PaymentMethod fromString(String s) {
                return null;
            }
        });
        paymentMethodComboBox.setOnAction(e -> {
            if (paymentMethodForm != null) {
                FormBuilder.removeRowsFromGridPane(root, 3, paymentMethodForm.getGridRow() + 1);
                GridPane.setRowSpan(accountTitledGroupBg, paymentMethodForm.getRowSpan() + 1);
            }
            gridRow = 2;
            paymentMethodForm = getPaymentMethodForm(paymentMethodComboBox.getSelectionModel().getSelectedItem());
            if (paymentMethodForm != null) {
                paymentMethodForm.addFormForAddAccount();
                gridRow = paymentMethodForm.getGridRow();
                Tuple2<Button, Button> tuple2 = add2ButtonsAfterGroup(root, ++gridRow, Res.get("shared.saveNewAccount"), Res.get("shared.cancel"));
                saveNewAccountButton = tuple2.first;
                saveNewAccountButton.setOnAction(event -> onSaveNewAccount(paymentMethodForm.getPaymentAccount()));
                saveNewAccountButton.disableProperty().bind(paymentMethodForm.allInputsValidProperty().not());
                Button cancelButton = tuple2.second;
                cancelButton.setOnAction(event -> onCancelNewAccount());
                GridPane.setRowSpan(accountTitledGroupBg, paymentMethodForm.getRowSpan() + 1);
            }
        });
    }

    // Select account form
    private void onSelectAccount(PaymentAccount paymentAccount) {
        removeAccountRows();
        addAccountButton.setDisable(false);
        accountTitledGroupBg = addTitledGroupBg(root, ++gridRow, 1, Res.get("shared.selectedAccount"), Layout.GROUP_DISTANCE);
        paymentMethodForm = getPaymentMethodForm(paymentAccount);
        if (paymentMethodForm != null) {
            paymentMethodForm.addFormForDisplayAccount();
            gridRow = paymentMethodForm.getGridRow();
            Tuple2<Button, Button> tuple = add2ButtonsAfterGroup(root, ++gridRow, Res.get("shared.deleteAccount"), Res.get("shared.cancel"));
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

    private PaymentMethodForm getPaymentMethodForm(PaymentAccount paymentAccount) {
        return getPaymentMethodForm(paymentAccount.getPaymentMethod(), paymentAccount);
    }

    private PaymentMethodForm getPaymentMethodForm(PaymentMethod paymentMethod) {
        final PaymentAccount paymentAccount = PaymentAccountFactory.getPaymentAccount(paymentMethod);
        paymentAccount.init();
        return getPaymentMethodForm(paymentMethod, paymentAccount);
    }

    private PaymentMethodForm getPaymentMethodForm(PaymentMethod paymentMethod, PaymentAccount paymentAccount) {
        switch (paymentMethod.getId()) {
            case PaymentMethod.OK_PAY_ID:
                return new OKPayForm(paymentAccount, accountAgeWitnessService, okPayValidator, inputValidator, root, gridRow, formatter);
            case PaymentMethod.PERFECT_MONEY_ID:
                return new PerfectMoneyForm(paymentAccount, accountAgeWitnessService, perfectMoneyValidator, inputValidator, root, gridRow, formatter);
            case PaymentMethod.SEPA_ID:
                return new SepaForm(paymentAccount, accountAgeWitnessService, ibanValidator, bicValidator, inputValidator, root, gridRow, formatter);
            case PaymentMethod.FASTER_PAYMENTS_ID:
                return new FasterPaymentsForm(paymentAccount, accountAgeWitnessService, inputValidator, root, gridRow, formatter);
            case PaymentMethod.NATIONAL_BANK_ID:
                return new NationalBankForm(paymentAccount, accountAgeWitnessService, inputValidator, root, gridRow, formatter, this::onCancelNewAccount);
            case PaymentMethod.SAME_BANK_ID:
                return new SameBankForm(paymentAccount, accountAgeWitnessService, inputValidator, root, gridRow, formatter, this::onCancelNewAccount);
            case PaymentMethod.SPECIFIC_BANKS_ID:
                return new SpecificBankForm(paymentAccount, accountAgeWitnessService, inputValidator, root, gridRow, formatter, this::onCancelNewAccount);
            case PaymentMethod.ALI_PAY_ID:
                return new AliPayForm(paymentAccount, accountAgeWitnessService, aliPayValidator, inputValidator, root, gridRow, formatter);
            case PaymentMethod.SWISH_ID:
                return new SwishForm(paymentAccount, accountAgeWitnessService, swishValidator, inputValidator, root, gridRow, formatter);
            case PaymentMethod.CLEAR_X_CHANGE_ID:
                return new ClearXchangeForm(paymentAccount, accountAgeWitnessService, clearXchangeValidator, inputValidator, root, gridRow, formatter);
            case PaymentMethod.CHASE_QUICK_PAY_ID:
                return new ChaseQuickPayForm(paymentAccount, accountAgeWitnessService, chaseQuickPayValidator, inputValidator, root, gridRow, formatter);
            case PaymentMethod.INTERAC_E_TRANSFER_ID:
                return new InteracETransferForm(paymentAccount, accountAgeWitnessService, interacETransferValidator, inputValidator, root, gridRow, formatter);
            case PaymentMethod.US_POSTAL_MONEY_ORDER_ID:
                return new USPostalMoneyOrderForm(paymentAccount, accountAgeWitnessService, usPostalMoneyOrderValidator, inputValidator, root, gridRow, formatter);
            case PaymentMethod.WESTERN_UNION_ID:
                return new WesternUnionForm(paymentAccount, accountAgeWitnessService, inputValidator, root, gridRow, formatter);
            case PaymentMethod.CASH_DEPOSIT_ID:
                return new CashDepositForm(paymentAccount, accountAgeWitnessService, inputValidator, root, gridRow, formatter);
            default:
                log.error("Not supported PaymentMethod: " + paymentMethod);
                return null;
        }
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

