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
import bisq.core.dao.governance.bond.lockup.LockupReason;
import bisq.core.dao.governance.bond.reputation.MyReputation;
import bisq.core.dao.governance.bond.reputation.MyReputationListService;
import bisq.core.dao.governance.bond.role.BondedRolesRepository;
import bisq.core.dao.state.model.blockchain.TxOutput;
import bisq.core.dao.state.model.governance.Role;
import bisq.core.dao.state.model.governance.RoleProposal;
import bisq.core.locale.Res;
import bisq.core.util.coin.BsqFormatter;
import bisq.core.util.coin.CoinUtil;
import bisq.core.util.FormattingUtils;

import bisq.network.p2p.P2PService;

import bisq.common.app.DevEnv;
import bisq.common.util.Tuple2;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.util.Optional;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;

@Slf4j
@Singleton
public class BondingViewUtils {
    private final P2PService p2PService;
    private final MyReputationListService myReputationListService;
    private final BondedRolesRepository bondedRolesRepository;
    private final WalletsSetup walletsSetup;
    private final DaoFacade daoFacade;
    private final Navigation navigation;
    private final BsqFormatter bsqFormatter;

    @Inject
    public BondingViewUtils(P2PService p2PService,
                            MyReputationListService myReputationListService,
                            BondedRolesRepository bondedRolesRepository,
                            WalletsSetup walletsSetup,
                            DaoFacade daoFacade,
                            Navigation navigation,
                            BsqFormatter bsqFormatter) {
        this.p2PService = p2PService;
        this.myReputationListService = myReputationListService;
        this.bondedRolesRepository = bondedRolesRepository;
        this.walletsSetup = walletsSetup;
        this.daoFacade = daoFacade;
        this.navigation = navigation;
        this.bsqFormatter = bsqFormatter;
    }

    public void lockupBondForBondedRole(Role role, Consumer<String> resultHandler) {
        Optional<RoleProposal> roleProposal = getAcceptedBondedRoleProposal(role);
        checkArgument(roleProposal.isPresent(), "roleProposal must be present");

        long requiredBond = daoFacade.getRequiredBond(roleProposal);
        Coin lockupAmount = Coin.valueOf(requiredBond);
        int lockupTime = roleProposal.get().getUnlockTime();
        if (!bondedRolesRepository.isBondedAssetAlreadyInBond(role)) {
            lockupBond(role.getHash(), lockupAmount, lockupTime, LockupReason.BONDED_ROLE, resultHandler);
        } else {
            handleError(new RuntimeException("The role has been used already for a lockup tx."));
        }
    }

    public void lockupBondForReputation(Coin lockupAmount, int lockupTime, byte[] salt, Consumer<String> resultHandler) {
        MyReputation myReputation = new MyReputation(salt);
        lockupBond(myReputation.getHash(), lockupAmount, lockupTime, LockupReason.REPUTATION, resultHandler);
        myReputationListService.addReputation(myReputation);
    }

    private void lockupBond(byte[] hash, Coin lockupAmount, int lockupTime, LockupReason lockupReason,
                            Consumer<String> resultHandler) {
        if (GUIUtil.isReadyForTxBroadcastOrShowPopup(p2PService, walletsSetup)) {
            if (!DevEnv.isDevMode()) {
                try {
                    Tuple2<Coin, Integer> miningFeeAndTxVsize = daoFacade.getLockupTxMiningFeeAndTxVsize(lockupAmount, lockupTime, lockupReason, hash);
                    Coin miningFee = miningFeeAndTxVsize.first;
                    int txVsize = miningFeeAndTxVsize.second;
                    String duration = FormattingUtils.formatDurationAsWords(lockupTime * 10 * 60 * 1000L, false, false);
                    new Popup().headLine(Res.get("dao.bond.reputation.lockup.headline"))
                            .confirmation(Res.get("dao.bond.reputation.lockup.details",
                                    bsqFormatter.formatCoinWithCode(lockupAmount),
                                    lockupTime,
                                    duration,
                                    bsqFormatter.formatBTCWithCode(miningFee),
                                    CoinUtil.getFeePerVbyte(miningFee, txVsize),
                                    txVsize / 1000d
                            ))
                            .actionButtonText(Res.get("shared.yes"))
                            .onAction(() -> publishLockupTx(lockupAmount, lockupTime, lockupReason, hash, resultHandler))
                            .closeButtonText(Res.get("shared.cancel"))
                            .show();
                } catch (Throwable e) {
                    log.error(e.toString());
                    e.printStackTrace();
                    new Popup().warning(e.getMessage()).show();
                }
            } else {
                publishLockupTx(lockupAmount, lockupTime, lockupReason, hash, resultHandler);
            }
        }
    }

    private void publishLockupTx(Coin lockupAmount, int lockupTime, LockupReason lockupReason, byte[] hash, Consumer<String> resultHandler) {
        daoFacade.publishLockupTx(lockupAmount,
                lockupTime,
                lockupReason,
                hash,
                txId -> {
                    if (!DevEnv.isDevMode())
                        new Popup().feedback(Res.get("dao.tx.published.success")).show();

                    if (resultHandler != null)
                        resultHandler.accept(txId);
                },
                this::handleError
        );
    }

    public Optional<RoleProposal> getAcceptedBondedRoleProposal(Role role) {
        return bondedRolesRepository.getAcceptedBondedRoleProposal(role);
    }

    public void unLock(String lockupTxId, Consumer<String> resultHandler) {
        if (GUIUtil.isReadyForTxBroadcastOrShowPopup(p2PService, walletsSetup)) {
            Optional<TxOutput> lockupTxOutput = daoFacade.getLockupTxOutput(lockupTxId);
            checkArgument(lockupTxOutput.isPresent(), "Lockup output must be present. TxId=" + lockupTxId);
            Coin unlockAmount = Coin.valueOf(lockupTxOutput.get().getValue());
            Optional<Integer> opLockTime = daoFacade.getLockTime(lockupTxId);
            int lockTime = opLockTime.orElse(-1);

            try {
                if (!DevEnv.isDevMode()) {
                    Tuple2<Coin, Integer> miningFeeAndTxVsize = daoFacade.getUnlockTxMiningFeeAndTxVsize(lockupTxId);
                    Coin miningFee = miningFeeAndTxVsize.first;
                    int txVsize = miningFeeAndTxVsize.second;
                    String duration = FormattingUtils.formatDurationAsWords(lockTime * 10 * 60 * 1000L, false, false);
                    new Popup().headLine(Res.get("dao.bond.reputation.unlock.headline"))
                            .confirmation(Res.get("dao.bond.reputation.unlock.details",
                                    bsqFormatter.formatCoinWithCode(unlockAmount),
                                    lockTime,
                                    duration,
                                    bsqFormatter.formatBTCWithCode(miningFee),
                                    CoinUtil.getFeePerVbyte(miningFee, txVsize),
                                    txVsize / 1000d
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
                new Popup().warning(t.getMessage()).show();
            }
        }
        log.info("unlock tx: {}", lockupTxId);
    }

    private void publishUnlockTx(String lockupTxId, Consumer<String> resultHandler) {
        daoFacade.publishUnlockTx(lockupTxId,
                txId -> {
                    if (!DevEnv.isDevMode())
                        new Popup().confirmation(Res.get("dao.tx.published.success")).show();

                    if (resultHandler != null)
                        resultHandler.accept(txId);
                },
                errorMessage -> new Popup().warning(errorMessage.toString()).show()
        );
    }

    private void handleError(Throwable throwable) {
        if (throwable instanceof InsufficientMoneyException) {
            final Coin missingCoin = ((InsufficientMoneyException) throwable).missing;
            final String missing = missingCoin != null ? missingCoin.toFriendlyString() : "null";
            new Popup().warning(Res.get("popup.warning.insufficientBtcFundsForBsqTx", missing))
                    .actionButtonTextWithGoTo("navigation.funds.depositFunds")
                    .onAction(() -> navigation.navigateTo(MainView.class, FundsView.class, DepositView.class))
                    .show();
        } else {
            log.error(throwable.toString());
            throwable.printStackTrace();
            new Popup().warning(throwable.toString()).show();
        }
    }
}
