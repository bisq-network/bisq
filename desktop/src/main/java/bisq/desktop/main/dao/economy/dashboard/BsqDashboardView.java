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

package bisq.desktop.main.dao.economy.dashboard;

import bisq.desktop.common.view.ActivatableView;
import bisq.desktop.common.view.FxmlView;

import bisq.core.dao.DaoFacade;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.model.blockchain.Block;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.user.Preferences;
import bisq.core.util.BsqFormatter;

import javax.inject.Inject;

import javafx.scene.layout.GridPane;

import javafx.beans.value.ChangeListener;

@FxmlView
public class BsqDashboardView extends ActivatableView<GridPane, Void> implements DaoStateListener {

    private final DaoFacade daoFacade;
    private final PriceFeedService priceFeedService;
    private final Preferences preferences;
    private final BsqFormatter bsqFormatter;

    private ChangeListener<Number> priceChangeListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private BsqDashboardView(DaoFacade daoFacade,
                             PriceFeedService priceFeedService,
                             Preferences preferences,
                             BsqFormatter bsqFormatter) {
        this.daoFacade = daoFacade;
        this.priceFeedService = priceFeedService;
        this.preferences = preferences;
        this.bsqFormatter = bsqFormatter;
    }

    @Override
    public void initialize() {
        priceChangeListener = (observable, oldValue, newValue) -> updatePrice();
    }

    @Override
    protected void activate() {
        daoFacade.addBsqStateListener(this);
        priceFeedService.updateCounterProperty().addListener(priceChangeListener);

        updateWithBsqBlockChainData();
        updatePrice();
    }

    @Override
    protected void deactivate() {
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
    }

    private void updatePrice() {
    }
}

