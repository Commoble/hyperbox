package net.commoble.hyperbox;

import java.util.function.Supplier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.collect.Lists;
import com.mojang.serialization.MapCodec;

import net.commoble.hyperbox.blocks.ApertureBlock;
import net.commoble.hyperbox.blocks.ApertureBlockEntity;
import net.commoble.hyperbox.blocks.C2SSaveHyperboxPacket;
import net.commoble.hyperbox.blocks.HyperboxBlock;
import net.commoble.hyperbox.blocks.HyperboxBlockEntity;
import net.commoble.hyperbox.blocks.HyperboxMenu;
import net.commoble.hyperbox.client.ClientProxy;
import net.commoble.hyperbox.dimension.DelayedTeleportData;
import net.commoble.hyperbox.dimension.HyperboxChunkGenerator;
import net.commoble.hyperbox.dimension.HyperboxDimension;
import net.commoble.hyperbox.dimension.HyperboxSaveData;
import net.commoble.hyperbox.dimension.ReturnPoint;
import net.commoble.hyperbox.dimension.TeleportHelper;
import net.commoble.infiniverse.api.InfiniverseAPI;
import net.commoble.infiniverse.api.UnregisterDimensionEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(Hyperbox.MODID)
public class Hyperbox
{
	public static final String MODID = "hyperbox";
	public static Hyperbox INSTANCE;
	
	public static final ResourceLocation HYPERBOX_ID = id(Names.HYPERBOX);
	// keys for the hyperbox dimension stuff
	public static final ResourceKey<Biome> BIOME_KEY = ResourceKey.create(Registries.BIOME, HYPERBOX_ID);
	public static final ResourceKey<Level> WORLD_KEY = ResourceKey.create(Registries.DIMENSION, HYPERBOX_ID);
	public static final ResourceKey<LevelStem> DIMENSION_KEY = ResourceKey.create(Registries.LEVEL_STEM, HYPERBOX_ID);
	public static final ResourceKey<DimensionType> DIMENSION_TYPE_KEY = ResourceKey.create(Registries.DIMENSION_TYPE, HYPERBOX_ID);

	public static final int DEFAULT_COLOR = 0x4a354a;
	
	public final CommonConfig commonConfig;
	public final Supplier<HyperboxBlock> hyperboxBlock;
	// the placement preview renderer gets the color handler from the player's currently held item instead of the blockstate
	public final Supplier<HyperboxBlock> hyperboxPreviewBlock;
	public final Supplier<ApertureBlock> apertureBlock;
	public final Supplier<Block> hyperboxWall;
	public final Supplier<BlockItem> hyperboxItem;
	public final Supplier<BlockEntityType<HyperboxBlockEntity>> hyperboxBlockEntityType;
	public final Supplier<BlockEntityType<ApertureBlockEntity>> apertureBlockEntityType;
	public final Supplier<MenuType<HyperboxMenu>> hyperboxMenuType;
	public final Supplier<MapCodec<HyperboxChunkGenerator>> hyperboxChunkGeneratorCodec;
	public final Supplier<AttachmentType<ReturnPoint>> returnPointAttachment;
	public final Supplier<DataComponentType<ResourceKey<Level>>> worldKeyDataComponent;
	
	public Hyperbox(IEventBus modBus)
	{
		INSTANCE = this;
		
		IEventBus forgeBus = NeoForge.EVENT_BUS;
		
		this.commonConfig = ConfigHelper.register(MODID, ModConfig.Type.COMMON, CommonConfig::new);
		
		// create and set up registrars
		DeferredRegister<SoundEvent> soundEvents = defreg(modBus, Registries.SOUND_EVENT);
		DeferredRegister<Block> blocks = defreg(modBus, Registries.BLOCK);
		DeferredRegister<Item> items = defreg(modBus, Registries.ITEM);
		DeferredRegister<BlockEntityType<?>> tileEntities = defreg(modBus, Registries.BLOCK_ENTITY_TYPE);
		DeferredRegister<MenuType<?>> menuTypes = defreg(modBus, Registries.MENU);
		DeferredRegister<MapCodec<? extends ChunkGenerator>> chunkGeneratorCodecs = defreg(modBus, Registries.CHUNK_GENERATOR);
		DeferredRegister<AttachmentType<?>> attachmentTypes = defreg(modBus, NeoForgeRegistries.Keys.ATTACHMENT_TYPES);
		DeferredRegister<DataComponentType<?>> dataComponentTypes = defreg(modBus, Registries.DATA_COMPONENT_TYPE);
		
		soundEvents.register("ambience", () -> SoundEvent.createVariableRangeEvent(id("ambience")));
		
		this.hyperboxBlock = blocks.register(Names.HYPERBOX, () -> new HyperboxBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.PURPUR_BLOCK).strength(2F, 1200F).isRedstoneConductor(HyperboxBlock::getIsNormalCube)));
		this.hyperboxPreviewBlock = blocks.register(Names.HYPERBOX_PREVIEW, () -> new HyperboxBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.PURPUR_BLOCK).strength(2F, 1200F).isRedstoneConductor(HyperboxBlock::getIsNormalCube)));
		this.hyperboxItem = items.register(Names.HYPERBOX, () -> new BlockItem(this.hyperboxBlock.get(), new Item.Properties()));
		this.hyperboxBlockEntityType = tileEntities.register(Names.HYPERBOX, () -> BlockEntityType.Builder.of(HyperboxBlockEntity::create, this.hyperboxBlock.get()).build(null));
		
		this.apertureBlock = blocks.register(Names.APERTURE, () -> new ApertureBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.BARRIER).mapColor(MapColor.NONE).lightLevel(state -> 6).isRedstoneConductor(HyperboxBlock::getIsNormalCube)));
		this.apertureBlockEntityType = tileEntities.register(Names.APERTURE, () -> BlockEntityType.Builder.of(ApertureBlockEntity::create, this.apertureBlock.get()).build(null));
		
		this.hyperboxWall = blocks.register(Names.HYPERBOX_WALL, () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.BARRIER).mapColor(MapColor.NONE)));
		
		this.hyperboxMenuType = menuTypes.register(Names.HYPERBOX, () -> new MenuType<HyperboxMenu>(HyperboxMenu::makeClientMenu, FeatureFlags.VANILLA_SET));
		
		this.hyperboxChunkGeneratorCodec = chunkGeneratorCodecs.register(Names.HYPERBOX, HyperboxChunkGenerator::makeCodec);
		
		this.returnPointAttachment = attachmentTypes.register(Names.RETURN_POINT, () -> AttachmentType.builder(() -> ReturnPoint.EMPTY)
			.serialize(ReturnPoint.CODEC, rp -> !rp.data().isEmpty())
			.copyOnDeath()
			.build());
		
		this.worldKeyDataComponent = dataComponentTypes.register("world_key", () -> DataComponentType.<ResourceKey<Level>>builder()
			.persistent(ResourceKey.codec(Registries.DIMENSION))
			.networkSynchronized(ResourceKey.streamCodec(Registries.DIMENSION))
			.build());
		
		// subscribe event handlers
		modBus.addListener(EventPriority.LOW, this::registerDelegateCapabilities);
		modBus.addListener(this::onRegisterPayloads);
		modBus.addListener(this::onBuildTabContents);
		forgeBus.addListener(this::onUnregisterDimension);
		forgeBus.addListener(EventPriority.HIGH, this::onPreLevelTick);
		forgeBus.addListener(EventPriority.HIGH, this::onPostLevelTick);
		
		// subscribe client-build event handlers
		if (FMLEnvironment.dist.isClient())
		{
			ClientProxy.doClientModInit(modBus, forgeBus);
		}
	}
	
	private void registerDelegateCapabilities(RegisterCapabilitiesEvent event)
	{
		for (var blockCapability : BlockCapability.getAll())
		{
			genericallyRegisterBlockCap(event, blockCapability);
		}
	}
	
	@SuppressWarnings("unchecked")
	private <T,C> void genericallyRegisterBlockCap(RegisterCapabilitiesEvent event, BlockCapability<T,C> blockCap)
	{
		event.registerBlockEntity(blockCap, hyperboxBlockEntityType.get(), (be, context) -> context instanceof Direction direction
			? be.getCapability((BlockCapability<T,Direction>)blockCap, direction)
			: null);
		event.registerBlockEntity(blockCap, apertureBlockEntityType.get(), (be, context) -> context instanceof Direction direction
			? be.getCapability((BlockCapability<T,Direction>)blockCap, direction)
			: null);
	}
	
	private void onRegisterPayloads(RegisterPayloadHandlersEvent event)
	{
		event.registrar(MODID)
			.playToServer(C2SSaveHyperboxPacket.TYPE, C2SSaveHyperboxPacket.STREAM_CODEC, C2SSaveHyperboxPacket::handle);
	}
	
	private void onBuildTabContents(BuildCreativeModeTabContentsEvent event)
	{
		if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS || event.getTabKey() == CreativeModeTabs.REDSTONE_BLOCKS)
		{
			event.accept(this.hyperboxItem.get());			
		}
	}
	
	private void onUnregisterDimension(UnregisterDimensionEvent event)
	{
		ServerLevel level = event.getLevel();
		MinecraftServer server = level.getServer();
		DimensionType hyperboxDimensionType = HyperboxDimension.getDimensionType(server);
		// if this is a hyperbox dimension
		if (level.dimensionType() == hyperboxDimensionType)
		{
			// send players to their return points (we have specific points we want to return players to)
			// iterate over a copy of the player list as we're modifying the original
			for (ServerPlayer player : Lists.newArrayList(level.players()))
			{
				TeleportHelper.ejectPlayerFromDeadWorld(player);
			}
		}
	}
	
	private void onPreLevelTick(LevelTickEvent.Pre event)
	{
		if (!(event.getLevel() instanceof ServerLevel serverLevel))
		{
			return;
		}
		
		if (this.commonConfig.autoForceHyperboxChunks.get() && HyperboxDimension.getDimensionType(serverLevel.getServer()) == serverLevel.dimensionType())
		{
			boolean isChunkForced = serverLevel.getForcedChunks().contains(HyperboxChunkGenerator.CHUNKID);
			boolean shouldChunkBeForced = shouldHyperboxChunkBeForced(serverLevel);
			if (isChunkForced != shouldChunkBeForced)
			{
				serverLevel.setChunkForced(HyperboxChunkGenerator.CHUNKPOS.x, HyperboxChunkGenerator.CHUNKPOS.z, shouldChunkBeForced);
			}
		}
	}
	
	private void onPostLevelTick(LevelTickEvent.Post event)
	{		
		if (!(event.getLevel() instanceof ServerLevel serverLevel))
		{
			return;
		}
		MinecraftServer server = serverLevel.getServer();
		
		// cleanup unused hyperboxes
		if (shouldUnloadDimension(server, serverLevel))
		{
			ResourceKey<Level> key = serverLevel.dimension();
			InfiniverseAPI.get().markDimensionForUnregistration(server, key);
		}
		
		// handle scheduled teleports
		DelayedTeleportData.tick(serverLevel);
	}
	
	public static boolean shouldUnloadDimension(MinecraftServer server, @Nonnull ServerLevel targetLevel)
	{
		// only unload hyperbox dimensions
		DimensionType hyperboxDimensionType = HyperboxDimension.getDimensionType(server);
		if (hyperboxDimensionType != targetLevel.dimensionType())
			return false;
		
		// only run this once a second as the getBlockEntity call flags the parent chunk to be kept loaded another tick
		// which prevents the hyperbox-chunk-unloader from running
		if ((targetLevel.getGameTime() + targetLevel.hashCode()) % 20 != 0)
			return false;
		
		HyperboxSaveData hyperboxData = HyperboxSaveData.getOrCreate(targetLevel);
		ResourceKey<Level> parentKey = hyperboxData.getParentWorld();
		BlockPos parentPos = hyperboxData.getParentPos();
		
		// if we can't find the parent world, unload the hyperbox dimension
		@Nullable ServerLevel parentLevel = server.getLevel(parentKey);
		if (parentLevel == null)
			return true;
		
		// don't load chunks in the tick event
		// if we can't check the chunk, we can't verify that the dimension should be unloaded
		if (!parentLevel.hasChunk(parentPos.getX()>>4, parentPos.getZ()>>4))
			return false;
		
		// if the te doesn't exist or isn't a hyperbox, return true and unload
		BlockEntity te = parentLevel.getBlockEntity(parentPos);
		if (!(te instanceof HyperboxBlockEntity hyperbox))
			return true;
		
		ResourceKey<Level> key = targetLevel.dimension();
		
		return hyperbox.getLevelKey()
			// if the te points to our dimension, return false and don't unload
			.map(childKey -> !childKey.equals(key))
			// if the te doesn't point anywhere, return true and unload
			.orElse(true);
	}

	@SuppressWarnings("deprecation")
	private static boolean shouldHyperboxChunkBeForced(ServerLevel hyperboxLevel)
	{
		MinecraftServer server = hyperboxLevel.getServer();
		HyperboxSaveData data = HyperboxSaveData.getOrCreate(hyperboxLevel);
		ResourceKey<Level> parentKey = data.getParentWorld();
		ServerLevel parentLevel = server.getLevel(parentKey);
		if (parentLevel == null)
			return false;
		
		BlockPos parentPos = data.getParentPos();	
		return parentLevel.hasChunkAt(parentPos);
	}
	
	// create and subscribe a forge DeferredRegister
	private static <T> DeferredRegister<T> defreg(IEventBus modBus, ResourceKey<Registry<T>> registry)
	{
		DeferredRegister<T> register = DeferredRegister.create(registry, MODID);
		register.register(modBus);
		return register;
	}
	
	public static ResourceLocation id(String path)
	{
		return ResourceLocation.fromNamespaceAndPath(MODID, path);
	}
}
