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

package bisq.core.proto;

import bisq.core.btc.model.AddressEntry;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferDirection;
import bisq.core.offer.OpenOffer;
import bisq.core.offer.availability.AvailabilityResult;
import bisq.core.support.dispute.DisputeResult;
import bisq.core.trade.model.bisq_v1.Trade;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ProtoDevUtil {
    // Util for auto generating enum values used in pb definition
    public static void printAllEnumsForPB() {
        StringBuilder sb = new StringBuilder("\n    enum State {\n");
        sb.append("        PB_ERROR = 0;\n");
        for (int i = 0; i < Trade.State.values().length; i++) {
            Trade.State s = Trade.State.values()[i];
            sb.append("        ");
            sb.append(s.toString());
            sb.append(" = ");
            sb.append(s.ordinal() + 1);
            sb.append(";\n");
        }
        sb.append("    }\n\n");

        sb.append("    enum Phase {\n");
        sb.append("        PB_ERROR = 0;\n");
        for (int i = 0; i < Trade.Phase.values().length; i++) {
            Trade.Phase s = Trade.Phase.values()[i];
            sb.append("        ");
            sb.append(s.toString());
            sb.append(" = ");
            sb.append(s.ordinal() + 1);
            sb.append(";\n");
        }
        sb.append("    }\n\n\n");

        sb.append("    enum DisputeState {\n");
        sb.append("        PB_ERROR = 0;\n");
        for (int i = 0; i < Trade.DisputeState.values().length; i++) {
            Trade.DisputeState s = Trade.DisputeState.values()[i];
            sb.append("        ");
            sb.append(s.toString());
            sb.append(" = ");
            sb.append(s.ordinal() + 1);
            sb.append(";\n");
        }
        sb.append("    }\n\n\n");

        sb.append("    enum TradePeriodState {\n");
        sb.append("        PB_ERROR = 0;\n");
        for (int i = 0; i < Trade.TradePeriodState.values().length; i++) {
            Trade.TradePeriodState s = Trade.TradePeriodState.values()[i];
            sb.append("        ");
            sb.append(s.toString());
            sb.append(" = ");
            sb.append(s.ordinal() + 1);
            sb.append(";\n");
        }
        sb.append("    }\n\n\n");

        sb.append("    enum Direction {\n");
        sb.append("        PB_ERROR = 0;\n");
        for (int i = 0; i < OfferDirection.values().length; i++) {
            OfferDirection s = OfferDirection.values()[i];
            sb.append("        ");
            sb.append(s.toString());
            sb.append(" = ");
            sb.append(s.ordinal() + 1);
            sb.append(";\n");
        }
        sb.append("    }\n\n\n");

        sb.append("    enum Winner {\n");
        sb.append("        PB_ERROR = 0;\n");
        for (int i = 0; i < DisputeResult.Winner.values().length; i++) {
            DisputeResult.Winner s = DisputeResult.Winner.values()[i];
            sb.append("        ");
            sb.append(s.toString());
            sb.append(" = ");
            sb.append(s.ordinal() + 1);
            sb.append(";\n");
        }
        sb.append("    }\n\n\n");

        sb.append("    enum Reason {\n");
        sb.append("        PB_ERROR = 0;\n");
        for (int i = 0; i < DisputeResult.Reason.values().length; i++) {
            DisputeResult.Reason s = DisputeResult.Reason.values()[i];
            sb.append("        ");
            sb.append(s.toString());
            sb.append(" = ");
            sb.append(s.ordinal() + 1);
            sb.append(";\n");
        }
        sb.append("    }\n\n\n");

        sb.append("    enum AvailabilityResult {\n");
        sb.append("        PB_ERROR = 0;\n");
        for (int i = 0; i < AvailabilityResult.values().length; i++) {
            AvailabilityResult s = AvailabilityResult.values()[i];
            sb.append("        ");
            sb.append(s.toString());
            sb.append(" = ");
            sb.append(s.ordinal() + 1);
            sb.append(";\n");
        }
        sb.append("    }\n\n\n");

        sb.append("    enum Context {\n");
        sb.append("        PB_ERROR = 0;\n");
        for (int i = 0; i < AddressEntry.Context.values().length; i++) {
            AddressEntry.Context s = AddressEntry.Context.values()[i];
            sb.append("        ");
            sb.append(s.toString());
            sb.append(" = ");
            sb.append(s.ordinal() + 1);
            sb.append(";\n");
        }
        sb.append("    }\n\n\n");

        sb.append("    enum State {\n");
        sb.append("        PB_ERROR = 0;\n");
        for (int i = 0; i < Offer.State.values().length; i++) {
            Offer.State s = Offer.State.values()[i];
            sb.append("        ");
            sb.append(s.toString());
            sb.append(" = ");
            sb.append(s.ordinal() + 1);
            sb.append(";\n");
        }
        sb.append("    }\n\n\n");

        sb.append("    enum State {\n");
        sb.append("        PB_ERROR = 0;\n");
        for (int i = 0; i < OpenOffer.State.values().length; i++) {
            OpenOffer.State s = OpenOffer.State.values()[i];
            sb.append("        ");
            sb.append(s.toString());
            sb.append(" = ");
            sb.append(s.ordinal() + 1);
            sb.append(";\n");
        }
        sb.append("    }\n\n\n");

        log.info(sb.toString());
    }

}
