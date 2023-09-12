package com.notenoughrunes;

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
import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class H2Importer
{
	
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
		File outputFile = new File(outputPath + ".mv.db"); // h2 add .mv.db suffix
		if (outputFile.exists())
		{
			log.warn("Output file {} already exists, overwriting...", outputFile);
			Files.delete(outputFile.toPath());
		}

		Connection dbConn = DriverManager.getConnection(
			buildConnectionString(
				outputPath,
				"TRACE_LEVEL_SYSTEM_OUT=1", // ERROR
				"TRACE_LEVEL_FILE=0", // OFF
				"MODE=MYSQL"
			)
		);

		for (ImportStep step : STEPS)
		{
			step.run(dbConn);
		}
	}
	
	private static String buildConnectionString(String fileName, String... parameters) 
	{
		StringBuilder sb = new StringBuilder("jdbc:h2:file:");
		sb.append(fileName);
		
		for (String p : parameters) {
			sb.append(';');
			sb.append(p);
		}
		
		return sb.toString();
	}

}