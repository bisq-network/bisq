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

package bisq.desktop.common.view;

import java.util.function.Function;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface FxmlView {

    /**
     * The location of the FXML file associated with annotated {@link View} class. By default the location will be
     * determined by {@link #convention()}.
     */
    String location() default "";

    /**
     * The function used to determine the location of the FXML file associated with the annotated {@link View} class.
     * By default it is the fully-qualified view class name, converted to a resource path, replacing the
     * {@code .class} suffix replaced with {@code .fxml}.
     */
    Class<? extends PathConvention> convention() default DefaultPathConvention.class;

    interface PathConvention extends Function<Class<? extends View>, String> {
    }
}
