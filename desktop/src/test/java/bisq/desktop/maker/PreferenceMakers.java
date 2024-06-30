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

package bisq.desktop.maker;

import bisq.core.btc.nodes.LocalBitcoinNode;
import bisq.core.provider.fee.FeeService;
import bisq.core.user.Preferences;
import bisq.core.user.PreferencesPayload;

import bisq.common.config.Config;
import bisq.common.persistence.PersistenceManager;

import com.natpryce.makeiteasy.Instantiator;
import com.natpryce.makeiteasy.Property;
import com.natpryce.makeiteasy.SameValueDonor;

import static com.natpryce.makeiteasy.MakeItEasy.a;
import static com.natpryce.makeiteasy.MakeItEasy.make;

public class PreferenceMakers {
    public static final Property<Preferences, PersistenceManager<PreferencesPayload>> storage = new Property<>();
    public static final Property<Preferences, Config> config = new Property<>();
    public static final Property<Preferences, FeeService> feeService = new Property<>();
    public static final Property<Preferences, LocalBitcoinNode> localBitcoinNode = new Property<>();
    public static final Property<Preferences, String> useTorFlagFromOptions = new Property<>();
    public static final Property<Preferences, String> referralID = new Property<>();

    public static final Instantiator<Preferences> Preferences = lookup -> new Preferences(
            lookup.valueOf(storage, new SameValueDonor<>(null)),
            lookup.valueOf(config, new SameValueDonor<>(null)),
            lookup.valueOf(feeService, new SameValueDonor<>(null)),
            lookup.valueOf(localBitcoinNode, new SameValueDonor<>(null)),
            lookup.valueOf(useTorFlagFromOptions, new SameValueDonor<>(null)),
            lookup.valueOf(referralID, new SameValueDonor<>(null)),
            Config.DEFAULT_FULL_DAO_NODE, false, null, null, Config.UNSPECIFIED_PORT, false);

    public static final Preferences empty = make(a(Preferences));
}
