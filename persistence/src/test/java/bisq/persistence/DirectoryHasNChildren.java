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

package bisq.persistence;

import java.nio.file.Path;

import java.io.File;

import java.util.Objects;



import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

public class DirectoryHasNChildren extends TypeSafeMatcher<Path> {

    private final int numberOfChildren;

    public DirectoryHasNChildren(int numberOfChildren) {
        this.numberOfChildren = numberOfChildren;
    }

    @Override
    protected boolean matchesSafely(Path item) {
        File[] files = item.toFile().listFiles();
        return Objects.requireNonNull(files).length == numberOfChildren;
    }

    @Override
    public void describeTo(Description description) {
        description.appendText("has " + numberOfChildren + " children");
    }

    public static Matcher<Path> hasNChildren(int numberOfChildren) {
        return new DirectoryHasNChildren(numberOfChildren);
    }
}
