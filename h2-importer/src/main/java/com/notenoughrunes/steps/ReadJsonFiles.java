package com.notenoughrunes.steps;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.notenoughrunes.H2Importer;
import com.notenoughrunes.gson.BooleanTypeAdapter;
import com.notenoughrunes.model.NERDropItem;
import com.notenoughrunes.model.NERInfoItem;
import com.notenoughrunes.model.NERProductionRecipe;
import com.notenoughrunes.model.NERShop;
import com.notenoughrunes.model.NERSpawnGroup;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.sql.Connection;
import java.util.Set;
import lombok.Getter;

public class ReadJsonFiles implements ImportStep
{

	public static final Gson GSON = new GsonBuilder()
		.registerTypeAdapter(boolean.class, new BooleanTypeAdapter())
		.create();

	@Getter
	private static Set<NERInfoItem> itemInfoData;

	@Getter
	private static Set<NERProductionRecipe> itemProductionData;

	@Getter
	private static Set<NERShop> itemShopData;

	@Getter
	private static Set<NERSpawnGroup> itemSpawnData;

	@Getter
	private static Set<NERDropItem> itemDropData;

	@Override
	public void run(Connection ignored) throws Exception
	{
		// @formatter:off
		itemInfoData = readJsonFile("items-info.min.json", new TypeToken<>() {});
		itemProductionData = readJsonFile("items-production.json", new TypeToken<>() {});
		itemShopData = readJsonFile("items-shopitems.min.json", new TypeToken<>() {});
		itemSpawnData = readJsonFile("items-spawns.min.json", new TypeToken<>() {});
		itemDropData = readJsonFile("items-drop-sources.min.json", new TypeToken<>() {});
		// @formatter:on
	}

	private <T> T readJsonFile(String fileName, TypeToken<T> typ) throws Exception
	{
		File jsonFile = new File(H2Importer.getInputDir(), fileName);
		if (!jsonFile.exists())
		{
			throw new FileNotFoundException("Expected JSON input file " + fileName + " does not exist");
		}

		try (FileReader jsonReader = new FileReader(jsonFile))
		{
			return GSON.fromJson(jsonReader, typ.getType());
		}
	}

}
