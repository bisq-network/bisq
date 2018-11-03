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

package bisq.desktop.main.dao.bonding.bonds;

import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.components.TxConfidenceListItem;
import bisq.desktop.components.indicator.TxConfidenceIndicator;

import bisq.core.btc.listeners.TxConfidenceListener;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.dao.governance.bond.BondConsensus;
import bisq.core.dao.governance.bond.lockup.LockupType;
import bisq.core.dao.governance.bond.role.BondedRole;
import bisq.core.dao.governance.bond.role.BondedRolesService;
import bisq.core.dao.state.model.blockchain.BaseTxOutput;
import bisq.core.dao.state.model.blockchain.TxOutput;
import bisq.core.dao.state.model.blockchain.TxType;
import bisq.core.dao.state.model.governance.BondedRoleType;
import bisq.core.locale.Res;
import bisq.core.util.BsqFormatter;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;

import java.util.Date;
import java.util.Optional;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;

@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
class BondListItem extends TxConfidenceListItem {
    private final BtcWalletService btcWalletService;
    private final DaoFacade daoFacade;

    private final BsqFormatter bsqFormatter;
    private final BondedRolesService bondedRolesService;
    private final Date date;

    private Coin amount = Coin.ZERO;
    private int lockTime;
    private AutoTooltipButton button;

    private TxConfidenceIndicator txConfidenceIndicator;
    private TxConfidenceListener txConfidenceListener;
    private boolean issuanceTx;

    BondListItem(Transaction transaction,
                 BsqWalletService bsqWalletService,
                 BtcWalletService btcWalletService,
                 DaoFacade daoFacade,
                 BondedRolesService bondedRolesService,
                 Date date,
                 BsqFormatter bsqFormatter) {
        super(transaction, bsqWalletService);

        this.btcWalletService = btcWalletService;
        this.daoFacade = daoFacade;
        this.bondedRolesService = bondedRolesService;
        this.date = date;
        this.bsqFormatter = bsqFormatter;

        checkNotNull(transaction, "transaction must not be null as we only have list items from transactions " +
                "which are available in the wallet");

        daoFacade.getLockupTxOutput(transaction.getHashAsString())
                .ifPresent(out -> amount = Coin.valueOf(out.getValue()));

        Optional<Integer> opLockTime = daoFacade.getLockTime(transaction.getHashAsString());
        lockTime = opLockTime.orElse(-1);

        button = new AutoTooltipButton();
        button.setMinWidth(70);
        button.updateText(Res.get("dao.bonding.unlock.unlock"));
        button.setVisible(true);
        button.setManaged(true);
    }

    public boolean isLockupAndUnspent() {
        boolean isLocked;
        Optional<BondedRole> optionalBondedRoleState = bondedRolesService.getBondedRoleStateFromLockupTxId(txId);
        if (optionalBondedRoleState.isPresent()) {
            BondedRole bondedRole = optionalBondedRoleState.get();
            //TODO
            //isLocked = bondedRole.getLockupTxId() != null && bondedRole.getUnlockTxId() == null;
            //log.error("isLocked {}, tx={}",isLocked,bondedRole.getLockupTxId());
        } else {
            //TODO get reputation
            isLocked = true;
        }
        return /*isLocked && */!isSpent() && getTxType() == TxType.LOCKUP;
    }

    private boolean isSpent() {
        Optional<TxOutput> optionalTxOutput = daoFacade.getLockupTxOutput(txId);
        return optionalTxOutput.map(txOutput -> !daoFacade.isUnspent(txOutput.getKey()))
                .orElse(true);

    }

    public TxType getTxType() {
        return daoFacade.getTx(txId)
                .flatMap(tx -> daoFacade.getOptionalTxType(tx.getId()))
                .orElse(confirmations == 0 ? TxType.UNVERIFIED : TxType.UNDEFINED_TX_TYPE);
    }

    private Optional<LockupType> getOptionalLockupType() {
        return getOpReturnData()
                .flatMap(BondConsensus::getLockupType);
    }

    private Optional<byte[]> getOpReturnData() {
        return daoFacade.getLockupOpReturnTxOutput(txId).map(BaseTxOutput::getOpReturnData);
    }

    public String getInfo() {
        Optional<BondedRoleType> optionalRoleType = bondedRolesService.getBondedRoleType(txId);
        if (optionalRoleType.isPresent()) {
            return optionalRoleType.get().getDisplayString();
        } else {
            Optional<LockupType> optionalLockupType = getOptionalLockupType();
            if (optionalLockupType.isPresent()) {
                LockupType lockupType = optionalLockupType.get();
                if (lockupType == LockupType.REPUTATION)
                    return Res.get("dao.bonding.unlock.reputation");
            }
        }
        return Res.get("shared.na");
    }
}
