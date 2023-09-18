package com.notenoughrunes.steps;

import com.notenoughrunes.model.NERInfoItem;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ImportItems implements ImportStep
{

	//language=SQL
	private static final String INSERT_ITEM_GROUP =
		"INSERT INTO ITEM_GROUPS (NAME) VALUES (?);";

	//language=SQL
	private static final String INSERT_ITEM =
		"INSERT INTO ITEMS (ID, NAME, EXAMINE_TEXT, GROUP_ID, VERSION, URL, IS_MEMBERS, IS_TRADEABLE, SEARCH_NAME) VALUES (" +
			"?," +
			"?," +
			"?," +
			"(SELECT ID FROM ITEM_GROUPS WHERE NAME = ?)," +
			"?," +
			"?," +
			"?," +
			"?," +
			"?" +
			");";

	@Override
	public void run(Connection db) throws Exception
	{
		Set<NERInfoItem> items = ReadJsonFiles.getItemInfoData();
		Set<String> groups = items.stream()
			.map(NERInfoItem::getGroup)
			.collect(Collectors.toSet());

		try (PreparedStatement stmt = db.prepareStatement(INSERT_ITEM_GROUP))
		{
			for (String groupName : groups)
			{
				stmt.setString(1, groupName);
				stmt.addBatch();
			}

			stmt.executeBatch();
		}
		db.commit();

		Map<Integer, String> seenIds = new HashMap<>();
		try (PreparedStatement stmt = db.prepareStatement(INSERT_ITEM))
		{
			for (NERInfoItem item : items)
			{
				if (seenIds.containsKey(item.getItemID()))
				{
					log.warn("item [{}] has a duplicate id [{}] with [{}], skipping",
						item.getName(), item.getItemID(), seenIds.get(item.getItemID())
					);
					continue;
				}

				int ix = 1;
				stmt.setInt(ix++, item.getItemID());
				stmt.setString(ix++, item.getName());
				stmt.setString(ix++, item.getExamineText());
				stmt.setString(ix++, item.getGroup());
				stmt.setString(ix++, item.getVersion());
				stmt.setString(ix++, item.getUrl());
				stmt.setBoolean(ix++, item.isMembers());
				stmt.setBoolean(ix++, item.isTradeable());
				stmt.setString(ix++, item.getName().toLowerCase());
				stmt.addBatch();
				seenIds.put(item.getItemID(), item.getName());
			}

			stmt.executeBatch();
		}
		db.commit();
	}

}
