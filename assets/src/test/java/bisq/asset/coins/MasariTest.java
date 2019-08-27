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

 public class MasariTest extends AbstractAssetTest {

     public MasariTest() {
        super(new Masari());
    }

     @Test
    public void testValidAddresses() {
        assertValidAddress("5n9Y2vwnf8oKBhHxRAyjS9aS9j5hTPjtS8RKzMbD3tP95yxkQWbUHkFhLs2UsjgNxj28W6YzNL9WFeY91xPGFXAaUwyVm1h");
        assertValidAddress("9n1AVze3gmj3ZpEz5Xju92FRiqtmcnQhhXJK7yx9D9qrHRvjZftndVci8HCYFttFeD7ftAMUqUGxG8iA4Sn2eVz45R2NUJj");
        assertValidAddress("5iB4LfuyvA5HSJP5A1xUKGb8pw5NkywxSeRZPxzy1U7kT3wBmypemQUUzTiCwjy6PTSrJpAvxiNDSUEjNryt17C8RvPdEg3");
    }

     @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("");
        assertInvalidAddress("5hJpeWa9aogfpY5Su8YmeYaeuD7pyQvSZURcNx26QskbSk9UdZ6cR4HR4YsdWRiBJfCZKLHRTfj7ojGUJ7N5j5hg4pGGCE");
        assertInvalidAddress("5kYyn6K8hRWg16nztTuvaZ6Jg3ytH84gjbUoEKjbMU4u659PKLpKuLWVSujFwJ1Qp3ZUxhcFHBXMQDmeAz46By3FRRkdaug2");
        assertInvalidAddress("4okMfbVrFXE4nF9dRKnVLiJi2xiMDDuSk6MJexpBaNgsLutSaBN7euR8TCf4Z1dqmG85GdQHrzSpYgX8Lf2VJnkaAk9MtQV");
        assertInvalidAddress("5jrE2mwcHkvZq9rQcvX1GCELnwAF6wwmJ4rhVdDP6y#326Gp6KSNbeWWb1sD2dmDZvczHFs8LGM1UjTQfQjjAu6S4eXGC5h");
    }
}
