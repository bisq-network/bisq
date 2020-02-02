package bisq.core.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DisplayPropertiesCleaner {
	
	static Logger LOGGER = LoggerFactory.getLogger(DisplayPropertiesCleaner.class);

	public static void main(String[] args) {
		DisplayPropertiesCleaner cleaner = new DisplayPropertiesCleaner();
		try {
			cleaner.process();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private void process() throws Exception {
		File baseDirectory = new File("../");
		Properties propertyEntries = new Properties();
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
		for(Object key : propertyEntries.keySet()) {
			pattern = Pattern.compile(String.valueOf(key));
			boolean found = false;
			for (File file : files) {
				scanner = new Scanner(file);
				found = scanner.findWithinHorizon(pattern, 0) != null;
				if(found) {
					break;
				}
			}
			if(!found) {
				count++;
				removePropertiesEntry(propertiesFilePruned, (String) key);
				unusedKeyList.add((String) key);
				LOGGER.info("UNUSED: [" + count + "] " + key);
			}
		}
		
		LOGGER.info(count + " entries removed from displayStrings.properties and stored in displayStrings.pruned.properties.");
		
	}
	
	private void removePropertiesEntry(File propertiesFile, String key) throws IOException {
		List<String> lines = FileUtils.readLines(propertiesFile);
		List<String> updatedLines = new ArrayList<>();
		boolean flag = false;
		for(String line : lines) {
			if(!flag) {
				if(!line.startsWith(key)) {
					updatedLines.add(line);
				} else {
					//TODO Trigger flag to remove subsequent lines with no "=" (to handle values spanning multiple lines)
					flag = true;
				}
			} else {
				if(line.contains("=")) {
					flag = false;
					updatedLines.add(line);
				}
			}
		}
		FileUtils.writeLines(propertiesFile, updatedLines, false);
	}

}
