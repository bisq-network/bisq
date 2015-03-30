/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.gui.main.portfolio.pending;

import io.bitsquare.gui.main.portfolio.pending.steps.CompletedView;
import io.bitsquare.gui.main.portfolio.pending.steps.StartFiatView;
import io.bitsquare.gui.main.portfolio.pending.steps.WaitView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PendingTradesOffererAsBuyerView extends PendingTradesStepsView {
    private static final Logger log = LoggerFactory.getLogger(PendingTradesOffererAsBuyerView.class);

    private TradeWizardItem waitTxConfirm;
    private TradeWizardItem startFiat;
    private TradeWizardItem waitFiatReceived;
    private TradeWizardItem completed;


    public PendingTradesOffererAsBuyerView() {
        super();
    }

    public void activate() {
        showWaitTxConfirm();
    }

    public void deactivate() {
    }

    public void showWaitTxConfirm() {
        showItem(waitTxConfirm);
    }

    public void showStartFiat() {
        showItem(startFiat);
    }

    public void showWaitFiatReceived() {
        showItem(waitFiatReceived);
    }

    public void showCompleted() {
        showItem(completed);
    }


    @Override
    protected void addWizards() {
        waitTxConfirm = new TradeWizardItem(WaitView.class, "Wait for blockchain confirmation");
        startFiat = new TradeWizardItem(StartFiatView.class, "Start payment");
        waitFiatReceived = new TradeWizardItem(WaitView.class, "Wait until payment has arrived");
        completed = new TradeWizardItem(CompletedView.class, "Completed");
        leftVBox.getChildren().addAll(waitTxConfirm, startFiat, waitFiatReceived, completed);
    }
}



