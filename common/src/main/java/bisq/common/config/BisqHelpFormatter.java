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

package bisq.common.config;

import joptsimple.HelpFormatter;
import joptsimple.OptionDescriptor;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class BisqHelpFormatter implements HelpFormatter {

    private final String fullName;
    private final String scriptName;
    private final String version;

    public BisqHelpFormatter(String fullName, String scriptName, String version) {
        this.fullName = fullName;
        this.scriptName = scriptName;
        this.version = version;
    }

    public String format(Map<String, ? extends OptionDescriptor> descriptors) {

        StringBuilder output = new StringBuilder();
        output.append(String.format("%s version %s\n\n", fullName, version));
        output.append(String.format("Usage: %s [options]\n\n", scriptName));
        output.append("Options:\n\n");

        for (Map.Entry<String, ? extends OptionDescriptor> entry : descriptors.entrySet()) {
            String optionName = entry.getKey();
            OptionDescriptor optionDesc = entry.getValue();

            if (optionDesc.representsNonOptions())
                continue;

            output.append(String.format("%s\n", formatOptionSyntax(optionName, optionDesc)));
            output.append(String.format("%s\n", formatOptionDescription(optionDesc)));
        }

        return output.toString();
    }

    private String formatOptionSyntax(String optionName, OptionDescriptor optionDesc) {
        StringBuilder result = new StringBuilder(String.format("  --%s", optionName));

        if (optionDesc.acceptsArguments())
            result.append(String.format("=<%s>", formatArgDescription(optionDesc)));

        List<?> defaultValues = optionDesc.defaultValues();
        if (defaultValues.size() > 0)
            result.append(String.format(" (default: %s)", formatDefaultValues(defaultValues)));

        return result.toString();
    }

    private String formatArgDescription(OptionDescriptor optionDesc) {
        String argDescription = optionDesc.argumentDescription();

        if (argDescription.length() > 0)
            return argDescription;

        String typeIndicator = optionDesc.argumentTypeIndicator();

        if (typeIndicator == null)
            return "value";

        try {
            Class<?> type = Class.forName(typeIndicator);
            return type.isEnum() ?
                    Arrays.stream(type.getEnumConstants()).map(Object::toString).collect(Collectors.joining("|")) :
                    typeIndicator.substring(typeIndicator.lastIndexOf('.') + 1);
        } catch (ClassNotFoundException ex) {
            // typeIndicator is something other than a class name, which can occur
            // in certain cases e.g. where OptionParser.withValuesConvertedBy is used.
            return typeIndicator;
        }
    }

    private Object formatDefaultValues(List<?> defaultValues) {
        return defaultValues.size() == 1 ?
                defaultValues.get(0) :
                defaultValues.toString();
    }

    private String formatOptionDescription(OptionDescriptor optionDesc) {
        StringBuilder output = new StringBuilder();

        String remainder = optionDesc.description().trim();

        // Wrap description text at 80 characters with 8 spaces of indentation and a
        // maximum of 72 chars of text, wrapping on spaces. Strings longer than 72 chars
        // without any spaces (e.g. a URL) are allowed to overflow the 80-char margin.
        while (remainder.length() > 72) {
            int idxFirstSpace = remainder.indexOf(' ');
            int chunkLen = idxFirstSpace == -1 ? remainder.length() : Math.max(idxFirstSpace, 73);
            String chunk = remainder.substring(0, chunkLen);
            int idxLastSpace = chunk.lastIndexOf(' ');
            int idxBreak = idxLastSpace > 0 ? idxLastSpace : chunk.length();
            String line = remainder.substring(0, idxBreak);
            output.append(formatLine(line));
            remainder = remainder.substring(chunk.length() - (chunk.length() - idxBreak)).trim();
        }

        if (remainder.length() > 0)
            output.append(formatLine(remainder));

        return output.toString();
    }

    private String formatLine(String line) {
        return String.format("        %s\n", line.trim());
    }
}
