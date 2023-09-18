package com.notenoughrunes.steps;

import com.notenoughrunes.H2Importer;
import static com.notenoughrunes.H2Importer.SELECT_LAST;
import static com.notenoughrunes.H2Importer.getLast;
import com.notenoughrunes.model.NERInfoItem;
import com.notenoughrunes.model.NERProductionMaterial;
import com.notenoughrunes.model.NERProductionRecipe;
import com.notenoughrunes.model.NERProductionSkill;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ImportProductions implements ImportStep
{

	//language=SQL
	private static final String INSERT_RECIPE =
		"INSERT INTO PRODUCTION_RECIPES (TICKS, FACILITIES, TOOLS, IS_MEMBERS, OUTPUT_ITEM_NAME, OUTPUT_ITEM_VERSION, OUTPUT_ITEM_ID, OUTPUT_QUANTITY, OUTPUT_QUANTITY_NOTE, OUTPUT_SUBTEXT) VALUES (" +
			"?," + // 1 ticks
			"?," + // 2 facilities
			"?," + // 3 tools
			"?," + // 4 is_members
			"?," + // 5 item_name
			"?," + // 6 item_version
			"?," + // 7 item_id
			"?," + // 8 quantity
			"?," + // 9 quantity_note
			"?" + // 10 subtext
			") RETURNING ID";
	
	//language=SQL
	private static final String INSERT_MATERIAL =
		"INSERT INTO PRODUCTION_MATERIALS (RECIPE_ID, ITEM_NAME, ITEM_VERSION, ITEM_ID, QUANTITY) VALUES (" +
			"?," +
			"?," +
			"?," +
			"?," +
			"?" +
			");";
	
	//language=SQL
	private static final String INSERT_SKILL =
		"INSERT INTO PRODUCTION_SKILLS (RECIPE_ID, NAME, LEVEL, EXPERIENCE, IS_BOOSTABLE) VALUES (" +
			"?," +
			"?," +
			"?," +
			"?," +
			"?" +
			");";
	
	@Override
	public void run(Connection db) throws Exception
	{
		Set<NERProductionRecipe> recipes = ReadJsonFiles.getItemProductionData();
		Set<NERInfoItem> infoItems = ReadJsonFiles.getItemInfoData();

		for (NERProductionRecipe recipe : recipes)
		{
			int recipeId;
			try (PreparedStatement ps = db.prepareStatement(INSERT_RECIPE))
			{
				writeRecipe(recipe, ps, infoItems);
				ResultSet rs = ps.executeQuery();
				rs.next();
				recipeId = rs.getInt(1);
			}
			catch (Exception e)
			{
				log.error("recipe insert failed for {}", recipe);
				throw e;
			}

			try (PreparedStatement ps = db.prepareStatement(INSERT_MATERIAL))
			{
				for (NERProductionMaterial material : recipe.getMaterials())
				{
					writeMaterial(recipeId, material, ps, infoItems);
					ps.addBatch();
				}
				
				ps.executeBatch();
			}

			try (PreparedStatement ps = db.prepareStatement(INSERT_SKILL))
			{
				for (NERProductionSkill material : recipe.getSkills())
				{
					writeSkill(recipeId, material, ps);
					ps.addBatch();
				}
				
				ps.executeBatch();
			}
		}
		db.commit();
	}
	
	private void writeRecipe(NERProductionRecipe recipe, PreparedStatement ps, Set<NERInfoItem> items) throws Exception
	{
		int ix = 1;
		ps.setString(ix++, recipe.getTicks());
		ps.setString(ix++, recipe.getFacilities());
		ps.setString(ix++, recipe.getTools());
		ps.setBoolean(ix++, recipe.isMembers());
		ps.setString(ix++, recipe.getOutput().getName());
		ps.setString(ix++, recipe.getOutput().getVersion());
		ps.setInt(ix++, H2Importer.getItemId(items, recipe.getOutput().getName(), recipe.getOutput().getVersion()));
		ps.setString(ix++, recipe.getOutput().getQuantity());
		ps.setString(ix++, recipe.getOutput().getQuantityNote());
		ps.setString(ix++, recipe.getOutput().getSubtext());
	}
	
	private void writeMaterial(int recipeId, NERProductionMaterial material, PreparedStatement ps, Set<NERInfoItem> items) throws Exception
	{
		int ix = 1;
		ps.setInt(ix++, recipeId);
		ps.setString(ix++, material.getName());
		ps.setString(ix++, material.getVersion());
		ps.setInt(ix++, H2Importer.getItemId(items, material.getName(), material.getVersion()));
		ps.setString(ix++, material.getQuantity());
	}
	
	private void writeSkill(int recipeId, NERProductionSkill skill, PreparedStatement ps) throws Exception
	{
		int ix = 1;
		ps.setInt(ix++, recipeId);
		ps.setString(ix++, skill.getName());
		ps.setString(ix++, skill.getLevel());
		ps.setString(ix++, skill.getExperience());
		ps.setBoolean(ix++, skill.isBoostable());
	}
	
}
