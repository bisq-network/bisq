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

package bisq.desktop.main.dao.bonding;

import bisq.desktop.Navigation;
import bisq.desktop.main.MainView;
import bisq.desktop.main.funds.FundsView;
import bisq.desktop.main.funds.deposit.DepositView;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.util.GUIUtil;

import bisq.core.btc.setup.WalletsSetup;
import bisq.core.dao.DaoFacade;
import bisq.core.dao.governance.bonding.bond.BondWithHash;
import bisq.core.dao.governance.bonding.bond.BondedReputation;
import bisq.core.dao.governance.bonding.lockup.LockupType;
import bisq.core.dao.state.model.blockchain.TxOutput;
import bisq.core.dao.state.model.governance.BondedRoleType;
import bisq.core.dao.state.model.governance.Role;
import bisq.core.locale.Res;
import bisq.core.util.BsqFormatter;

import bisq.network.p2p.P2PService;

import bisq.common.app.DevEnv;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;

import javax.inject.Inject;

import java.util.Optional;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;

@Slf4j
public class BondingViewUtils {

    private final P2PService p2PService;
    private final WalletsSetup walletsSetup;
    private final DaoFacade daoFacade;
    private final Navigation navigation;
    private final BsqFormatter bsqFormatter;

    @Inject
    public BondingViewUtils(P2PService p2PService, WalletsSetup walletsSetup, DaoFacade daoFacade,
                            Navigation navigation, BsqFormatter bsqFormatter) {
        this.p2PService = p2PService;
        this.walletsSetup = walletsSetup;
        this.daoFacade = daoFacade;
        this.navigation = navigation;
        this.bsqFormatter = bsqFormatter;
    }

    private void lockupBond(BondWithHash bondWithHash, Coin lockupAmount, int lockupTime, LockupType lockupType,
                            Consumer<String> resultHandler) {
        if (GUIUtil.isReadyForTxBroadcast(p2PService, walletsSetup)) {
            if (!DevEnv.isDevMode()) {
                new Popup<>().headLine(Res.get("dao.bonding.lock.sendFunds.headline"))
                        .confirmation(Res.get("dao.bonding.lock.sendFunds.details",
                                bsqFormatter.formatCoinWithCode(lockupAmount),
                                lockupTime
                        ))
                        .actionButtonText(Res.get("shared.yes"))
                        .onAction(() -> publishLockupTx(bondWithHash, lockupAmount, lockupTime, lockupType, resultHandler))
                        .closeButtonText(Res.get("shared.cancel"))
                        .show();
            } else {
                publishLockupTx(bondWithHash, lockupAmount, lockupTime, lockupType, resultHandler);
            }
        } else {
            GUIUtil.showNotReadyForTxBroadcastPopups(p2PService, walletsSetup);
        }
    }

    private void publishLockupTx(BondWithHash bondWithHash, Coin lockupAmount, int lockupTime, LockupType lockupType, Consumer<String> resultHandler) {
        daoFacade.publishLockupTx(lockupAmount,
                lockupTime,
                lockupType,
                bondWithHash,
                txId -> {
                    if (!DevEnv.isDevMode())
                        new Popup<>().feedback(Res.get("dao.tx.published.success")).show();

                    if (resultHandler != null)
                        resultHandler.accept(txId);
                },
                this::handleError
        );
    }

    public void lockupBondForBondedRole(Role role, Consumer<String> resultHandler) {
        BondedRoleType bondedRoleType = role.getBondedRoleType();
        Coin lockupAmount = Coin.valueOf(bondedRoleType.getRequiredBond());
        int lockupTime = bondedRoleType.getUnlockTimeInBlocks();
        lockupBond(role, lockupAmount, lockupTime, LockupType.BONDED_ROLE, resultHandler);
    }

    public void lockupBondForReputation(Coin lockupAmount, int lockupTime, Consumer<String> resultHandler) {
        BondedReputation bondedReputation = BondedReputation.createBondedReputation();
        lockupBond(bondedReputation, lockupAmount, lockupTime, LockupType.REPUTATION, resultHandler);
    }

    public void unLock(String lockupTxId, Consumer<String> resultHandler) {
        if (GUIUtil.isReadyForTxBroadcast(p2PService, walletsSetup)) {
            Optional<TxOutput> lockupTxOutput = daoFacade.getLockupTxOutput(lockupTxId);
            checkArgument(lockupTxOutput.isPresent(), "Lockup output must be present. TxId=" + lockupTxId);
            Coin unlockAmount = Coin.valueOf(lockupTxOutput.get().getValue());
            Optional<Integer> opLockTime = daoFacade.getLockTime(lockupTxId);
            int lockTime = opLockTime.orElse(-1);

            try {
                if (!DevEnv.isDevMode()) {
                    new Popup<>().headLine(Res.get("dao.bonding.unlock.sendTx.headline"))
                            .confirmation(Res.get("dao.bonding.unlock.sendTx.details",
                                    bsqFormatter.formatCoinWithCode(unlockAmount),
                                    lockTime
                            ))
                            .actionButtonText(Res.get("shared.yes"))
                            .onAction(() -> publishUnlockTx(lockupTxId, resultHandler))
                            .closeButtonText(Res.get("shared.cancel"))
                            .show();
                } else {
                    publishUnlockTx(lockupTxId, resultHandler);
                }
            } catch (Throwable t) {
                log.error(t.toString());
                t.printStackTrace();
                new Popup<>().warning(t.getMessage()).show();
            }
        } else {
            GUIUtil.showNotReadyForTxBroadcastPopups(p2PService, walletsSetup);
        }
        log.info("unlock tx: {}", lockupTxId);
    }

    private void publishUnlockTx(String lockupTxId, Consumer<String> resultHandler) {
        daoFacade.publishUnlockTx(lockupTxId,
                txId -> {
                    if (!DevEnv.isDevMode())
                        new Popup<>().confirmation(Res.get("dao.tx.published.success")).show();

                    if (resultHandler != null)
                        resultHandler.accept(txId);
                },
                errorMessage -> new Popup<>().warning(errorMessage.toString()).show()
        );
    }

    private void handleError(Throwable throwable) {
        if (throwable instanceof InsufficientMoneyException) {
            final Coin missingCoin = ((InsufficientMoneyException) throwable).missing;
            final String missing = missingCoin != null ? missingCoin.toFriendlyString() : "null";
            new Popup<>().warning(Res.get("popup.warning.insufficientBtcFundsForBsqTx", missing))
                    .actionButtonTextWithGoTo("navigation.funds.depositFunds")
                    .onAction(() -> navigation.navigateTo(MainView.class, FundsView.class, DepositView.class))
                    .show();
        } else {
            log.error(throwable.toString());
            throwable.printStackTrace();
            new Popup<>().warning(throwable.toString()).show();
        }
    }
}
