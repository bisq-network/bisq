package bisq.core.util;

import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DisplayPropertiesCheckTest {
	
	static Logger LOGGER = LoggerFactory.getLogger(DisplayPropertiesCheckTest.class);
	
//	@Ignore
	@Test
	public void testPrune() throws Exception {
		File baseDirectory = new File("../");
		Properties propertyEntries = new Properties();
		FileInputStream in = new FileInputStream("./src/test/java/bisq/core/util/DisplayPropertiesCheckTest.ignorelist");
		BufferedReader reader = new BufferedReader(new InputStreamReader(in));
		
		List<String> ignorePatternList = new ArrayList<>(); 
		String line;
		while ((line = reader.readLine()) != null) {
			ignorePatternList.add(line.trim());
		}
		reader.close();
		in.close();
		
		File propertiesFilePruned = new File("./src/main/resources/i18n/displayStrings.pruned.properties");
		File propertiesFile = new File("./src/main/resources/i18n/displayStrings.properties");
		Files.copy(propertiesFile.toPath(), propertiesFilePruned.toPath(), StandardCopyOption.REPLACE_EXISTING);
		propertyEntries.load(new FileInputStream(propertiesFile));
		List<String> unusedKeyList = new ArrayList<>();
		String[] extensions = new String[] { "java" };
		LOGGER.info("Getting all .java files in " + baseDirectory.getCanonicalPath() + " including those in subdirectories");
		List<File> files = (List<File>) FileUtils.listFiles(baseDirectory, extensions, true);
		Scanner scanner;
		Pattern pattern;
		int count = 0;
		for (Object key : propertyEntries.keySet().stream().filter(prop -> !keyMatches(prop, ignorePatternList)).collect(Collectors.toList())) {
			pattern = Pattern.compile(String.valueOf(key));
			boolean found = false;
			for (File file : files) {
				scanner = new Scanner(file);
				found = scanner.findWithinHorizon(pattern, 0) != null;
				if (found) {
					break;
				}
			}
			if (!found) {
				count++;
				removePropertiesEntry(propertiesFilePruned, (String) key);
				unusedKeyList.add((String) key);
				LOGGER.info("UNUSED: [" + count + "] " + key);
			}
		}
		
		LOGGER.info(count + " entries removed from displayStrings.properties and stored in displayStrings.pruned.properties.");
		
	}
	
	private boolean keyMatches(Object key, List<String> ignorePatternList) {
		boolean flag = false;
		for (String pattern : ignorePatternList) {
			flag = key != null && key.toString().matches(pattern);
			if (flag) {
				LOGGER.info("IGNORED: '" + key + "' matches regex pattern => " + pattern);
				break;
			}
		}
		return flag;
	}
	
	private void removePropertiesEntry(File propertiesFile, String key) throws IOException {
		List<String> lines = FileUtils.readLines(propertiesFile);
		List<String> updatedLines = new ArrayList<>();
		boolean flag = false;
		for (String line : lines) {
			if (!flag) {
				if (!line.replace(" ", "").startsWith(key + "=")) {
					updatedLines.add(line);
				} else {			
					LOGGER.info("SKIPPING: '" + key + "'");
					flag = line.endsWith("\\") ? true : false;
				}
			} else {
				LOGGER.info("SKIPPING (MULTILINE): '" + key + "'");
				flag = line.endsWith("\\") ? true : false;
			}
		}
		
		assertTrue(lines.size() > updatedLines.size());
		
		FileUtils.writeLines(propertiesFile, updatedLines, false);
	}

}
