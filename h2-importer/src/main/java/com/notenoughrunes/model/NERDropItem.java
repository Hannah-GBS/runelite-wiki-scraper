package com.notenoughrunes.model;

import java.util.Set;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class NERDropItem
{
	private final String name;

	private final Set<NERDropSource> dropSources;
}
