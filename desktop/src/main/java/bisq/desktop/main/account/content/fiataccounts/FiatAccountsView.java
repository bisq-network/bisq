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

package bisq.desktop.main.account.content.fiataccounts;

import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.TitledGroupBg;
import bisq.desktop.components.paymentmethods.AdvancedCashForm;
import bisq.desktop.components.paymentmethods.AliPayForm;
import bisq.desktop.components.paymentmethods.CashDepositForm;
import bisq.desktop.components.paymentmethods.ChaseQuickPayForm;
import bisq.desktop.components.paymentmethods.ClearXchangeForm;
import bisq.desktop.components.paymentmethods.F2FForm;
import bisq.desktop.components.paymentmethods.FasterPaymentsForm;
import bisq.desktop.components.paymentmethods.HalCashForm;
import bisq.desktop.components.paymentmethods.InteracETransferForm;
import bisq.desktop.components.paymentmethods.MoneyBeamForm;
import bisq.desktop.components.paymentmethods.MoneyGramForm;
import bisq.desktop.components.paymentmethods.NationalBankForm;
import bisq.desktop.components.paymentmethods.PaymentMethodForm;
import bisq.desktop.components.paymentmethods.PerfectMoneyForm;
import bisq.desktop.components.paymentmethods.PopmoneyForm;
import bisq.desktop.components.paymentmethods.PromptPayForm;
import bisq.desktop.components.paymentmethods.RevolutForm;
import bisq.desktop.components.paymentmethods.SameBankForm;
import bisq.desktop.components.paymentmethods.SepaForm;
import bisq.desktop.components.paymentmethods.SepaInstantForm;
import bisq.desktop.components.paymentmethods.SpecificBankForm;
import bisq.desktop.components.paymentmethods.SwishForm;
import bisq.desktop.components.paymentmethods.USPostalMoneyOrderForm;
import bisq.desktop.components.paymentmethods.UpholdForm;
import bisq.desktop.components.paymentmethods.WeChatPayForm;
import bisq.desktop.components.paymentmethods.WesternUnionForm;
import bisq.desktop.main.account.content.PaymentAccountsView;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.util.FormBuilder;
import bisq.desktop.util.GUIUtil;
import bisq.desktop.util.Layout;
import bisq.desktop.util.validation.AdvancedCashValidator;
import bisq.desktop.util.validation.AliPayValidator;
import bisq.desktop.util.validation.BICValidator;
import bisq.desktop.util.validation.ChaseQuickPayValidator;
import bisq.desktop.util.validation.ClearXchangeValidator;
import bisq.desktop.util.validation.F2FValidator;
import bisq.desktop.util.validation.HalCashValidator;
import bisq.desktop.util.validation.IBANValidator;
import bisq.desktop.util.validation.InteracETransferValidator;
import bisq.desktop.util.validation.MoneyBeamValidator;
import bisq.desktop.util.validation.PerfectMoneyValidator;
import bisq.desktop.util.validation.PopmoneyValidator;
import bisq.desktop.util.validation.PromptPayValidator;
import bisq.desktop.util.validation.RevolutValidator;
import bisq.desktop.util.validation.SwishValidator;
import bisq.desktop.util.validation.USPostalMoneyOrderValidator;
import bisq.desktop.util.validation.UpholdValidator;
import bisq.desktop.util.validation.WeChatPayValidator;

import bisq.core.app.BisqEnvironment;
import bisq.core.locale.Res;
import bisq.core.payment.AccountAgeWitnessService;
import bisq.core.payment.CashDepositAccount;
import bisq.core.payment.ClearXchangeAccount;
import bisq.core.payment.F2FAccount;
import bisq.core.payment.HalCashAccount;
import bisq.core.payment.MoneyGramAccount;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.PaymentAccountFactory;
import bisq.core.payment.RevolutAccount;
import bisq.core.payment.USPostalMoneyOrderAccount;
import bisq.core.payment.WesternUnionAccount;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.util.BSFormatter;
import bisq.core.util.validation.InputValidator;

import bisq.common.util.Tuple2;
import bisq.common.util.Tuple3;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;

import javafx.stage.Stage;

import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import javafx.util.StringConverter;

import java.util.List;
import java.util.stream.Collectors;

import static bisq.desktop.util.FormBuilder.add2ButtonsAfterGroup;
import static bisq.desktop.util.FormBuilder.add3ButtonsAfterGroup;
import static bisq.desktop.util.FormBuilder.addTitledGroupBg;
import static bisq.desktop.util.FormBuilder.addTopLabelListView;

@FxmlView
public class FiatAccountsView extends PaymentAccountsView<GridPane, FiatAccountsViewModel> {

    private final IBANValidator ibanValidator;
    private final BICValidator bicValidator;
    private final InputValidator inputValidator;
    private final UpholdValidator upholdValidator;
    private final MoneyBeamValidator moneyBeamValidator;
    private final PopmoneyValidator popmoneyValidator;
    private final RevolutValidator revolutValidator;
    private final AliPayValidator aliPayValidator;
    private final PerfectMoneyValidator perfectMoneyValidator;
    private final SwishValidator swishValidator;
    private final ClearXchangeValidator clearXchangeValidator;
    private final ChaseQuickPayValidator chaseQuickPayValidator;
    private final InteracETransferValidator interacETransferValidator;
    private final USPostalMoneyOrderValidator usPostalMoneyOrderValidator;
    private final WeChatPayValidator weChatPayValidator;
    private final HalCashValidator halCashValidator;
    private final F2FValidator f2FValidator;
    private final PromptPayValidator promptPayValidator;
    private final AdvancedCashValidator advancedCashValidator;
    private final AccountAgeWitnessService accountAgeWitnessService;
    private final BSFormatter formatter;
    private ComboBox<PaymentMethod> paymentMethodComboBox;
    private PaymentMethodForm paymentMethodForm;
    private TitledGroupBg accountTitledGroupBg;
    private Button saveNewAccountButton;
    private int gridRow = 0;

    @Inject
    public FiatAccountsView(FiatAccountsViewModel model,
                            IBANValidator ibanValidator,
                            BICValidator bicValidator,
                            InputValidator inputValidator,
                            UpholdValidator upholdValidator,
                            MoneyBeamValidator moneyBeamValidator,
                            PopmoneyValidator popmoneyValidator,
                            RevolutValidator revolutValidator,
                            AliPayValidator aliPayValidator,
                            PerfectMoneyValidator perfectMoneyValidator,
                            SwishValidator swishValidator,
                            ClearXchangeValidator clearXchangeValidator,
                            ChaseQuickPayValidator chaseQuickPayValidator,
                            InteracETransferValidator interacETransferValidator,
                            USPostalMoneyOrderValidator usPostalMoneyOrderValidator,
                            WeChatPayValidator weChatPayValidator,
                            HalCashValidator halCashValidator,
                            F2FValidator f2FValidator,
                            PromptPayValidator promptPayValidator,
                            AdvancedCashValidator advancedCashValidator,
                            AccountAgeWitnessService accountAgeWitnessService,
                            BSFormatter formatter) {
        super(model);

        this.ibanValidator = ibanValidator;
        this.bicValidator = bicValidator;
        this.inputValidator = inputValidator;
        this.upholdValidator = upholdValidator;
        this.moneyBeamValidator = moneyBeamValidator;
        this.popmoneyValidator = popmoneyValidator;
        this.revolutValidator = revolutValidator;
        this.aliPayValidator = aliPayValidator;
        this.perfectMoneyValidator = perfectMoneyValidator;
        this.swishValidator = swishValidator;
        this.clearXchangeValidator = clearXchangeValidator;
        this.chaseQuickPayValidator = chaseQuickPayValidator;
        this.interacETransferValidator = interacETransferValidator;
        this.usPostalMoneyOrderValidator = usPostalMoneyOrderValidator;
        this.weChatPayValidator = weChatPayValidator;
        this.halCashValidator = halCashValidator;
        this.f2FValidator = f2FValidator;
        this.promptPayValidator = promptPayValidator;
        this.advancedCashValidator = advancedCashValidator;
        this.accountAgeWitnessService = accountAgeWitnessService;
        this.formatter = formatter;
    }

    @Override
    protected ObservableList<PaymentAccount> getPaymentAccounts() {
        return model.getPaymentAccounts();
    }

    @Override
    protected void importAccounts() {
        model.dataModel.importAccounts((Stage) root.getScene().getWindow());
    }

    @Override
    protected void exportAccounts() {
        model.dataModel.exportAccounts((Stage) root.getScene().getWindow());
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onSaveNewAccount(PaymentAccount paymentAccount) {
        Coin maxTradeLimitAsCoin = paymentAccount.getPaymentMethod().getMaxTradeLimitAsCoin("USD");
        Coin maxTradeLimitSecondMonth = maxTradeLimitAsCoin.divide(2L);
        Coin maxTradeLimitFirstMonth = maxTradeLimitAsCoin.divide(4L);
        if (paymentAccount instanceof F2FAccount) {
            new Popup<>().information(Res.get("payment.f2f.info"))
                    .width(700)
                    .closeButtonText(Res.get("payment.f2f.info.openURL"))
                    .onClose(() -> GUIUtil.openWebPage("https://docs.bisq.network/trading-rules.html#f2f-trading"))
                    .actionButtonText(Res.get("shared.iUnderstand"))
                    .onAction(() -> doSaveNewAccount(paymentAccount))
                    .show();
        } else if (paymentAccount instanceof HalCashAccount) {
            // HalCash has no chargeback risk so we don't show the text from payment.limits.info.
            new Popup<>().information(Res.get("payment.halCash.info"))
                    .width(700)
                    .closeButtonText(Res.get("shared.cancel"))
                    .actionButtonText(Res.get("shared.iUnderstand"))
                    .onAction(() -> doSaveNewAccount(paymentAccount))
                    .show();
        } else {
            new Popup<>().information(Res.get("payment.limits.info",
                    formatter.formatCoinWithCode(maxTradeLimitFirstMonth),
                    formatter.formatCoinWithCode(maxTradeLimitSecondMonth),
                    formatter.formatCoinWithCode(maxTradeLimitAsCoin)))
                    .width(700)
                    .closeButtonText(Res.get("shared.cancel"))
                    .actionButtonText(Res.get("shared.iUnderstand"))
                    .onAction(() -> {
                        final String currencyName = BisqEnvironment.getBaseCurrencyNetwork().getCurrencyName();
                        if (paymentAccount instanceof ClearXchangeAccount) {
                            new Popup<>().information(Res.get("payment.clearXchange.info", currencyName, currencyName))
                                    .width(900)
                                    .closeButtonText(Res.get("shared.cancel"))
                                    .actionButtonText(Res.get("shared.iConfirm"))
                                    .onAction(() -> doSaveNewAccount(paymentAccount))
                                    .show();
                        } else if (paymentAccount instanceof WesternUnionAccount) {
                            new Popup<>().information(Res.get("payment.westernUnion.info"))
                                    .width(700)
                                    .closeButtonText(Res.get("shared.cancel"))
                                    .actionButtonText(Res.get("shared.iUnderstand"))
                                    .onAction(() -> doSaveNewAccount(paymentAccount))
                                    .show();
                        } else if (paymentAccount instanceof MoneyGramAccount) {
                            new Popup<>().information(Res.get("payment.moneyGram.info"))
                                    .width(700)
                                    .closeButtonText(Res.get("shared.cancel"))
                                    .actionButtonText(Res.get("shared.iUnderstand"))
                                    .onAction(() -> doSaveNewAccount(paymentAccount))
                                    .show();
                        } else if (paymentAccount instanceof CashDepositAccount) {
                            new Popup<>().information(Res.get("payment.cashDeposit.info"))
                                    .width(700)
                                    .closeButtonText(Res.get("shared.cancel"))
                                    .actionButtonText(Res.get("shared.iConfirm"))
                                    .onAction(() -> doSaveNewAccount(paymentAccount))
                                    .show();
                        } else if (paymentAccount instanceof RevolutAccount) {
                            new Popup<>().information(Res.get("payment.revolut.info"))
                                    .width(700)
                                    .closeButtonText(Res.get("shared.cancel"))
                                    .actionButtonText(Res.get("shared.iConfirm"))
                                    .onAction(() -> doSaveNewAccount(paymentAccount))
                                    .show();
                        } else if (paymentAccount instanceof USPostalMoneyOrderAccount) {
                            new Popup<>().information(Res.get("payment.usPostalMoneyOrder.info"))
                                    .width(700)
                                    .closeButtonText(Res.get("shared.cancel"))
                                    .actionButtonText(Res.get("shared.iUnderstand"))
                                    .onAction(() -> doSaveNewAccount(paymentAccount))
                                    .show();
                        } else {
                            doSaveNewAccount(paymentAccount);
                        }
                    })
                    .show();
        }
    }

    private void doSaveNewAccount(PaymentAccount paymentAccount) {
        if (getPaymentAccounts().stream().noneMatch(e -> e.getAccountName() != null &&
                e.getAccountName().equals(paymentAccount.getAccountName()))) {
            model.onSaveNewAccount(paymentAccount);
            removeNewAccountForm();
        } else {
            new Popup<>().warning(Res.get("shared.accountNameAlreadyUsed")).show();
        }
    }

    private void onCancelNewAccount() {
        removeNewAccountForm();
    }

    protected boolean deleteAccountFromModel(PaymentAccount paymentAccount) {
        return model.onDeleteAccount(paymentAccount);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Base form
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void buildForm() {
        addTitledGroupBg(root, gridRow, 2, Res.get("shared.manageAccounts"));

        Tuple3<Label, ListView<PaymentAccount>, VBox> tuple = addTopLabelListView(root, gridRow, Res.get("account.fiat.yourFiatAccounts"), Layout.FIRST_ROW_DISTANCE);
        paymentAccountsListView = tuple.second;
        paymentAccountsListView.setPrefHeight(2 * Layout.LIST_ROW_HEIGHT + 14);
        setPaymentAccountsCellFactory();

        Tuple3<Button, Button, Button> tuple3 = add3ButtonsAfterGroup(root, ++gridRow, Res.get("shared.addNewAccount"),
                Res.get("shared.ExportAccounts"), Res.get("shared.importAccounts"));
        addAccountButton = tuple3.first;
        exportButton = tuple3.second;
        importButton = tuple3.third;
    }

    // Add new account form
    @Override
    protected void addNewAccount() {
        paymentAccountsListView.getSelectionModel().clearSelection();
        removeAccountRows();
        addAccountButton.setDisable(true);
        accountTitledGroupBg = addTitledGroupBg(root, ++gridRow, 2, Res.get("shared.createNewAccount"), Layout.GROUP_DISTANCE);
        paymentMethodComboBox = FormBuilder.addComboBox(root, gridRow, Res.get("shared.paymentMethod"), Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        paymentMethodComboBox.setPromptText(Res.get("shared.selectPaymentMethod"));
        paymentMethodComboBox.setVisibleRowCount(11);
        paymentMethodComboBox.setPrefWidth(250);
        List<PaymentMethod> list = PaymentMethod.getPaymentMethods().stream()
                .filter(paymentMethod -> !paymentMethod.isAsset())
                .collect(Collectors.toList());
        paymentMethodComboBox.setItems(FXCollections.observableArrayList(list));
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
    @Override
    protected void onSelectAccount(PaymentAccount paymentAccount) {
        removeAccountRows();
        addAccountButton.setDisable(false);
        accountTitledGroupBg = addTitledGroupBg(root, ++gridRow, 2, Res.get("shared.selectedAccount"), Layout.GROUP_DISTANCE);
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
            case PaymentMethod.UPHOLD_ID:
                return new UpholdForm(paymentAccount, accountAgeWitnessService, upholdValidator, inputValidator, root, gridRow, formatter);
            case PaymentMethod.MONEY_BEAM_ID:
                return new MoneyBeamForm(paymentAccount, accountAgeWitnessService, moneyBeamValidator, inputValidator, root, gridRow, formatter);
            case PaymentMethod.POPMONEY_ID:
                return new PopmoneyForm(paymentAccount, accountAgeWitnessService, popmoneyValidator, inputValidator, root, gridRow, formatter);
            case PaymentMethod.REVOLUT_ID:
                return new RevolutForm(paymentAccount, accountAgeWitnessService, revolutValidator, inputValidator, root, gridRow, formatter);
            case PaymentMethod.PERFECT_MONEY_ID:
                return new PerfectMoneyForm(paymentAccount, accountAgeWitnessService, perfectMoneyValidator, inputValidator, root, gridRow, formatter);
            case PaymentMethod.SEPA_ID:
                return new SepaForm(paymentAccount, accountAgeWitnessService, ibanValidator, bicValidator, inputValidator, root, gridRow, formatter);
            case PaymentMethod.SEPA_INSTANT_ID:
                return new SepaInstantForm(paymentAccount, accountAgeWitnessService, ibanValidator, bicValidator, inputValidator, root, gridRow, formatter);
            case PaymentMethod.FASTER_PAYMENTS_ID:
                return new FasterPaymentsForm(paymentAccount, accountAgeWitnessService, inputValidator, root, gridRow, formatter);
            case PaymentMethod.NATIONAL_BANK_ID:
                return new NationalBankForm(paymentAccount, accountAgeWitnessService, inputValidator, root, gridRow, formatter);
            case PaymentMethod.SAME_BANK_ID:
                return new SameBankForm(paymentAccount, accountAgeWitnessService, inputValidator, root, gridRow, formatter);
            case PaymentMethod.SPECIFIC_BANKS_ID:
                return new SpecificBankForm(paymentAccount, accountAgeWitnessService, inputValidator, root, gridRow, formatter);
            case PaymentMethod.ALI_PAY_ID:
                return new AliPayForm(paymentAccount, accountAgeWitnessService, aliPayValidator, inputValidator, root, gridRow, formatter);
            case PaymentMethod.WECHAT_PAY_ID:
                return new WeChatPayForm(paymentAccount, accountAgeWitnessService, weChatPayValidator, inputValidator, root, gridRow, formatter);
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
            case PaymentMethod.MONEY_GRAM_ID:
                return new MoneyGramForm(paymentAccount, accountAgeWitnessService, inputValidator, root, gridRow, formatter);
            case PaymentMethod.WESTERN_UNION_ID:
                return new WesternUnionForm(paymentAccount, accountAgeWitnessService, inputValidator, root, gridRow, formatter);
            case PaymentMethod.CASH_DEPOSIT_ID:
                return new CashDepositForm(paymentAccount, accountAgeWitnessService, inputValidator, root, gridRow, formatter);
            case PaymentMethod.HAL_CASH_ID:
                return new HalCashForm(paymentAccount, accountAgeWitnessService, halCashValidator, inputValidator, root, gridRow, formatter);
            case PaymentMethod.F2F_ID:
                return new F2FForm(paymentAccount, accountAgeWitnessService, f2FValidator, inputValidator, root, gridRow, formatter);
            case PaymentMethod.PROMPT_PAY_ID:
                return new PromptPayForm(paymentAccount, accountAgeWitnessService, promptPayValidator, inputValidator, root, gridRow, formatter);
            case PaymentMethod.ADVANCED_CASH_ID:
                return new AdvancedCashForm(paymentAccount, accountAgeWitnessService, advancedCashValidator, inputValidator, root, gridRow, formatter);
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

    @Override
    protected void removeSelectAccountForm() {
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

