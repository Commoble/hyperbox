package commoble.hyperbox.box;

import javax.annotation.Nullable;

import commoble.hyperbox.Hyperbox;
import commoble.hyperbox.SpawnPointHelper;
import commoble.hyperbox.dimension.DimensionHelper;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.state.DirectionProperty;
import net.minecraft.state.StateContainer.Builder;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;

public class HyperboxBlock extends Block
{
	public static final Vector3d DESTINATION_VECTOR = new Vector3d(7.5D, 2.5D, 7.5D);
	public static final BlockPos DESTINATION_POS = new BlockPos(7,2,7);
	public static final BlockPos MIN_SPAWN_CORNER = new BlockPos(1,1,1);
	public static final BlockPos MAX_SPAWN_CORNER = new BlockPos(13,12,13); // don't want to spawn with head in the bedrock ceiling
	public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

	public HyperboxBlock(Properties properties)
	{
		super(properties);
		this.setDefaultState(this.stateContainer.getBaseState()
			.with(FACING, Direction.NORTH));
	}

	@Override
	protected void fillStateContainer(Builder<Block, BlockState> builder)
	{
		super.fillStateContainer(builder);
		builder.add(FACING);
	}

	@Override
	public boolean hasTileEntity(BlockState state)
	{
		return true;
	}

	@Override
	public TileEntity createTileEntity(BlockState state, IBlockReader world)
	{
		return Hyperbox.INSTANCE.hyperboxTileEntityType.get().create();
	}

	@Override
	public ActionResultType onBlockActivated(BlockState state, World worldIn, BlockPos pos, PlayerEntity player, Hand handIn, BlockRayTraceResult hit)
	{
		if (player instanceof ServerPlayerEntity)
		{
			ServerPlayerEntity serverPlayer = (ServerPlayerEntity)player;
			MinecraftServer server = serverPlayer.server;
			
			HyperboxTileEntity.get(worldIn, pos)
				.ifPresent(te -> 
				{
					ServerWorld targetWorld = te.getOrCreateWorld(server);
					BlockPos spawnPoint = SpawnPointHelper.getBestSpawnPosition(targetWorld, DESTINATION_POS, MIN_SPAWN_CORNER, MAX_SPAWN_CORNER);
					DimensionHelper.sendPlayerToDimension(serverPlayer, targetWorld, Vector3d.copyCentered(spawnPoint));
				});
		}
		
		return ActionResultType.SUCCESS;
	}

	// called by BlockItems after the block is placed into the world
	@Override
	public void onBlockPlacedBy(World worldIn, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack)
	{
		// if item was named via anvil+nametag, convert that to a TE name
		HyperboxTileEntity.get(worldIn, pos)
			.ifPresent(te -> {
				if (stack.hasDisplayName())
				{
					te.setName(stack.getDisplayName());
				}
				if (!te.getWorldKey().isPresent())
				{
					te.setNewWorldKey();
				}
				te.afterBlockPlaced();
			});
	}

//	@Override
//	@Deprecated
//	public ItemStack getItem(IBlockReader worldIn, BlockPos pos, BlockState state)
//	{
//		return super.getItem(worldIn, pos, state);
////		ItemStack itemstack = super.getItem(worldIn, pos, state);
////		HyperboxTileEntity.get(worldIn, pos)
////			.ifPresent(te -> {
////				CompoundNBT nbt = te.writeExtraData(new CompoundNBT());
////				if (!nbt.isEmpty())
////				{
////					itemstack.setTagInfo("BlockEntityTag", nbt); // consistency with vanilla
////				}
////			});
////
////		return itemstack;
//	}
}
