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

package bisq.desktop.main.dao.bonding.lockup;

import bisq.desktop.common.view.ActivatableView;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.InputTextField;
import bisq.desktop.components.TitledGroupBg;
import bisq.desktop.main.dao.bonding.BondingViewUtils;
import bisq.desktop.main.dao.wallet.BsqBalanceUtil;
import bisq.desktop.util.FormBuilder;
import bisq.desktop.util.Layout;
import bisq.desktop.util.validation.BsqValidator;

import bisq.core.btc.listeners.BsqBalanceListener;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.dao.bonding.BondingConsensus;
import bisq.core.dao.bonding.lockup.LockupType;
import bisq.core.dao.governance.role.BondedRole;
import bisq.core.dao.governance.role.Role;
import bisq.core.locale.Res;
import bisq.core.util.BsqFormatter;
import bisq.core.util.validation.IntegerValidator;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;

import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.GridPane;

import javafx.beans.value.ChangeListener;

import javafx.collections.FXCollections;

import javafx.util.StringConverter;

import java.util.Arrays;

import static bisq.desktop.util.FormBuilder.addButtonAfterGroup;
import static bisq.desktop.util.FormBuilder.addInputTextField;
import static bisq.desktop.util.FormBuilder.addTitledGroupBg;

@FxmlView
public class LockupView extends ActivatableView<GridPane, Void> implements BsqBalanceListener {
    private final BsqWalletService bsqWalletService;
    private final BsqFormatter bsqFormatter;
    private final BsqBalanceUtil bsqBalanceUtil;
    private final BondingViewUtils bondingViewUtils;
    private final BsqValidator bsqValidator;
    private final DaoFacade daoFacade;
    private final IntegerValidator timeInputTextFieldValidator;

    private int gridRow = 0;
    private InputTextField amountInputTextField;
    private InputTextField timeInputTextField;
    private ComboBox<LockupType> lockupTypeComboBox;
    private ComboBox<BondedRole> bondedRolesComboBox;
    private Button lockupButton;
    private ChangeListener<Boolean> amountFocusOutListener, timeFocusOutListener;
    private ChangeListener<String> amountInputTextFieldListener, timeInputTextFieldListener;
    private ChangeListener<BondedRole> bondedRoleStateListener;
    private ChangeListener<LockupType> lockupTypeListener;
    private TitledGroupBg titledGroupBg;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private LockupView(BsqWalletService bsqWalletService,
                       BsqFormatter bsqFormatter,
                       BsqBalanceUtil bsqBalanceUtil,
                       BondingViewUtils bondingViewUtils,
                       BsqValidator bsqValidator,
                       DaoFacade daoFacade) {
        this.bsqWalletService = bsqWalletService;
        this.bsqFormatter = bsqFormatter;
        this.bsqBalanceUtil = bsqBalanceUtil;
        this.bondingViewUtils = bondingViewUtils;
        this.bsqValidator = bsqValidator;
        this.daoFacade = daoFacade;

        timeInputTextFieldValidator = new IntegerValidator();
        timeInputTextFieldValidator.setMinValue(BondingConsensus.getMinLockTime());
        timeInputTextFieldValidator.setMaxValue(BondingConsensus.getMaxLockTime());
    }

    @Override
    public void initialize() {
        gridRow = bsqBalanceUtil.addGroup(root, gridRow);

        int columnSpan = 3;
        titledGroupBg = addTitledGroupBg(root, ++gridRow, 3, Res.get("dao.bonding.lock.lockBSQ"),
                Layout.GROUP_DISTANCE);
        GridPane.setColumnSpan(titledGroupBg, columnSpan);

        amountInputTextField = addInputTextField(root, gridRow, Res.get("dao.bonding.lock.amount"),
                Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        //amountInputTextField.setPromptText(Res.get("dao.bonding.lock.setAmount", bsqFormatter.formatCoinWithCode(Restrictions.getMinNonDustOutput())));
        amountInputTextField.setValidator(bsqValidator);
        GridPane.setColumnSpan(amountInputTextField, columnSpan);

        timeInputTextField = FormBuilder.addInputTextField(root, ++gridRow, Res.get("dao.bonding.lock.time"));

       /* timeInputTextField.setPromptText(Res.get("dao.bonding.lock.setTime",
                String.valueOf(BondingConsensus.getMinLockTime()), String.valueOf(BondingConsensus.getMaxLockTime())));*/

        timeInputTextField.setValidator(timeInputTextFieldValidator);
        GridPane.setColumnSpan(timeInputTextField, columnSpan);

        lockupTypeComboBox = FormBuilder.<LockupType>addComboBox(root, ++gridRow, Res.get("dao.bonding.lock.type"));
        GridPane.setColumnSpan(lockupTypeComboBox, columnSpan);
        lockupTypeComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(LockupType lockupType) {
                return lockupType.getDisplayString();
            }

            @Override
            public LockupType fromString(String string) {
                return null;
            }
        });
        lockupTypeComboBox.setItems(FXCollections.observableArrayList(Arrays.asList(LockupType.values())));
        lockupTypeListener = (observable, oldValue, newValue) -> {
            if (newValue != null) {
                bondedRolesComboBox.getSelectionModel().clearSelection();
            }
            int lockupRows = 3;
            if (newValue == LockupType.BONDED_ROLE) {
                bondedRolesComboBox.setVisible(true);
                lockupRows++;

                bondedRolesComboBox.setItems(FXCollections.observableArrayList(daoFacade.getBondedRoleStates()));
            } else {
                bondedRolesComboBox.setVisible(false);
                bondedRolesComboBox.getItems().clear();
            }
            GridPane.setRowSpan(titledGroupBg, lockupRows);
            GridPane.setRowIndex(lockupButton, GridPane.getRowIndex(amountInputTextField) + lockupRows);
        };


        bondedRolesComboBox = FormBuilder.addComboBox(root, ++gridRow, Res.get("dao.bonding.lock.bondedRoles"));
        GridPane.setColumnSpan(bondedRolesComboBox, columnSpan);
        bondedRolesComboBox.setVisible(false);
        bondedRolesComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(BondedRole bondedRole) {
                return bondedRole.getRole().getDisplayString();
            }

            @Override
            public BondedRole fromString(String string) {
                return null;
            }
        });
        bondedRoleStateListener = (observable, oldValue, newValue) -> {
            if (newValue != null) {
                Role role = newValue.getRole();
                amountInputTextField.setText(bsqFormatter.formatCoin(Coin.valueOf(role.getBondedRoleType().getRequiredBond())));
                timeInputTextField.setText(String.valueOf(role.getBondedRoleType().getUnlockTimeInBlocks()));
                amountInputTextField.resetValidation();
                timeInputTextField.resetValidation();
                amountInputTextField.setEditable(false);
                timeInputTextField.setEditable(false);
            } else {
                amountInputTextField.clear();
                timeInputTextField.clear();
                amountInputTextField.resetValidation();
                timeInputTextField.resetValidation();
                amountInputTextField.setEditable(true);
                timeInputTextField.setEditable(true);
            }
        };

        lockupButton = addButtonAfterGroup(root, gridRow, Res.get("dao.bonding.lock.lockupButton"));
        lockupButton.setOnAction((event) -> {
            switch (lockupTypeComboBox.getValue()) {
                case BONDED_ROLE:
                    if (bondedRolesComboBox.getValue() != null) {
                        bondingViewUtils.lockupBondForBondedRole(bondedRolesComboBox.getValue().getRole(),
                                () -> bondedRolesComboBox.getSelectionModel().clearSelection());
                    }
                    break;
                case REPUTATION:
                    bondingViewUtils.lockupBondForReputation(bsqFormatter.parseToCoin(amountInputTextField.getText()),
                            Integer.parseInt(timeInputTextField.getText()),
                            () -> {
                                amountInputTextField.setText("");
                                timeInputTextField.setText("");
                            });
                    break;
                default:
                    log.error("Unknown lockup option=" + lockupTypeComboBox.getValue());
            }
        });

        amountFocusOutListener = (observable, oldValue, newValue) -> {
            if (!newValue) {
                updateButtonState();
                onUpdateBalances();
            }
        };
        timeFocusOutListener = (observable, oldValue, newValue) -> {
            if (!newValue) {
                updateButtonState();
                onUpdateBalances();
            }
        };
        amountInputTextFieldListener = (observable, oldValue, newValue) -> updateButtonState();
        timeInputTextFieldListener = (observable, oldValue, newValue) -> updateButtonState();
    }

    @Override
    protected void activate() {
        bsqBalanceUtil.activate();

        amountInputTextField.textProperty().addListener(amountInputTextFieldListener);
        timeInputTextField.textProperty().addListener(timeInputTextFieldListener);
        amountInputTextField.focusedProperty().addListener(amountFocusOutListener);
        timeInputTextField.focusedProperty().addListener(timeFocusOutListener);
        lockupTypeComboBox.getSelectionModel().selectedItemProperty().addListener(lockupTypeListener);
        bondedRolesComboBox.getSelectionModel().selectedItemProperty().addListener(bondedRoleStateListener);

        bsqWalletService.addBsqBalanceListener(this);

        lockupTypeComboBox.getSelectionModel().clearSelection();
        bondedRolesComboBox.getSelectionModel().clearSelection();

        onUpdateBalances();
    }

    @Override
    protected void deactivate() {
        bsqBalanceUtil.deactivate();

        amountInputTextField.textProperty().removeListener(amountInputTextFieldListener);
        timeInputTextField.textProperty().removeListener(timeInputTextFieldListener);
        amountInputTextField.focusedProperty().removeListener(amountFocusOutListener);
        timeInputTextField.focusedProperty().removeListener(timeFocusOutListener);
        lockupTypeComboBox.getSelectionModel().selectedItemProperty().removeListener(lockupTypeListener);
        bondedRolesComboBox.getSelectionModel().selectedItemProperty().removeListener(bondedRoleStateListener);

        bsqWalletService.removeBsqBalanceListener(this);
    }

    @Override
    public void onUpdateBalances(Coin confirmedBalance,
                                 Coin availableNonBsqBalance,
                                 Coin pendingBalance,
                                 Coin lockedForVotingBalance,
                                 Coin lockupBondsBalance,
                                 Coin unlockingBondsBalance) {
        bsqValidator.setAvailableBalance(confirmedBalance);
        boolean isValid = bsqValidator.validate(amountInputTextField.getText()).isValid;
        lockupButton.setDisable(!isValid);
    }

    private void onUpdateBalances() {
        onUpdateBalances(bsqWalletService.getAvailableBalance(),
                bsqWalletService.getAvailableNonBsqBalance(),
                bsqWalletService.getUnverifiedBalance(),
                bsqWalletService.getLockedForVotingBalance(),
                bsqWalletService.getLockupBondsBalance(),
                bsqWalletService.getUnlockingBondsBalance());
    }

    private void updateButtonState() {
        lockupButton.setDisable(!bsqValidator.validate(amountInputTextField.getText()).isValid ||
                !timeInputTextFieldValidator.validate(timeInputTextField.getText()).isValid ||
                bondedRolesComboBox.getSelectionModel().getSelectedItem() == null ||
                lockupTypeComboBox.getSelectionModel().getSelectedItem() == null);
    }
}
