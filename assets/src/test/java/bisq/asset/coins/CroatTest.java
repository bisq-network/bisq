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

public class CroatTest extends AbstractAssetTest {

    public CroatTest() {
        super(new Croat());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("CsZ46x2mzB3GhjrC2Lt7oZ4Efmj8USUjVM7Bdz8B8EF6bQwN84NzSti7RwLZcFoZG5NR1iaiZY8GP2KwumVc1jGzHLvBzAv");
        assertValidAddress("CjxZDcoWCsx1wmYkmJcFpSTgqpjoFGRW9dQT8JqgwvkBaU6Q3X4MJ4QjVkNUM7GHp6NjYaTrKeH4bSRTK3mCYsHf2818vzv");
        assertValidAddress("CoCJje3bcEH2dkvb5suRy2ZiBtPBeBqWaY9sbMLEtqEvDn969eDx1zqV4FP8erJSJFK5Br6GheGnJJG7BDtG9XFbFcMkUJU");
    }

    @Test
    public void testInvalidAddresses() { 
        assertInvalidAddress("ZsZ46x2mzB3GhjrC2Lt7oZ4Efmj8USUjVM7Bdz8B8EF6bQwN84NzSti7RwLZcFoZG5NR1iaiZY8GP2KwumVc1jGzHLvBzAv");
        assertInvalidAddress("");
        assertInvalidAddress("CjxZDcoWCsx1wmYkmJcFpSTgqpjoFGRW9dQT8JqgwvkBaU6Q3X4MJ4QjV#NUM7GHp6NjYaTrKeH4bSRTK3mCYsHf2818vzv");
        assertInvalidAddress("CoCJje3bcEH2dkvb5suRy2ZiBtPBeBqWaY9sbMLEtqEvDn969eDx1zqV4FP8erJSJFK5Br6GheGnJJG7BDtG9XFbFcMkUJUuuuuuuuu");
        assertInvalidAddress("CsZ46x2mzB3GhjrC2Lt7oZ4Efmj8USUjVM7Bdz8B8EF6bQwN84NzSti7RwLZcFoZG5NR1iaiZY8GP2KwumVc1jGzHLvBzAv11111111");
        assertInvalidAddress("CjxZDcoWCsx1wmYkmJcFpSTgqpjoFGRW9dQT8JqgwvkBaU6Q3X4MJ4QjVkNUM7GHp6NjYaTrKeH4bSRTK3m");
        assertInvalidAddress("CjxZDcoWCsx1wmYkmJcFpSTgqpjoFGRW9dQT8JqgwvkBaU6Q3X4MJ4QjVkNUM7GHp6NjYaTrKeH4bSRTK3mCYsHf2818vzv$%");
    }
}
