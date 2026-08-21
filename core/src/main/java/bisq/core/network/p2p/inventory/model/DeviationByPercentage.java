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

package bisq.core.network.p2p.inventory.model;

public class DeviationByPercentage implements DeviationType {
    private final double lowerAlertTrigger;
    private final double upperAlertTrigger;
    private final double lowerWarnTrigger;
    private final double upperWarnTrigger;

    // In case want to see the % deviation but not trigger any warnings or alerts
    public DeviationByPercentage() {
        this(0, Double.MAX_VALUE, 0, Double.MAX_VALUE);
    }

    public DeviationByPercentage(double lowerAlertTrigger,
                                 double upperAlertTrigger,
                                 double lowerWarnTrigger,
                                 double upperWarnTrigger) {
        this.lowerAlertTrigger = lowerAlertTrigger;
        this.upperAlertTrigger = upperAlertTrigger;
        this.lowerWarnTrigger = lowerWarnTrigger;
        this.upperWarnTrigger = upperWarnTrigger;
    }

    public DeviationSeverity getDeviationSeverity(double deviation) {
        if (deviation <= lowerAlertTrigger || deviation >= upperAlertTrigger) {
            return DeviationSeverity.ALERT;
        }

        if (deviation <= lowerWarnTrigger || deviation >= upperWarnTrigger) {
            return DeviationSeverity.WARN;
        }

        return DeviationSeverity.OK;
    }
}
