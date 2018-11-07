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

package bisq.desktop.main.settings.preferences;

import bisq.desktop.maker.PreferenceMakers;

import bisq.core.arbitration.Arbitrator;
import bisq.core.arbitration.ArbitratorManager;
import bisq.core.user.Preferences;

import bisq.network.p2p.NodeAddress;

import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;

import java.util.ArrayList;

import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ArbitratorManager.class, Preferences.class})
@PowerMockIgnore({"com.sun.org.apache.xerces.*", "javax.xml.*", "org.xml.*"})
public class PreferencesViewModelTest {



    @Test
    public void testGetArbitrationLanguages() {

        ArbitratorManager arbitratorManager = mock(ArbitratorManager.class);

        final ObservableMap<NodeAddress, Arbitrator> arbitrators = FXCollections.observableHashMap();

        ArrayList<String> languagesOne = new ArrayList<String>() {{
            add("en");
            add("de");
        }};

        ArrayList<String> languagesTwo = new ArrayList<String>() {{
            add("en");
            add("es");
        }};

        Arbitrator one = new Arbitrator(new NodeAddress("arbitrator:1"), null, null, null,
                languagesOne, 0L, null, "", null,
                null, null);

        Arbitrator two = new Arbitrator(new NodeAddress("arbitrator:2"), null, null, null,
                languagesTwo, 0L, null, "", null,
                null, null);

        arbitrators.put(one.getNodeAddress(), one);
        arbitrators.put(two.getNodeAddress(), two);

        Preferences preferences = PreferenceMakers.empty;

        when(arbitratorManager.getArbitratorsObservableMap()).thenReturn(arbitrators);

        PreferencesViewModel model = new PreferencesViewModel(preferences, arbitratorManager);

        assertEquals("English, Deutsch, español", model.getArbitrationLanguages());
    }

}
