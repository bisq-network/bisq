/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.locale;

import java.io.Serializable;

import javax.annotation.concurrent.Immutable;

@Immutable
public class Region implements Serializable {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = 1L;

    public final String code;
    public final String name;

    public Region(String code, String name) {
        this.code = code;
        this.name = name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Region region = (Region) o;

        //noinspection SimplifiableIfStatement
        if (code != null ? !code.equals(region.code) : region.code != null) return false;
        return !(name != null ? !name.equals(region.name) : region.name != null);

    }

    @Override
    public int hashCode() {
        int result = code != null ? code.hashCode() : 0;
        result = 31 * result + (name != null ? name.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Region{" +
                "code='" + code + '\'' +
                ", name='" + name + '\'' +
                '}';
    }
}
