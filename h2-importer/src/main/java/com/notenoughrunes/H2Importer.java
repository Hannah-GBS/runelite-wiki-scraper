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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.similarity.LevenshteinDistance;

@Slf4j
@RequiredArgsConstructor
public class H2Importer
{

	public static final String SELECT_LAST = "SELECT last_insert_rowid()";
	
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
		
//		for (String p : parameters) {
//			sb.append(';');
//			sb.append(p);
//		}
		
		return sb.toString();
	}

	public static int getItemId(Set<NERInfoItem> items, String itemName, String version) {
		Set<NERInfoItem> matchedItems = items.stream()
			.filter(item -> item.getName().contains(itemName) || itemName.contains(item.getName())
			|| item.getGroup().contains(itemName) || itemName.contains(item.getGroup()))
			.collect(Collectors.toSet());

		return matchedItems.stream()
			.min(compareNameAndGroup(itemName, version))
			.orElse(new NERInfoItem("null item", "", "", "", "", 0, false, false))
			.getItemID();
	}

	private static Comparator<NERInfoItem> compareNameAndGroup(String itemName, String version) {
		return Comparator.comparing((NERInfoItem item) -> new LevenshteinDistance().apply(item.getName(), itemName))
			.thenComparing(item -> new LevenshteinDistance().apply(item.getGroup(), itemName))
			.thenComparing(item -> new LevenshteinDistance().apply(item.getVersion() != null ? item.getVersion() : "", version != null ? version : ""));
	}

	public static int getLast(Connection db) throws Exception
	{
		try (PreparedStatement ps = db.prepareStatement(SELECT_LAST))
		{
			ResultSet rs = ps.executeQuery();
			rs.first();
			return rs.getInt(1);
		}
		catch (Exception e)
		{
			log.error("select last failed");
			throw e;
		}
	}

}