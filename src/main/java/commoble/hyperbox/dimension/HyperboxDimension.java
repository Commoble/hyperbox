package commoble.hyperbox.dimension;

import commoble.hyperbox.Hyperbox;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.Dimension;
import net.minecraft.world.DimensionType;
import net.minecraft.world.World;

public class HyperboxDimension
{	
	public static Dimension createDimension(MinecraftServer server, RegistryKey<Dimension> key)
	{
		return new Dimension(() -> getDimensionType(server), new HyperboxChunkGenerator(server));
	}
	
	public static DimensionType getDimensionType(MinecraftServer server)
	{
		return server.func_244267_aX() // get dynamic registries
			.getRegistry(Registry.DIMENSION_TYPE_KEY)
			.getOrThrow(Hyperbox.DIMENSION_TYPE_KEY);
	}
	
	public static boolean isHyperboxDimension(RegistryKey<World> key)
	{
		return key.getLocation().getNamespace().equals(Hyperbox.MODID);
	}
}
