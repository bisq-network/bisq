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

public class DragonglassTest extends AbstractAssetTest {

    public DragonglassTest() {
        super(new Dragonglass());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("dRGLhxvCtLk1vfSD3WmFzyTN5ph2gZYvkZfxvLSrcdry95x4PPJrCKBTKDEFZYTw4bCGqoiaUWxNd8B41vqXaTY72Vi2XcvikX");
        assertValidAddress("dRGLjS5v91tDd4GDZeahUj95nkXSNQs5DMY1YStLN2hSNWD67iZh7ED7oDw841Kx6oUYouZaXmBNFcqSptNZ4dL94CbZbF53jt");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("dRGLjS5v91tDd4GDZeahUj95nkXSNQs5DMY1YStLN2hSNWD67iZh7ED7oDw841Kx6oUYouZaXmBNFcqSptNZ4dL94CbZbF53j");
        assertInvalidAddress("dRGLjS5v91tDd4GDZeahUj95nkXSNQs5DMY1YStLN2hSNWD67iZh7ED7oDw841Ko6oUYouZaXmBNFcqSptNZ4dL94oUCifk4048");
        assertInvalidAddress("DRGLhxvCtLk1vfSD3WmFzyTN5ph2gZYvkZfxvLSrcdry95x4PPJrCKBTKDEFZYTw4bCGqoiaUWxNd8B41vqXaTY72Vi2XcvikX");
        assertInvalidAddress("drglhxvCtLk1vfSD3WmFzyTN5ph2gZYvkZfxvLSrcdry95x4PPJrCKBTKDEFZYTw4bCGqoiaUWxNd8B41vqXaTY72Vi2XcvikX");
        assertInvalidAddress("dRgLjS5v91tDd4GDZeahUj95nkXSNQs5DMY1YStLN2hSNWD67iZh7ED7oDw841Kx6oUYouZaXmBNFcqSptNZ4dL94CbZbF53jt");
        assertInvalidAddress("dRGlhxvCtLk1vfSD3WmFzyTN5ph2gZYvkZfxvLSrcdry95x4PPJrCKBTKDEFZYTw4bCGqoiaUWxNd8B41vqXaTY72Vi2XcvikX");
    }
}
