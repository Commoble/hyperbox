package commoble.hyperbox;

import commoble.hyperbox.blocks.ApertureBlock;
import commoble.hyperbox.blocks.ApertureTileEntity;
import commoble.hyperbox.blocks.HyperboxBlock;
import commoble.hyperbox.blocks.HyperboxTileEntity;
import commoble.hyperbox.client.ClientProxy;
import commoble.hyperbox.dimension.DelayedTeleportWorldData;
import commoble.hyperbox.dimension.HyperboxChunkGenerator;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.Dimension;
import net.minecraft.world.DimensionType;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.TickEvent.WorldTickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig.Type;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.IForgeRegistryEntry;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(Hyperbox.MODID)
public class Hyperbox
{
	public static final String MODID = "hyperbox";
	public static Hyperbox INSTANCE;
	
	public static final ResourceLocation HYPERBOX_ID = new ResourceLocation(MODID, Names.HYPERBOX);
	// keys for the hyperbox dimension stuff
	public static final RegistryKey<Biome> BIOME_KEY = RegistryKey.getOrCreateKey(Registry.BIOME_KEY, HYPERBOX_ID);
	public static final RegistryKey<World> WORLD_KEY = RegistryKey.getOrCreateKey(Registry.WORLD_KEY, HYPERBOX_ID);
	public static final RegistryKey<Dimension> DIMENSION_KEY = RegistryKey.getOrCreateKey(Registry.DIMENSION_KEY, HYPERBOX_ID);
	public static final RegistryKey<DimensionType> DIMENSION_TYPE_KEY = RegistryKey.getOrCreateKey(Registry.DIMENSION_TYPE_KEY, HYPERBOX_ID);
	
	public final ServerConfig serverConfig;
	public final RegistryObject<HyperboxBlock> hyperboxBlock;
	public final RegistryObject<ApertureBlock> apertureBlock;
	public final RegistryObject<TileEntityType<HyperboxTileEntity>> hyperboxTileEntityType;
	public final RegistryObject<TileEntityType<ApertureTileEntity>> apertureTileEntityType;
	
	public Hyperbox()
	{
		INSTANCE = this;
		
		IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
		IEventBus forgeBus = MinecraftForge.EVENT_BUS;
		
		this.serverConfig = ConfigHelper.register(Type.SERVER, ServerConfig::new);
		
		// create and set up registrars
		DeferredRegister<Block> blocks = makeRegister(modBus, ForgeRegistries.BLOCKS);
		DeferredRegister<Item> items = makeRegister(modBus, ForgeRegistries.ITEMS);
		DeferredRegister<TileEntityType<?>> tileEntities = makeRegister(modBus, ForgeRegistries.TILE_ENTITIES);
		
		this.hyperboxBlock = blocks.register(Names.HYPERBOX, () -> new HyperboxBlock(AbstractBlock.Properties.from(Blocks.PURPUR_BLOCK).setOpaque(HyperboxBlock::getIsNormalCube)));
		items.register(Names.HYPERBOX, () -> new BlockItem(this.hyperboxBlock.get(), new Item.Properties().group(ItemGroup.DECORATIONS)));
		this.hyperboxTileEntityType = tileEntities.register(Names.HYPERBOX, () -> TileEntityType.Builder.create(HyperboxTileEntity::new, this.hyperboxBlock.get()).build(null));
		
		this.apertureBlock = blocks.register(Names.APERTURE, () -> new ApertureBlock(AbstractBlock.Properties.from(Blocks.BARRIER).setLightLevel(state -> 6).setOpaque(HyperboxBlock::getIsNormalCube)));
		this.apertureTileEntityType = tileEntities.register(Names.APERTURE, () -> TileEntityType.Builder.create(ApertureTileEntity::new, this.apertureBlock.get()).build(null));
		
		// subscribe event handlers
		modBus.addListener(this::onCommonSetup);
		forgeBus.addListener(this::onWorldTick);
		
		// subscribe client-build event handlers
		if (FMLEnvironment.dist == Dist.CLIENT)
		{
			ClientProxy.doClientModInit(modBus, forgeBus);
		}
	}
	
	void onCommonSetup(FMLCommonSetupEvent event)
	{
		event.enqueueWork(this::afterCommonSetup);
	}
	
	// run on the main thread after the parallel stuff
	void afterCommonSetup()
	{
		// register things to vanilla Registries for which forge registries don't exist
		registerVanilla(Registry.CHUNK_GENERATOR_CODEC, Names.HYPERBOX, HyperboxChunkGenerator.CODEC);
	}
	
	void onWorldTick(WorldTickEvent event)
	{
		World world = event.world;
		if (event.phase == TickEvent.Phase.END && world instanceof ServerWorld)
		{
			ServerWorld serverWorld = (ServerWorld)world;
			DelayedTeleportWorldData.tick(serverWorld);
		}
	}
	
	// helper methods for registering things
	
	static <T> T registerVanilla(Registry<T> registry, String name, T thing)
	{
		return Registry.register(registry, new ResourceLocation(MODID, name), thing);
	}
	
	// create and subscribe a forge DeferredRegister
	static <T extends IForgeRegistryEntry<T>> DeferredRegister<T> makeRegister(IEventBus modBus, IForgeRegistry<T> registry)
	{
		DeferredRegister<T> register = DeferredRegister.create(registry, MODID);
		register.register(modBus);
		return register;
	}
}
