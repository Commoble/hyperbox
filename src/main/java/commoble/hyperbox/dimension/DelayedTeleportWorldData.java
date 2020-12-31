package commoble.hyperbox.dimension;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import commoble.hyperbox.Hyperbox;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.storage.WorldSavedData;

// we can't teleport players from onBlockActivated as there are assumptions
// in the right click processing that assume a player's world does not change
// so what we'll do is schedule a teleport to occur one tick later
public class DelayedTeleportWorldData extends WorldSavedData
{
	public static final String DATA_KEY = Hyperbox.MODID + ":delayed_teleports";
	
	private List<TeleportEntry> entries = new ArrayList<>();

	public DelayedTeleportWorldData()
	{
		super(DATA_KEY);
	}
	
	public static DelayedTeleportWorldData get(ServerWorld world)
	{
		return world.getSavedData().getOrCreate(DelayedTeleportWorldData::new, DATA_KEY);
	}
	
	/**
	 * Does *not* create worlds that don't already exist
	 * So they should be created by the thing that schedules the tick, if possible
	 * @param world The world that is being ticked and contains a data instance
	 */
	public static void tick(ServerWorld world)
	{
		MinecraftServer server = world.getServer();
		DelayedTeleportWorldData data = get(world);
		List<TeleportEntry> list = data.entries;
		data.entries = new ArrayList<>();
		for (TeleportEntry entry : list)
		{
			@Nullable ServerPlayerEntity player = server.getPlayerList().getPlayerByUUID(entry.playerUUID);
			@Nullable ServerWorld targetWorld = server.getWorld(entry.targetWorld);
			if (player != null && targetWorld != null && player.world == world)
			{
				DimensionHelper.sendPlayerToDimension(player, targetWorld, entry.targetVec);
			}
		}
	}
	
	public void addPlayer(PlayerEntity player, RegistryKey<World> destination, Vector3d targetVec)
	{
		this.entries.add(new TeleportEntry(PlayerEntity.getUUID(player.getGameProfile()), destination, targetVec));
	}

	@Override
	public void read(CompoundNBT nbt)
	{
		// noop, data is transient
		// we're only using this so we can associate teleports with specific worlds (instead of saving data statically)
	}

	@Override
	public CompoundNBT write(CompoundNBT compound)
	{
		return compound;
	}

	static class TeleportEntry
	{
		final UUID playerUUID;
		final RegistryKey<World> targetWorld;
		final Vector3d targetVec;
		
		public TeleportEntry(UUID playerUUID, RegistryKey<World> targetWorld, Vector3d targetVec)
		{
			this.playerUUID = playerUUID;
			this.targetWorld = targetWorld;
			this.targetVec = targetVec;
		}
	}
}
