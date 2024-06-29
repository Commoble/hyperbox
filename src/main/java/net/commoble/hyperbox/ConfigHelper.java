package net.commoble.hyperbox;

import java.util.function.Function;

import javax.annotation.Nullable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.neoforged.fml.ModList;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Helper for creating configs and defining complex objects in configs 
 */
public record ConfigHelper(ModConfigSpec.Builder builder)
{
	static final Logger LOGGER = LogManager.getLogger();

	public static <T> T register(
		final String modid,
		final ModConfig.Type configType,
		final Function<ModConfigSpec.Builder, T> configFactory)
	{
		return register(modid, configType, configFactory, null);
	}

	public static <T> T register(
		final String modid,
		final ModConfig.Type configType,
		final Function<ModConfigSpec.Builder, T> configFactory,
		final @Nullable String configName)
	{
		final var mod = ModList.get().getModContainerById(modid).get();
		final org.apache.commons.lang3.tuple.Pair<T, ModConfigSpec> entry = new ModConfigSpec.Builder()
			.configure(configFactory);
		final T config = entry.getLeft();
		final ModConfigSpec spec = entry.getRight();
		if (configName == null)
		{
			mod.registerConfig(configType,spec);
		}
		else
		{
			mod.registerConfig(configType, spec, configName + ".toml");
		}
		
		return config;
	}
}
