package commoble.hyperbox.dimension;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.BiMap;
import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Lifecycle;

import commoble.hyperbox.Hyperbox;
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
			
			Hyperbox.CHANNEL.sendToServer(new UpdateDimensionsPacket(key, false));
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
		List<IBorderListener> listeners = overworldBorder.listeners;
		IBorderListener target = null;
		for (IBorderListener listener : listeners)
		{
			if (listener instanceof IBorderListener.Impl)
			{
				IBorderListener.Impl impl = (IBorderListener.Impl)listener;
				WorldBorder border = impl.worldBorder;
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
		SimpleRegistry<Dimension> oldRegistry = settings.field_236208_h_;
		SimpleRegistry<Dimension> newRegistry = new SimpleRegistry<Dimension>(Registry.DIMENSION_KEY, oldRegistry.getLifecycle());
		
		ObjectList<Dimension> oldList = oldRegistry.entryList;
		BiMap<RegistryKey<Dimension>, Dimension> oldMap = oldRegistry.keyToObjectMap;
		BiMap<Dimension, RegistryKey<Dimension>> oldInvertedMap = oldMap.inverse();
		Map<Dimension, Lifecycle> oldLifecycles = oldRegistry.objectToLifecycleMap;
		
		for (Dimension dimension : oldList)
		{
			RegistryKey<Dimension> key = oldInvertedMap.get(dimension);
			if (!keysToRemove.contains(key))
			{
				newRegistry.register(key, dimension, oldLifecycles.get(dimension));
			}
		}
		
		// then replace the old registry with the new registry
		settings.field_236208_h_= newRegistry;
	}
}
