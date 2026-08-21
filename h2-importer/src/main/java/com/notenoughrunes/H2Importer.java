package com.notenoughrunes;

import com.notenoughrunes.model.NERInfoItem;
import com.notenoughrunes.steps.CreateTables;
import com.notenoughrunes.steps.ImportItems;
import com.notenoughrunes.steps.ImportProductions;
import com.notenoughrunes.steps.ImportShops;
import com.notenoughrunes.steps.ImportSpawnsDrops;
import com.notenoughrunes.steps.ImportStep;
import com.notenoughrunes.steps.ReadJsonFiles;
import java.io.File;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class H2Importer
{
	static final Pattern CLUE_SCROLL_PATTERN = Pattern.compile("Clue scroll \\((.*)\\)");
	static final String SCROLL_BOX_STRING = "Scroll box ($1)";

	@Getter
	private static File inputDir;

	private static final List<ImportStep> STEPS = Arrays.asList(
		new ReadJsonFiles(),
		new CreateTables(),
		new ImportItems(),
		new ImportProductions(),
		new ImportShops(),
		new ImportSpawnsDrops()
	);

	public static void main(String[] args) throws Exception
	{
		if (args.length < 2)
		{
			throw new IllegalArgumentException("No input/output locations specified.");
		}

		inputDir = new File(args[0]);
		if (!inputDir.exists() || !inputDir.isDirectory())
		{
			throw new IllegalArgumentException("inputDir is not a valid directory path");
		}

		String outputPath = new File(args[1]).getAbsolutePath();
		File outputFile = new File(outputPath);
		if (outputFile.exists())
		{
			log.warn("Output file {} already exists, overwriting...", outputFile);
			Files.delete(outputFile.toPath());
		}

		try (Connection dbConn = DriverManager.getConnection(
			buildConnectionString(outputPath)
		))
		{
			dbConn.setAutoCommit(false);
			for (ImportStep step : STEPS)
			{
				String stepName = step.getClass().getSimpleName();
				log.info("[{}] Start", stepName);
				Instant start = Instant.now();
				step.run(dbConn);
				Duration elapsed = Duration.between(start, Instant.now());
				log.info("[{}] Complete ({})", stepName, elapsed);
			}
		}
	}
	
	private static String buildConnectionString(String fileName, String... parameters) 
	{
		StringBuilder sb = new StringBuilder("jdbc:sqlite:");
		sb.append(fileName);

		return sb.toString();
	}


	public static int getItemIdPrecise(Set<NERInfoItem> items, String itemGroupName, String version) {
		String groupName;
		Matcher clueScrollMatcher = CLUE_SCROLL_PATTERN.matcher(itemGroupName);
		if (clueScrollMatcher.find())
		{
			groupName = clueScrollMatcher.replaceAll(SCROLL_BOX_STRING);
		}
		else
		{
			groupName = itemGroupName;
		}

		Optional<NERInfoItem> matchedItem = items.stream().filter(item ->
		{
			if (version != null && item.getVersion() != null)
			{
				return item.getGroup().equalsIgnoreCase(groupName) && item.getVersion().equalsIgnoreCase(version);
			} else {
				return item.getGroup().equalsIgnoreCase(groupName) && item.isDefaultVersion();
			}
		}).findFirst();

		if (matchedItem.isPresent()) {
			return matchedItem.get().getItemID();
		} else {
			log.warn("No item matched for {}#{}", itemGroupName, version);
			return 20594; // Bank filler ID
		}
	}
}