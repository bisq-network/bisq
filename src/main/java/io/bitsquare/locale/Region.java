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

import java.util.Objects;

public class Region implements Serializable {
    private static final long serialVersionUID = -5930294199097793187L;


    private final String code;

    private final String name;

    public Region(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public int hashCode() {
        return Objects.hashCode(code);
    }

    public boolean equals(Object obj) {
        if (!(obj instanceof Region)) {
            return false;
        }
        if (obj == this) {
            return true;
        }

        Region other = (Region) obj;
        return code.equals(other.getCode());
    }


    String getCode() {
        return code;
    }


    public String getName() {
        return name;
    }


    @Override
    public String toString() {
        return "regionCode='" + code + '\'' +
                ", continentName='" + name;
    }
}
