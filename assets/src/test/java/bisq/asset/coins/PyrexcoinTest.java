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

package bisq.asset.coins;

import bisq.asset.AbstractAssetTest;
import org.junit.Test;

public class PyrexcoinTest extends AbstractAssetTest {

    public PyrexcoinTest() {
        super(new Pyrexcoin());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("PYX1g3CF8ov7bLMMXinNtcFv1oBzUHiPBAPRNm4xqScpAzGB3D23aNy3pupbzj52Ae9ZMjVAHKHfPT7WVLSj8m8q6nYLHeRn8h");
        assertValidAddress("PYX1nLe6C1TYSoiXry11vDSNJkzVs2whWQj3rxK3op9gZEBWnys6gy4AWMHcKuke1DhCh8fE5CjFcf3GBdQeLn2k2PiShkoq5a");
        assertValidAddress("PYs1AP37Qet1b7CQGg3JpvKTTSQN6hcXbB6oTVKk3S8a1LjzH6C1RTrL3zoqMnM3ziUZ9qLMgH8eGHYpmf4vzsYy44CJJgCP9V");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("");
        assertInvalidAddress("PdX1nLe6C1TYSoiXry11vDSNJkzVs2whWQj3rxK3op9gZEBWnys6gy4AWMHcKuke1DhCh8fE5CjFcf3GBdQeLn2k2PiShkoq5a");
        assertInvalidAddress("PhX1nLe6C1TYSoiXry11vDSNJkzVs2whWQj3rxK3op9gZEBWnys6gy4AWMHcKuke1DhCh8fE5CjFcf3GBdQeLn2k2PiShkoq5a");
        assertInvalidAddress("PYC1nLe6C1TYSoiXry11vDSNJkzVs2whWQj3rxK3op9gZEBWnys6gy4AWMHcKuke1DhCh8fE5CjFcf3GBdQeLn2k2PiShkoq5a!");
        assertInvalidAddress("PgXtfn6MydFfhepy6JHRkdekMXJ1xYSow2Q1a4LiRUafJdkxUcBu6Pc7Hkc2zdzFytesdX2oatSmfa2du8qq4WhZJQrcJFF47BD");
    }
}
