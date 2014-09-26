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

package io.bitsquare.gui.main.orders.pending.uimock;

import io.bitsquare.gui.CachedViewCB;
import io.bitsquare.gui.components.processbar.ProcessStepBar;
import io.bitsquare.gui.components.processbar.ProcessStepItem;

import java.net.URL;

import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PendingTradesControllerUIMock extends CachedViewCB {
    private static final Logger log = LoggerFactory.getLogger(PendingTradesControllerUIMock.class);
    public ProcessStepBar processBar;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public PendingTradesControllerUIMock() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        super.initialize(url, rb);

        List<ProcessStepItem> items = new ArrayList<>();
        items.add(new ProcessStepItem("Deposit TX published"));
        items.add(new ProcessStepItem("Waiting for other trader"));
        items.add(new ProcessStepItem("Waiting for payment"));
        items.add(new ProcessStepItem("Payment received"));
        processBar.setProcessStepItems(items);
        // processBar.next();
    }

    @Override
    public void deactivate() {
        super.deactivate();
    }

    @Override
    public void activate() {
        super.activate();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // GUI handlers
    ///////////////////////////////////////////////////////////////////////////////////////////


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////


}

