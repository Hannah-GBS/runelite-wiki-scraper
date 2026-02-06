package com.notenoughrunes.model;

import java.util.Set;
import javax.annotation.Nullable;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class NERDropItem
{
	private final String group;

	private final String name;

	@Nullable
	private final String version;

	private final Set<NERDropSource> dropSources;
}
