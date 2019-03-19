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

package bisq.desktop.main.dao.wallet.dashboard;

import bisq.desktop.common.view.ActivatableView;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.HyperlinkWithIcon;
import bisq.desktop.main.dao.wallet.BsqBalanceUtil;
import bisq.desktop.util.FormBuilder;
import bisq.desktop.util.Layout;

import bisq.core.dao.DaoFacade;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.model.blockchain.Block;
import bisq.core.dao.state.model.governance.IssuanceType;
import bisq.core.locale.Res;
import bisq.core.monetary.Price;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.user.Preferences;
import bisq.core.util.BsqFormatter;

import bisq.common.util.Tuple3;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;

import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import javafx.beans.value.ChangeListener;

import java.util.Optional;

import static bisq.desktop.util.FormBuilder.addTitledGroupBg;
import static bisq.desktop.util.FormBuilder.addTopLabelReadOnlyTextField;

@FxmlView
public class BsqDashboardView extends ActivatableView<GridPane, Void> implements DaoStateListener {

    private final BsqBalanceUtil bsqBalanceUtil;
    private final DaoFacade daoFacade;
    private final PriceFeedService priceFeedService;
    private final Preferences preferences;
    private final BsqFormatter bsqFormatter;

    private int gridRow = 0;
    private TextField genesisIssueAmountTextField, compRequestIssueAmountTextField, reimbursementAmountTextField, availableAmountTextField,
            burntAmountTextField, totalLockedUpAmountTextField, totalUnlockingAmountTextField,
            totalUnlockedAmountTextField, totalConfiscatedAmountTextField, allTxTextField, burntTxTextField,
            utxoTextField, compensationIssuanceTxTextField,
            reimbursementIssuanceTxTextField, priceTextField, marketCapTextField;
    private ChangeListener<Number> priceChangeListener;
    private HyperlinkWithIcon hyperlinkWithIcon;
    private Coin availableAmount;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private BsqDashboardView(BsqBalanceUtil bsqBalanceUtil,
                             DaoFacade daoFacade,
                             PriceFeedService priceFeedService,
                             Preferences preferences,
                             BsqFormatter bsqFormatter) {
        this.bsqBalanceUtil = bsqBalanceUtil;
        this.daoFacade = daoFacade;
        this.priceFeedService = priceFeedService;
        this.preferences = preferences;
        this.bsqFormatter = bsqFormatter;
    }

    @Override
    public void initialize() {
        gridRow = bsqBalanceUtil.addGroup(root, gridRow);
        int columnIndex = 2;

        int startRow = gridRow;
        addTitledGroupBg(root, ++gridRow, 5, Res.get("dao.wallet.dashboard.distribution"), Layout.GROUP_DISTANCE);
        genesisIssueAmountTextField = FormBuilder.addTopLabelReadOnlyTextField(root, gridRow, Res.get("dao.wallet.dashboard.genesisIssueAmount"), Layout.FIRST_ROW_AND_GROUP_DISTANCE).second;
        compRequestIssueAmountTextField = FormBuilder.addTopLabelReadOnlyTextField(root, ++gridRow, Res.get("dao.wallet.dashboard.compRequestIssueAmount")).second;
        reimbursementAmountTextField = FormBuilder.addTopLabelReadOnlyTextField(root, ++gridRow, Res.get("dao.wallet.dashboard.reimbursementAmount")).second;
        burntAmountTextField = FormBuilder.addTopLabelReadOnlyTextField(root, ++gridRow, Res.get("dao.wallet.dashboard.burntAmount")).second;
        availableAmountTextField = FormBuilder.addTopLabelReadOnlyTextField(root, ++gridRow, Res.get("dao.wallet.dashboard.availableAmount")).second;

        gridRow = startRow;
        addTitledGroupBg(root, ++gridRow, columnIndex, 5, Res.get("dao.wallet.dashboard.locked"), Layout.GROUP_DISTANCE);
        totalLockedUpAmountTextField = FormBuilder.addTopLabelReadOnlyTextField(root, gridRow, columnIndex, Res.get("dao.wallet.dashboard.totalLockedUpAmount"), Layout.FIRST_ROW_AND_GROUP_DISTANCE).second;
        totalUnlockingAmountTextField = FormBuilder.addTopLabelReadOnlyTextField(root, ++gridRow, columnIndex, Res.get("dao.wallet.dashboard.totalUnlockingAmount")).second;
        totalUnlockedAmountTextField = FormBuilder.addTopLabelReadOnlyTextField(root, ++gridRow, columnIndex, Res.get("dao.wallet.dashboard.totalUnlockedAmount")).second;
        totalConfiscatedAmountTextField = FormBuilder.addTopLabelReadOnlyTextField(root, ++gridRow, columnIndex, Res.get("dao.wallet.dashboard.totalConfiscatedAmount")).second;
        gridRow++;

        startRow = gridRow;
        addTitledGroupBg(root, ++gridRow, 2, Res.get("dao.wallet.dashboard.market"), Layout.GROUP_DISTANCE);
        priceTextField = FormBuilder.addTopLabelReadOnlyTextField(root, gridRow, Res.get("dao.wallet.dashboard.price"), Layout.FIRST_ROW_AND_GROUP_DISTANCE).second;
        marketCapTextField = FormBuilder.addTopLabelReadOnlyTextField(root, ++gridRow, Res.get("dao.wallet.dashboard.marketCap")).second;

        gridRow = startRow;
        addTitledGroupBg(root, ++gridRow, columnIndex, 2, Res.get("dao.wallet.dashboard.genesis"), Layout.GROUP_DISTANCE);
        String genTxHeight = String.valueOf(daoFacade.getGenesisBlockHeight());
        String genesisTxId = daoFacade.getGenesisTxId();
        String url = preferences.getBsqBlockChainExplorer().txUrl + genesisTxId;
        addTopLabelReadOnlyTextField(root, gridRow, columnIndex, Res.get("dao.wallet.dashboard.genesisBlockHeight"),
                genTxHeight, Layout.FIRST_ROW_AND_GROUP_DISTANCE);

        // TODO use addTopLabelTxIdTextField
        Tuple3<Label, HyperlinkWithIcon, VBox> tuple = FormBuilder.addTopLabelHyperlinkWithIcon(root, ++gridRow, columnIndex,
                Res.get("dao.wallet.dashboard.genesisTxId"), genesisTxId, url, 0);
        hyperlinkWithIcon = tuple.second;
        hyperlinkWithIcon.setTooltip(new Tooltip(Res.get("tooltip.openBlockchainForTx", genesisTxId)));

        startRow = gridRow;
        addTitledGroupBg(root, ++gridRow, 3, Res.get("dao.wallet.dashboard.txDetails"), Layout.GROUP_DISTANCE);
        allTxTextField = FormBuilder.addTopLabelReadOnlyTextField(root, gridRow, Res.get("dao.wallet.dashboard.allTx"),
                genTxHeight, Layout.FIRST_ROW_AND_GROUP_DISTANCE).second;
        utxoTextField = FormBuilder.addTopLabelReadOnlyTextField(root, ++gridRow, Res.get("dao.wallet.dashboard.utxo")).second;
        compensationIssuanceTxTextField = FormBuilder.addTopLabelReadOnlyTextField(root, ++gridRow,
                Res.get("dao.wallet.dashboard.compensationIssuanceTx")).second;

        gridRow = startRow;
        addTitledGroupBg(root, ++gridRow, columnIndex, 3, "", Layout.GROUP_DISTANCE);
        reimbursementIssuanceTxTextField = FormBuilder.addTopLabelReadOnlyTextField(root, gridRow, columnIndex,
                Res.get("dao.wallet.dashboard.reimbursementIssuanceTx"),
                Layout.FIRST_ROW_AND_GROUP_DISTANCE).second;
        burntTxTextField = FormBuilder.addTopLabelReadOnlyTextField(root, ++gridRow, columnIndex,
                Res.get("dao.wallet.dashboard.burntTx")).second;
        ++gridRow;

        priceChangeListener = (observable, oldValue, newValue) -> updatePrice();
    }

    @Override
    protected void activate() {
        bsqBalanceUtil.activate();

        daoFacade.addBsqStateListener(this);
        priceFeedService.updateCounterProperty().addListener(priceChangeListener);

        updateWithBsqBlockChainData();
        updatePrice();
    }

    @Override
    protected void deactivate() {
        bsqBalanceUtil.deactivate();
        daoFacade.removeBsqStateListener(this);
        priceFeedService.updateCounterProperty().removeListener(priceChangeListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoStateListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onParseBlockCompleteAfterBatchProcessing(Block block) {
        updateWithBsqBlockChainData();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void updateWithBsqBlockChainData() {
        Coin issuedAmountFromGenesis = daoFacade.getGenesisTotalSupply();
        genesisIssueAmountTextField.setText(bsqFormatter.formatAmountWithGroupSeparatorAndCode(issuedAmountFromGenesis));

        Coin issuedAmountFromCompRequests = Coin.valueOf(daoFacade.getTotalIssuedAmount(IssuanceType.COMPENSATION));
        compRequestIssueAmountTextField.setText(bsqFormatter.formatAmountWithGroupSeparatorAndCode(issuedAmountFromCompRequests));
        Coin issuedAmountFromReimbursementRequests = Coin.valueOf(daoFacade.getTotalIssuedAmount(IssuanceType.REIMBURSEMENT));
        reimbursementAmountTextField.setText(bsqFormatter.formatAmountWithGroupSeparatorAndCode(issuedAmountFromReimbursementRequests));

        Coin burntFee = Coin.valueOf(daoFacade.getTotalBurntFee());
        Coin totalLockedUpAmount = Coin.valueOf(daoFacade.getTotalLockupAmount());
        Coin totalUnlockingAmount = Coin.valueOf(daoFacade.getTotalAmountOfUnLockingTxOutputs());
        Coin totalUnlockedAmount = Coin.valueOf(daoFacade.getTotalAmountOfUnLockedTxOutputs());
        Coin totalConfiscatedAmount = Coin.valueOf(daoFacade.getTotalAmountOfConfiscatedTxOutputs());
        availableAmount = issuedAmountFromGenesis
                .add(issuedAmountFromCompRequests)
                .add(issuedAmountFromReimbursementRequests)
                .subtract(burntFee)
                .subtract(totalConfiscatedAmount);

        availableAmountTextField.setText(bsqFormatter.formatAmountWithGroupSeparatorAndCode(availableAmount));
        burntAmountTextField.setText("-" + bsqFormatter.formatAmountWithGroupSeparatorAndCode(burntFee));
        totalLockedUpAmountTextField.setText(bsqFormatter.formatAmountWithGroupSeparatorAndCode(totalLockedUpAmount));
        totalUnlockingAmountTextField.setText(bsqFormatter.formatAmountWithGroupSeparatorAndCode(totalUnlockingAmount));
        totalUnlockedAmountTextField.setText(bsqFormatter.formatAmountWithGroupSeparatorAndCode(totalUnlockedAmount));
        totalConfiscatedAmountTextField.setText(bsqFormatter.formatAmountWithGroupSeparatorAndCode(totalConfiscatedAmount));
        allTxTextField.setText(String.valueOf(daoFacade.getTxs().size()));
        utxoTextField.setText(String.valueOf(daoFacade.getUnspentTxOutputs().size()));
        compensationIssuanceTxTextField.setText(String.valueOf(daoFacade.getNumIssuanceTransactions(IssuanceType.COMPENSATION)));
        reimbursementIssuanceTxTextField.setText(String.valueOf(daoFacade.getNumIssuanceTransactions(IssuanceType.REIMBURSEMENT)));
        burntTxTextField.setText(String.valueOf(daoFacade.getFeeTxs().size()));
    }

    private void updatePrice() {
        Optional<Price> optionalBsqPrice = priceFeedService.getBsqPrice();
        if (optionalBsqPrice.isPresent()) {
            Price bsqPrice = optionalBsqPrice.get();
            priceTextField.setText(bsqFormatter.formatPrice(bsqPrice) + " BSQ/BTC");

            marketCapTextField.setText(bsqFormatter.formatMarketCap(priceFeedService.getMarketPrice("BSQ"),
                    priceFeedService.getMarketPrice(preferences.getPreferredTradeCurrency().getCode()),
                    availableAmount));
        } else {
            priceTextField.setText(Res.get("shared.na"));
            marketCapTextField.setText(Res.get("shared.na"));
        }
    }
}

