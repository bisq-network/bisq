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

package bisq.common.util;


import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static java.util.Arrays.stream;
import static org.apache.commons.lang3.StringUtils.capitalize;

public class ReflectionUtils {

    /**
     * Recursively loads a list of fields for a given class and its superclasses,
     * using a filter predicate to exclude any unwanted fields.
     *
     * @param fields The list of fields being loaded for a class hierarchy.
     * @param clazz The lowest level class in a hierarchy;  excluding Object.class.
     * @param isExcludedField The field exclusion predicate.
     */
    public static void loadFieldListForClassHierarchy(List<Field> fields,
                                                      Class<?> clazz,
                                                      Predicate<Field> isExcludedField) {
        fields.addAll(stream(clazz.getDeclaredFields())
                .filter(f -> !isExcludedField.test(f))
                .collect(Collectors.toList()));

        Class<?> superclass = clazz.getSuperclass();
        if (!Objects.equals(superclass, Object.class))
            loadFieldListForClassHierarchy(fields,
                    superclass,
                    isExcludedField);
    }

    /**
     * Returns an Optional of a setter method for a given field and a class hierarchy,
     * or Optional.empty() if it does not exist.
     *
     * @param field The field used to find a setter method.
     * @param clazz The lowest level class in a hierarchy;  excluding Object.class.
     * @return Optional<Method> of the setter method for a field in the class hierarchy,
     * or Optional.empty() if it does not exist.
     */
    public static Optional<Method> getSetterMethodForFieldInClassHierarchy(Field field,
                                                                           Class<?> clazz) {
        Optional<Method> setter = stream(clazz.getDeclaredMethods())
                .filter((m) -> isSetterForField(m, field))
                .findFirst();

        if (setter.isPresent())
            return setter;

        Class<?> superclass = clazz.getSuperclass();
        if (!Objects.equals(superclass, Object.class)) {
            setter = getSetterMethodForFieldInClassHierarchy(field, superclass);
            if (setter.isPresent())
                return setter;
        }

        return Optional.empty();
    }

    public static boolean isSetterForField(Method m, Field f) {
        return m.getName().startsWith("set")
                && m.getName().endsWith(capitalize(f.getName()))
                && m.getReturnType().getName().equals("void")
                && m.getParameterCount() == 1
                && m.getParameterTypes()[0].getName().equals(f.getType().getName());
    }

    public static boolean isSetterOnClass(Method setter, Class<?> clazz) {
        return clazz.equals(setter.getDeclaringClass());
    }

    public static String getVisibilityModifierAsString(Field field) {
        if (Modifier.isPrivate(field.getModifiers()))
            return "private";
        else if (Modifier.isProtected(field.getModifiers()))
            return "protected";
        else if (Modifier.isPublic(field.getModifiers()))
            return "public";
        else
            return "";
    }
}
