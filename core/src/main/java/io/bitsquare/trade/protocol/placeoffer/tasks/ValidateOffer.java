/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.trade.protocol.placeoffer.tasks;

import io.bitsquare.common.taskrunner.Task;
import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.trade.protocol.placeoffer.PlaceOfferModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ValidateOffer extends Task<PlaceOfferModel> {
    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(ValidateOffer.class);

    @SuppressWarnings({"WeakerAccess", "unused"})
    public ValidateOffer(TaskRunner taskHandler, PlaceOfferModel model) {
        super(taskHandler, model);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            model.offer.validate();

            complete();
        } catch (Exception e) {
            model.offer.setErrorMessage("An error occurred.\n" +
                    "Error message:\n"
                    + e.getMessage());
            failed(e);
        }
    }
}
