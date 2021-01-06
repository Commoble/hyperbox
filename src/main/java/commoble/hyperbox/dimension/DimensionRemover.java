package commoble.hyperbox.dimension;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.BiMap;
import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Lifecycle;

import commoble.hyperbox.ReflectionHelper;
import commoble.hyperbox.ReflectionHelper.MutableInstanceField;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.SimpleRegistry;
import net.minecraft.world.Dimension;
import net.minecraft.world.World;
import net.minecraft.world.border.IBorderListener;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.gen.settings.DimensionGeneratorSettings;
import net.minecraft.world.server.ServerWorld;

public class DimensionRemover
{
	public static final Logger LOGGER = LogManager.getLogger();


	/** fields in SimpleRegistry needed for unregistering dimensions **/
	public static class SimpleRegistryAccess
	{
		@SuppressWarnings("rawtypes")
		public static final MutableInstanceField<SimpleRegistry, ObjectList<Dimension>> entryList =
			ReflectionHelper.getInstanceField(SimpleRegistry.class, "field_243533_bf");
		@SuppressWarnings("rawtypes")
		public static final MutableInstanceField<SimpleRegistry, BiMap<RegistryKey<Dimension>, Dimension>> keyToObjectMap =
			ReflectionHelper.getInstanceField(SimpleRegistry.class, "field_239649_bb_");
		@SuppressWarnings("rawtypes")
		public static final MutableInstanceField<SimpleRegistry, Map<Dimension, Lifecycle>> objectToLifecycleMap =
			ReflectionHelper.getInstanceField(SimpleRegistry.class, "field_243535_bj");
	}
	
	public static class DimensionGeneratorSettingsAccess
	{
		public static final MutableInstanceField<DimensionGeneratorSettings, SimpleRegistry<Dimension>> dimensionRegistry =
			ReflectionHelper.getInstanceField(DimensionGeneratorSettings.class, "field_236208_h_");
	}
	
	public static class WorldBorderAccess
	{
		public static final Function<WorldBorder, List<IBorderListener>> listeners =
			ReflectionHelper.getInstanceFieldGetter(WorldBorder.class, "field_177758_a");
	}
	
	public static class BorderListenerAccess
	{
		public static final Function<IBorderListener.Impl, WorldBorder> worldBorder =
			ReflectionHelper.getInstanceFieldGetter(IBorderListener.Impl.class, "field_219590_a");
	}

	/**
	 * 
	 * @param server A server instance
	 * @param keys The IDs of the dimensions to unregister
	 * @return the set of dimension IDs that were successfully unregistered, and a list of the worlds corresponding to them.
	 * Be aware that these serverworlds will no longer be accessible via the MinecraftServer after calling this method.
	 */
	@SuppressWarnings("deprecation")
	public static Pair<Set<RegistryKey<Dimension>>, List<ServerWorld>> unregisterDimensions(MinecraftServer server, Set<RegistryKey<World>> keys)
	{
		// we need to remove the dimension/world from three places
		// the dimension registry, the world registry, and the world border listener
		// the world registry is just a simple map and the world border listener has a remove() method
		// the dimension registry has five sub-collections that need to be cleaned up
		// we should also probably move players from that world into the overworld if possible
		DimensionGeneratorSettings dimensionGeneratorSettings = server.getServerConfiguration()
			.getDimensionGeneratorSettings();
		
		// set of keys whose worlds were found and removed
		Set<RegistryKey<Dimension>> removedKeys = new HashSet<>();
		List<ServerWorld> removedWorlds = new ArrayList<>(keys.size());
		for (RegistryKey<World> key : keys)
		{
			RegistryKey<Dimension> dimensionKey = RegistryKey.getOrCreateKey(Registry.DIMENSION_KEY, key.getLocation());
			ServerWorld removedWorld = server.forgeGetWorldMap().remove(key);
			if (removedWorld != null)
			{
				// iterate over a copy as the world will remove players from the original list
				for (ServerPlayerEntity player : Lists.newArrayList((removedWorld.getPlayers())))
				{
					DimensionHelper.ejectPlayerFromDeadWorld(player);
//					server.getPlayerList().func_232644_a_(player, true); // respawn player
//					player.connection.disconnect(new StringTextComponent("Localized existence failure"));
//					Vector3d targetVec = 
//					DimensionHelper.sendPlayerToDimension(player, overworld, targetVec);
				}
				removedWorld.save(null, false, removedWorld.disableLevelSaving);
				removeWorldBorderListener(server, removedWorld);
				removedKeys.add(dimensionKey);
				removedWorlds.add(removedWorld);
			}
		}

		if (!removedKeys.isEmpty())
		{
			removeRegisteredDimensions(server, dimensionGeneratorSettings, removedKeys);
			server.markWorldsDirty();
		}
		return Pair.of(removedKeys, removedWorlds);
	}
	
	private static void removeWorldBorderListener(MinecraftServer server, ServerWorld removedWorld)
	{
		ServerWorld overworld = server.getWorld(World.OVERWORLD);
		WorldBorder overworldBorder = overworld.getWorldBorder();
		List<IBorderListener> listeners = WorldBorderAccess.listeners.apply(overworldBorder);
		IBorderListener target = null;
		for (IBorderListener listener : listeners)
		{
			if (listener instanceof IBorderListener.Impl)
			{
				IBorderListener.Impl impl = (IBorderListener.Impl)listener;
				WorldBorder border = BorderListenerAccess.worldBorder.apply(impl);
				if (removedWorld.getWorldBorder() == border)
				{
					target = listener;
					break;
				}
			}
		}
		if (target != null)
		{
			overworldBorder.removeListener(target);
		}
	}
	
	private static void removeRegisteredDimensions(MinecraftServer server, DimensionGeneratorSettings settings, Set<RegistryKey<Dimension>> keysToRemove)
	{
		// get all the old dimensions except the given one, add them to a new registry in the same order
		SimpleRegistry<Dimension> oldRegistry = DimensionGeneratorSettingsAccess.dimensionRegistry.get(settings);
		SimpleRegistry<Dimension> newRegistry = new SimpleRegistry<Dimension>(Registry.DIMENSION_KEY, oldRegistry.getLifecycle());
		
		ObjectList<Dimension> oldList = SimpleRegistryAccess.entryList.get(oldRegistry);
		BiMap<RegistryKey<Dimension>, Dimension> oldMap = SimpleRegistryAccess.keyToObjectMap.get(oldRegistry);
		BiMap<Dimension, RegistryKey<Dimension>> oldInvertedMap = oldMap.inverse();
		Map<Dimension, Lifecycle> oldLifecycles = SimpleRegistryAccess.objectToLifecycleMap.get(oldRegistry);
		
		for (Dimension dimension : oldList)
		{
			RegistryKey<Dimension> key = oldInvertedMap.get(dimension);
			if (!keysToRemove.contains(key))
			{
				newRegistry.register(key, dimension, oldLifecycles.get(dimension));
			}
		}
		
		// then replace the old registry with the new registry
		DimensionGeneratorSettingsAccess.dimensionRegistry.set(settings, newRegistry);
	}
}
