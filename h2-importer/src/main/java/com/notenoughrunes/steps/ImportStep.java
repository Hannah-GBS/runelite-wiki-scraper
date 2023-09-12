package com.notenoughrunes.steps;

import java.sql.Connection;

public interface ImportStep
{
	
	void run(Connection db) throws Exception;
	
}
