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

package bisq.desktop.main.dao.bonding.reputation;

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
import bisq.core.dao.governance.bond.BondConsensus;
import bisq.core.locale.Res;
import bisq.core.util.BsqFormatter;
import bisq.core.util.validation.HexStringValidator;
import bisq.core.util.validation.IntegerValidator;

import bisq.common.crypto.Hash;
import bisq.common.util.Utilities;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;

import com.google.common.base.Charsets;

import javafx.scene.control.Button;
import javafx.scene.layout.GridPane;

import javafx.beans.value.ChangeListener;

import java.util.UUID;

import static bisq.desktop.util.FormBuilder.addButtonAfterGroup;
import static bisq.desktop.util.FormBuilder.addInputTextField;
import static bisq.desktop.util.FormBuilder.addTitledGroupBg;

@FxmlView
public class ReputationView extends ActivatableView<GridPane, Void> implements BsqBalanceListener {
    private final BsqWalletService bsqWalletService;
    private final BsqFormatter bsqFormatter;
    private final BsqBalanceUtil bsqBalanceUtil;
    private final BondingViewUtils bondingViewUtils;
    private final HexStringValidator hexStringValidator;
    private final BsqValidator bsqValidator;
    private final IntegerValidator timeInputTextFieldValidator;

    private int gridRow = 0;
    private InputTextField amountInputTextField, timeInputTextField, saltInputTextField;
    private Button lockupButton;
    private ChangeListener<Boolean> amountFocusOutListener, timeFocusOutListener, saltFocusOutListener;
    private ChangeListener<String> amountInputTextFieldListener, timeInputTextFieldListener, saltInputTextFieldListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private ReputationView(BsqWalletService bsqWalletService,
                           BsqFormatter bsqFormatter,
                           BsqBalanceUtil bsqBalanceUtil,
                           BondingViewUtils bondingViewUtils,
                           HexStringValidator hexStringValidator,
                           BsqValidator bsqValidator) {
        this.bsqWalletService = bsqWalletService;
        this.bsqFormatter = bsqFormatter;
        this.bsqBalanceUtil = bsqBalanceUtil;
        this.bondingViewUtils = bondingViewUtils;
        this.hexStringValidator = hexStringValidator;
        this.bsqValidator = bsqValidator;

        timeInputTextFieldValidator = new IntegerValidator();
        timeInputTextFieldValidator.setMinValue(BondConsensus.getMinLockTime());
        timeInputTextFieldValidator.setMaxValue(BondConsensus.getMaxLockTime());
    }

    @Override
    public void initialize() {
        gridRow = bsqBalanceUtil.addGroup(root, gridRow);

        int columnSpan = 3;
        TitledGroupBg titledGroupBg = addTitledGroupBg(root, ++gridRow, 3, Res.get("dao.bonding.lock.lockBSQ"),
                Layout.GROUP_DISTANCE);
        GridPane.setColumnSpan(titledGroupBg, columnSpan);

        amountInputTextField = addInputTextField(root, gridRow, Res.get("dao.bonding.lock.amount"),
                Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        //amountInputTextField.setPromptText(Res.get("dao.bonding.lock.setAmount", bsqFormatter.formatCoinWithCode(Restrictions.getMinNonDustOutput())));
        amountInputTextField.setValidator(bsqValidator);
        GridPane.setColumnSpan(amountInputTextField, columnSpan);

        timeInputTextField = FormBuilder.addInputTextField(root, ++gridRow, Res.get("dao.bonding.lock.time"));

        saltInputTextField = FormBuilder.addInputTextField(root, ++gridRow, Res.get("dao.bonding.lock.salt"));
        GridPane.setColumnSpan(saltInputTextField, columnSpan);
        saltInputTextField.setValidator(hexStringValidator);

       /* timeInputTextField.setPromptText(Res.get("dao.bonding.lock.setTime",
                String.valueOf(BondConsensus.getMinLockTime()), String.valueOf(BondConsensus.getMaxLockTime())));*/

        timeInputTextField.setValidator(timeInputTextFieldValidator);
        GridPane.setColumnSpan(timeInputTextField, columnSpan);

        lockupButton = addButtonAfterGroup(root, ++gridRow, Res.get("dao.bonding.lock.lockupButton"));
        lockupButton.setOnAction((event) -> {
            Coin lockupAmount = bsqFormatter.parseToCoin(amountInputTextField.getText());
            int lockupTime = Integer.parseInt(timeInputTextField.getText());
            byte[] salt = Utilities.decodeFromHex(saltInputTextField.getText());
            bondingViewUtils.lockupBondForReputation(lockupAmount,
                    lockupTime,
                    salt,
                    txId -> {
                        amountInputTextField.setText("");
                        timeInputTextField.setText("");
                    });
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
        saltFocusOutListener = (observable, oldValue, newValue) -> {
            if (!newValue) {
                updateButtonState();
                onUpdateBalances();
            }
        };
        amountInputTextFieldListener = (observable, oldValue, newValue) -> updateButtonState();
        timeInputTextFieldListener = (observable, oldValue, newValue) -> updateButtonState();
        saltInputTextFieldListener = (observable, oldValue, newValue) -> updateButtonState();
    }

    @Override
    protected void activate() {
        bsqBalanceUtil.activate();

        amountInputTextField.textProperty().addListener(amountInputTextFieldListener);
        amountInputTextField.focusedProperty().addListener(amountFocusOutListener);

        timeInputTextField.textProperty().addListener(timeInputTextFieldListener);
        timeInputTextField.focusedProperty().addListener(timeFocusOutListener);

        saltInputTextField.textProperty().addListener(saltInputTextFieldListener);
        saltInputTextField.focusedProperty().addListener(saltFocusOutListener);

        bsqWalletService.addBsqBalanceListener(this);

        byte[] randomBytes = UUID.randomUUID().toString().getBytes(Charsets.UTF_8);
        byte[] hashOfRandomBytes = Hash.getSha256Ripemd160hash(randomBytes);
        saltInputTextField.setText(Utilities.bytesAsHexString(hashOfRandomBytes));

        onUpdateBalances();
    }

    @Override
    protected void deactivate() {
        bsqBalanceUtil.deactivate();

        amountInputTextField.textProperty().removeListener(amountInputTextFieldListener);
        amountInputTextField.focusedProperty().removeListener(amountFocusOutListener);

        timeInputTextField.textProperty().removeListener(timeInputTextFieldListener);
        timeInputTextField.focusedProperty().removeListener(timeFocusOutListener);

        saltInputTextField.textProperty().removeListener(saltInputTextFieldListener);
        saltInputTextField.focusedProperty().removeListener(saltFocusOutListener);

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
        boolean isValid = bsqValidator.validate(amountInputTextField.getText()).isValid &&
                timeInputTextFieldValidator.validate(timeInputTextField.getText()).isValid &&
                hexStringValidator.validate(saltInputTextField.getText()).isValid;

        lockupButton.setDisable(!isValid);
    }
}
