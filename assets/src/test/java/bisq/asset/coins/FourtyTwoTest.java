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

public class FourtyTwoTest extends AbstractAssetTest {

    public FourtyTwoTest() {
        super(new FourtyTwo());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("foUrDvc6vtJYMvqpx4oydJjL445udJ83M8rAqpkF8hEcbyLCp5MhvLaLGXtVYkqVXDG8YEpGBU7F241FtWXVCFEK7EMgnjrsM8");
        assertValidAddress("foUrFDEDkMGjV4HJzgYqSHhPTFaHfcpLM4WGZjYQZyrcCgyZs32QweCZEysK8eNxgsWdXv3YBP8QWDDWBAPu55eJ6gLf2TubwG");
        assertValidAddress("SNakeyQFcEacGHFaCgj4VpdfM3VTsFDygNHswx3CtKpn8uD1DmrbFwfM11cSyv3CZrNNWh4AALYuGS4U4pxYPHTiBn2DUJASoQw4B");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("");
        assertInvalidAddress("fUrDvc6vtJYMvqpx4oydJjL445udJ83M8rAqpkF8hEcbyLCp5MhvLaLGXtVYkqVXDG8YEpGBU7F241FtWXVCFEK7EMgnjrsM8");
        assertInvalidAddress("UrFDEDkMGjV4HJzgYqSHhPTFaHfcpLM4WGZjYQZyrcCgyZs32QweCZEysK8eNxgsWdXv3YBP8QWDDWBAPu55eJ6gLf2TubwG");
        assertInvalidAddress("keyQFcEacGHFaCgj4VpdfM3VTsFDygNHswx3CtKpn8uD1DmrbFwfM11cSyv3CZrNNWh4AALYuGS4U4pxYPHTiBn2DUJASoQw4B!");
        assertInvalidAddress("akeyQFcEacGHFaCgj4VpdfM3VTsFDygNHswx3CtKpn8uD1DmrbFwfM11cSyv3CZrNNWh4AALYuGS4U4pxYPHTiBn2DUJASoQw4B");
    }
}
