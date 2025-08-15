package com.notenoughrunes.model;

import java.util.Set;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class NERShop
{
	private final String name;
	private final String sellMultiplier;
	private final String location;
	private final boolean isMembers;
	private final String coords;
	private final String plane;
	private final String mapID;
	private final Set<NERShopItem> items;
}
