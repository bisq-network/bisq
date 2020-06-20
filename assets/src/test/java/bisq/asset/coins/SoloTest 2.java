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

 public class SoloTest extends AbstractAssetTest {

     public SoloTest() {
        super(new Solo());
    }

     @Test
    public void testValidAddresses() {
        assertValidAddress("SL3UVNhEHuaWK9PwhVgMZWD5yaL6VBC4xRuXLnLFWizxavKvSqbcSpH2fG3dT36uMJEQ6XoKBqvFLUnzWG4Rb5e11yqsioFy8");
        assertValidAddress("Ssy27ePzscCj4spPjgtc8NKGSud9eLFLHGEWNAo8PuC53NnWhDDTX17Cfo3BzFKdYZfU9ovtEYNtQ4ezTtPhAHEuAR5mF8dTqB");
        assertValidAddress("Ssy2WFFnmi3XYJz8UsXPKzHtUxFdVhdSuU3sBGmpTbTLQqpZEMPS8GB486Q8UCaskdbGzxJxwdJYobtJmEPwDawa5mXD5spNbs");
    }

     @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("");
        assertInvalidAddress("SL3dqGkkFszKzjzyXSLkYB6X9uqad7ih3DJtTeB8hrzD9iaRjWAUHZ8FA3NErphWM00NzURSTL7FEZ9un9fgLYjK2f7mHRFBn");
        assertInvalidAddress("Ssy2WLjegYxS5P1djMSRmVG8EzXDfHyde6BiZRd3aDyVh1vjwUB2GJHfWhVsvg1i4TjWyGRC9rD4n3kCE2gPA9yx6K34AyzcMZ");
        assertInvalidAddress("Sl3UVNhEHuaWK9PwhVgMZWD5yaL6VBC4xRuXLnLFWizxavKvSXxXSpam8d3dMaDuMJEQ6XoKBqvFLUnzWG4Rb5e11yqsioFy8");
        assertInvalidAddress("Ssy2WFFnmi3XYJz8UsXPKzHtUxFdVhdSuU3sBGmpTbTLQLoLIghGooDdf6QTryaskdbGzxJxwdJYobtJmEPwDawa5mXD5spNbs");
    }
}
