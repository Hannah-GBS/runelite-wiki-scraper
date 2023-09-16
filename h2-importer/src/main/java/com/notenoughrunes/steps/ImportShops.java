package com.notenoughrunes.steps;

import com.notenoughrunes.H2Importer;
import com.notenoughrunes.model.NERInfoItem;
import com.notenoughrunes.model.NERShop;
import com.notenoughrunes.model.NERShopItem;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ImportShops implements ImportStep
{

	//language=SQL
	private static final String INSERT_SHOP =
		"INSERT INTO SHOPS (NAME, SELL_MULTIPLIER, LOCATION, IS_MEMBERS) VALUES (" +
			"?," +
			"?," +
			"?," +
			"?" +
			")";

	//language=SQL
	private static final String INSERT_SHOP_ITEM =
		"INSERT INTO SHOP_ITEMS (ITEM_NAME, ITEM_VERSION, ITEM_ID, SHOP_ID, CURRENCY, STOCK, BUY_PRICE, SELL_PRICE) VALUES (" +
			"?," +
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
		Set<NERShop> shops = ReadJsonFiles.getItemShopData();
		Set<NERInfoItem> infoItems = ReadJsonFiles.getItemInfoData();

		for (NERShop shop : shops)
		{
			int shopId;
			try (PreparedStatement ps = db.prepareStatement(INSERT_SHOP, Statement.RETURN_GENERATED_KEYS))
			{
				writeShop(shop, ps);
				ps.executeUpdate();

				ResultSet rs = ps.getGeneratedKeys();
				rs.first();
				shopId = rs.getInt("ID");
			}

			try (PreparedStatement ps = db.prepareStatement(INSERT_SHOP_ITEM))
			{
				for (NERShopItem item : shop.getItems())
				{
					writeShopItem(item, shopId, ps, infoItems);
					ps.addBatch();
				}

				ps.executeBatch();
			}
		}
	}

	private void writeShop(NERShop shop, PreparedStatement ps) throws SQLException
	{
		int ix = 1;
		ps.setString(ix++, shop.getName());
		ps.setString(ix++, shop.getSellMultiplier());
		ps.setString(ix++, shop.getLocation());
		ps.setBoolean(ix++, shop.isMembers());
	}

	private void writeShopItem(NERShopItem item, int shopId, PreparedStatement ps, Set<NERInfoItem> items) throws SQLException
	{
		int ix = 1;
		ps.setString(ix++, item.getName());
		ps.setString(ix++, item.getVersion());
		ps.setInt(ix++, H2Importer.getItemId(items, item.getName(), item.getVersion()));
		ps.setInt(ix++, shopId);
		ps.setString(ix++, item.getCurrency());
		ps.setString(ix++, item.getStock());
		ps.setString(ix++, item.getBuyPrice());
		ps.setString(ix++, item.getSellPrice());
	}

}
