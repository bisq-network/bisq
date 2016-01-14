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

package io.bitsquare.alert;

import io.bitsquare.common.handlers.ErrorMessageHandler;
import io.bitsquare.common.handlers.ResultHandler;
import io.bitsquare.p2p.P2PService;
import io.bitsquare.p2p.storage.HashMapChangedListener;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

/**
 * Used to load global alert messages.
 * The message is signed by the project developers private key and use data protection.
 */
public class AlertService {
    private static final Logger log = LoggerFactory.getLogger(AlertService.class);
    private final P2PService p2PService;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, Initialization
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public AlertService(P2PService p2PService) {
        this.p2PService = p2PService;
    }

    public void addHashSetChangedListener(HashMapChangedListener hashMapChangedListener) {
        p2PService.addHashSetChangedListener(hashMapChangedListener);
    }

    public void addAlertMessage(Alert alert, @Nullable ResultHandler resultHandler, @Nullable ErrorMessageHandler errorMessageHandler) {
        boolean result = p2PService.addData(alert);
        if (result) {
            log.trace("Add alertMessage to network was successful. AlertMessage = " + alert);
            if (resultHandler != null) resultHandler.handleResult();
        } else {
            if (errorMessageHandler != null) errorMessageHandler.handleErrorMessage("Add alertMessage failed");
        }
    }

    public void removeAlertMessage(Alert alert, @Nullable ResultHandler resultHandler, @Nullable ErrorMessageHandler errorMessageHandler) {
        if (p2PService.removeData(alert)) {
            log.trace("Remove alertMessage from network was successful. AlertMessage = " + alert);
            if (resultHandler != null) resultHandler.handleResult();
        } else {
            if (errorMessageHandler != null) errorMessageHandler.handleErrorMessage("Remove alertMessage failed");
        }
    }

}
