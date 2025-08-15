package com.notenoughrunes.steps;

import com.notenoughrunes.model.NERDropItem;
import com.notenoughrunes.model.NERDropSource;
import com.notenoughrunes.model.NERSpawnGroup;
import com.notenoughrunes.model.NERSpawnItem;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ImportSpawnsDrops implements ImportStep
{

	//language=SQL
	private static final String INSERT_SPAWN =
		"INSERT INTO SPAWN_ITEMS (GROUP_ID, NAME, COORDS, LOCATION, IS_MEMBERS, PLANE, MAP_ID) VALUES (" +
			"(SELECT ID FROM ITEM_GROUPS WHERE NAME=?)," +
			"?," +
			"?," +
			"?," +
			"?," +
			"?," +
			"?" +
			")";

	//language=SQL
	private static final String INSERT_DROP =
		"INSERT INTO DROP_SOURCES (ITEM_NAME, SOURCE, QUANTITY_LOW, QUANTITY_HIGH, RARITY, DROP_LEVEL, DROP_TYPE) VALUES (" +
			"?," +
			"?," +
			"?," +
			"?," +
			"?," +
			"?," +
			"?" +
			")";

	@Override
	public void run(Connection db) throws Exception
	{
		Set<NERSpawnGroup> spawnGroups = ReadJsonFiles.getItemSpawnData();
		try (PreparedStatement ps = db.prepareStatement(INSERT_SPAWN))
		{
			for (NERSpawnGroup spawnGroup : spawnGroups)
			{
				for (NERSpawnItem spawn : spawnGroup.getSpawns())
				{
					writeSpawn(spawn, spawnGroup.getGroup(), ps);
					ps.addBatch();
				}
			}

			ps.executeBatch();
		}
		db.commit();

		Set<NERDropItem> drops = ReadJsonFiles.getItemDropData();
		try (PreparedStatement ps = db.prepareStatement(INSERT_DROP))
		{
			for (NERDropItem drop : drops)
			{
				for (NERDropSource source : drop.getDropSources())
				{
					writeDropSource(source, drop.getName(), ps);
					ps.addBatch();
				}
			}

			ps.executeBatch();
		}
		db.commit();
	}

	private void writeSpawn(NERSpawnItem spawn, String group, PreparedStatement ps) throws SQLException
	{
		int ix = 1;
		ps.setString(ix++, group);
		ps.setString(ix++, spawn.getName());
		ps.setString(ix++, spawn.getCoords());
		ps.setString(ix++, spawn.getLocation());
		ps.setBoolean(ix++, spawn.isMembers());
		ps.setString(ix++, spawn.getPlane());
		ps.setString(ix++, spawn.getMapID());
	}

	private void writeDropSource(NERDropSource source, String drop, PreparedStatement ps) throws SQLException
	{
		int ix = 1;
		ps.setString(ix++, drop);
		ps.setString(ix++, source.getSource());
		ps.setInt(ix++, source.getQuantityLow());
		ps.setInt(ix++, source.getQuantityHigh());
		ps.setString(ix++, source.getRarity());
		ps.setString(ix++, source.getDropLevel());
		ps.setString(ix++, source.getDropType());
	}

}
