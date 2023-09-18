package com.notenoughrunes.steps;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.Statement;

public class CreateTables implements ImportStep
{
	@Override
	public void run(Connection db) throws Exception
	{
		String createTablesSql;
		try (InputStream schemaStream = getClass().getResourceAsStream("/create-tables.sql"))
		{
			assert schemaStream != null;
			createTablesSql = new String(schemaStream.readAllBytes());
		}

		try (Statement stmt = db.createStatement())
		{
			stmt.executeUpdate(createTablesSql);
		}
		db.commit();
	}
}
