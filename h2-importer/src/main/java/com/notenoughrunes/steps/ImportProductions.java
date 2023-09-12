package com.notenoughrunes.steps;

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
		"INSERT INTO PRODUCTION_RECIPES (TICKS, FACILITIES, TOOLS, IS_MEMBERS, OUTPUT_ITEM_NAME, OUTPUT_ITEM_VERSION, OUTPUT_QUANTITY, OUTPUT_QUANTITY_NOTE, OUTPUT_SUBTEXT) VALUES (" +
			"?," + // 1 ticks
			"?," + // 2 facilities
			"?," + // 3 tools
			"?," + // 4 is_members
			"?," + // 5 item_name
			"?," + // 6 item_version
			"?," + // 7 quantity
			"?," + // 8 quantity_note
			"?" + // 9 subtext
			")";
	
	//language=SQL
	private static final String INSERT_MATERIAL =
		"INSERT INTO PRODUCTION_MATERIALS (RECIPE_ID, ITEM_NAME, ITEM_VERSION, QUANTITY) VALUES (" +
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
		
		for (NERProductionRecipe recipe : recipes)
		{
			int recipeId;
			try (PreparedStatement ps = db.prepareStatement(INSERT_RECIPE, Statement.RETURN_GENERATED_KEYS))
			{
				writeRecipe(recipe, ps);
				ps.executeUpdate();

				ResultSet rs = ps.getGeneratedKeys();
				rs.first();
				recipeId = rs.getInt("ID");
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
					writeMaterial(recipeId, material, ps);
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
	}
	
	private void writeRecipe(NERProductionRecipe recipe, PreparedStatement ps) throws Exception
	{
		int ix = 1;
		ps.setString(ix++, recipe.getTicks());
		ps.setString(ix++, recipe.getFacilities());
		ps.setString(ix++, recipe.getTools());
		ps.setBoolean(ix++, recipe.isMembers());
		ps.setString(ix++, recipe.getOutput().getName());
		ps.setString(ix++, recipe.getOutput().getVersion());
		ps.setString(ix++, recipe.getOutput().getQuantity());
		ps.setString(ix++, recipe.getOutput().getQuantityNote());
		ps.setString(ix++, recipe.getOutput().getSubtext());
	}
	
	private void writeMaterial(int recipeId, NERProductionMaterial material, PreparedStatement ps) throws Exception
	{
		int ix = 1;
		ps.setInt(ix++, recipeId);
		ps.setString(ix++, material.getName());
		ps.setString(ix++, material.getVersion());
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
