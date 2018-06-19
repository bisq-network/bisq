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

package bisq.desktop.main.dao.bonding.Lock;

import bisq.desktop.Navigation;
import bisq.desktop.common.view.ActivatableView;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.InputTextField;
import bisq.desktop.main.MainView;
import bisq.desktop.main.dao.wallet.BsqBalanceUtil;
import bisq.desktop.main.funds.FundsView;
import bisq.desktop.main.funds.deposit.DepositView;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.util.BSFormatter;
import bisq.desktop.util.BsqFormatter;
import bisq.desktop.util.GUIUtil;
import bisq.desktop.util.Layout;
import bisq.desktop.util.validation.BsqAddressValidator;
import bisq.desktop.util.validation.BsqValidator;

import bisq.core.btc.Restrictions;
import bisq.core.btc.wallet.BsqBalanceListener;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.WalletsManager;
import bisq.core.btc.wallet.WalletsSetup;
import bisq.core.dao.DaoFacade;
import bisq.core.dao.voting.proposal.param.Param;
import bisq.core.locale.Res;

import bisq.network.p2p.P2PService;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;

import javax.inject.Inject;

import javafx.scene.control.Button;
import javafx.scene.layout.GridPane;

import javafx.beans.value.ChangeListener;

import static bisq.desktop.util.FormBuilder.addButtonAfterGroup;
import static bisq.desktop.util.FormBuilder.addLabelInputTextField;
import static bisq.desktop.util.FormBuilder.addTitledGroupBg;

@FxmlView
public class LockupBSQView extends ActivatableView<GridPane, Void> implements BsqBalanceListener {
    private final BsqWalletService bsqWalletService;
    private final WalletsSetup walletsSetup;
    private final P2PService p2PService;
    private final BsqFormatter bsqFormatter;
    private final Navigation navigation;
    private final BsqBalanceUtil bsqBalanceUtil;
    private final BsqValidator bsqValidator;
    private final DaoFacade daoFacade;

    private int gridRow = 0;
    private InputTextField amountInputTextField;
    private InputTextField timeInputTextField;
    private Button lockupButton;
    private ChangeListener<Boolean> focusOutListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private LockupBSQView(BsqWalletService bsqWalletService,
                          BtcWalletService btcWalletService,
                          WalletsManager walletsManager,
                          WalletsSetup walletsSetup,
                          P2PService p2PService,
                          BsqFormatter bsqFormatter,
                          BSFormatter btcFormatter,
                          Navigation navigation,
                          BsqBalanceUtil bsqBalanceUtil,
                          BsqValidator bsqValidator,
                          BsqAddressValidator bsqAddressValidator,
                          DaoFacade daoFacade) {
        this.bsqWalletService = bsqWalletService;
        this.walletsSetup = walletsSetup;
        this.p2PService = p2PService;
        this.bsqFormatter = bsqFormatter;
        this.navigation = navigation;
        this.bsqBalanceUtil = bsqBalanceUtil;
        this.bsqValidator = bsqValidator;
        this.daoFacade = daoFacade;
    }

    @Override
    public void initialize() {
        // TODO: Show balance locked up in bonds
        gridRow = bsqBalanceUtil.addGroup(root, gridRow);

        addTitledGroupBg(root, ++gridRow, 4, Res.get("dao.bonding.lock.lockBSQ"), Layout.GROUP_DISTANCE);

        amountInputTextField = addLabelInputTextField(root, gridRow, Res.get("dao.bonding.lock.amount"),
                Layout.FIRST_ROW_AND_GROUP_DISTANCE).second;
        amountInputTextField.setPromptText(Res.get("dao.bonding.lock.setAmount", Restrictions.getMinNonDustOutput().value));
        amountInputTextField.setValidator(bsqValidator);
        timeInputTextField = addLabelInputTextField(root, ++gridRow, Res.get("dao.bonding.lock.time"), Layout.GRID_GAP).second;
        timeInputTextField.setPromptText(Res.get("dao.bonding.lock.setTime",
                Param.LOCKTIME_MIN.getDefaultValue(), Param.LOCKTIME_MAX.getDefaultValue()));
        // TODO: add some int validator
//        timeInputTextField.setValidator(bsqValidator);

        focusOutListener = (observable, oldValue, newValue) -> {
            if (!newValue)
                onUpdateBalances(bsqWalletService.getAvailableBalance(), bsqWalletService.getPendingBalance(),
                        bsqWalletService.getLockedForVotingBalance(), bsqWalletService.getLockedInBondsBalance(),
                        bsqWalletService.getUnlockingBondsBalance());
        };

        lockupButton = addButtonAfterGroup(root, ++gridRow, Res.get("dao.bonding.lock.lockupButton"));

        lockupButton.setOnAction((event) -> {
            if (GUIUtil.isReadyForTxBroadcast(p2PService, walletsSetup)) {
                Coin lockupAmount = bsqFormatter.parseToCoin(amountInputTextField.getText());
                int lockupTime = Integer.parseInt(timeInputTextField.getText());
                try {
                    new Popup<>().headLine(Res.get("dao.bonding.lock.sendFunds.headline"))
                            .confirmation(Res.get("dao.bonding.lock.sendFunds.details",
                                    bsqFormatter.formatCoinWithCode(lockupAmount),
                                    lockupTime
                            ))
                            .actionButtonText(Res.get("shared.yes"))
                            .onAction(() -> {
                                daoFacade.publishLockupTx(lockupAmount,
                                        lockupTime,
                                        () -> {
                                            new Popup<>().confirmation(Res.get("dao.tx.published.success")).show();
                                        },
                                        errorMessage -> new Popup<>().warning(errorMessage.toString()).show()
                                );
                                amountInputTextField.setText("");
                            })
                            .closeButtonText(Res.get("shared.cancel"))
                            .show();
                } catch (Throwable t) {
                    if (t instanceof InsufficientMoneyException) {
                        final Coin missingCoin = ((InsufficientMoneyException) t).missing;
                        final String missing = missingCoin != null ? missingCoin.toFriendlyString() : "null";
                        //noinspection unchecked
                        new Popup<>().warning(Res.get("popup.warning.insufficientBtcFundsForBsqTx", missing))
                                .actionButtonTextWithGoTo("navigation.funds.depositFunds")
                                .onAction(() -> navigation.navigateTo(MainView.class, FundsView.class, DepositView.class))
                                .show();
                    } else {
                        log.error(t.toString());
                        t.printStackTrace();
                        new Popup<>().warning(t.getMessage()).show();
                    }
                }
            } else {
                GUIUtil.showNotReadyForTxBroadcastPopups(p2PService, walletsSetup);
            }
        });
    }

    @Override
    protected void activate() {
        bsqBalanceUtil.activate();
        amountInputTextField.focusedProperty().addListener(focusOutListener);
        bsqWalletService.addBsqBalanceListener(this);
        onUpdateBalances(bsqWalletService.getAvailableBalance(), bsqWalletService.getPendingBalance(),
                bsqWalletService.getLockedForVotingBalance(), bsqWalletService.getLockedInBondsBalance(),
                bsqWalletService.getUnlockingBondsBalance());
    }

    @Override
    protected void deactivate() {
        bsqBalanceUtil.deactivate();
        amountInputTextField.focusedProperty().removeListener(focusOutListener);
        bsqWalletService.removeBsqBalanceListener(this);
    }

    @Override
    public void onUpdateBalances(Coin confirmedBalance,
                                 Coin pendingBalance,
                                 Coin lockedForVotingBalance,
                                 Coin lockedInBondsBalance,
                                 Coin unlockingBondsBalance) {
        bsqValidator.setAvailableBalance(confirmedBalance);
        boolean isValid = bsqValidator.validate(amountInputTextField.getText()).isValid;
        lockupButton.setDisable(!isValid);
    }
}
