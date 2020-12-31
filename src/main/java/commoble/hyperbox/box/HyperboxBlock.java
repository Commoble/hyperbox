package commoble.hyperbox.box;

import java.util.Optional;

import javax.annotation.Nullable;

import commoble.hyperbox.DirectionHelper;
import commoble.hyperbox.Hyperbox;
import commoble.hyperbox.SpawnPointHelper;
import commoble.hyperbox.aperture.ApertureTileEntity;
import commoble.hyperbox.dimension.DelayedTeleportWorldData;
import commoble.hyperbox.dimension.HyperboxChunkGenerator;
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
import net.minecraft.world.IWorldReader;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;

public class HyperboxBlock extends Block
{	
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
					Direction apertureSide = this.getApertureSideForHyperboxSide(state, hit.getFace());
					BlockPos spawnPoint = SpawnPointHelper.getBestSpawnPosition(
						targetWorld,
						this.getChildTargetPos(state, apertureSide),
						HyperboxChunkGenerator.MIN_SPAWN_CORNER,
						HyperboxChunkGenerator.MAX_SPAWN_CORNER);
//					DimensionHelper.sendPlayerToDimension(serverPlayer, targetWorld, Vector3d.copyCentered(spawnPoint));
					DelayedTeleportWorldData.get(serverPlayer.getServerWorld()).addPlayer(serverPlayer, targetWorld.getDimensionKey(), Vector3d.copyCentered(spawnPoint));
				});
		}
		
		return ActionResultType.SUCCESS;
	}

	// called by BlockItems after the block is placed into the world
	@Override
	public void onBlockPlacedBy(World worldIn, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack)
	{
		// if item was named via anvil+nametag, convert that to a TE name
		if (!worldIn.isRemote)
		{
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
			// simulate some neighbor updates so the inner apertures can update
			for (Direction dir : Direction.values())
			{
	//			BlockPos neighborPos = pos.offset(dir);
	//			BlockState neighborState = worldIn.getBlockState(neighborPos);
	//			this.onNeighborUpdated(state, worldIn, pos, neighborState, neighborPos);
			}
		}
	}
	
	public Direction getApertureSideForHyperboxSide(BlockState state, Direction side)
	{
		return side;
	}
	
	public BlockPos getChildTargetPos(BlockState state, Direction side)
	{
		// the hyperbox dimension chunk is a 15x15x15 space, with bedrock walls, a corner at 0,0,0, and the center at 7,7,7
		// we want to get the position of the block adjacent to the relevant aperture
		// if side is e.g. west (the west side of the parent block)
		// then the target position is the block one space to the east of the western aperture
		// or six spaces to the west of the center
		return HyperboxChunkGenerator.CENTER.offset(side, 6);
	}

	@Override
	public boolean canProvidePower(BlockState state)
	{
		return true;
	}

	@SuppressWarnings("deprecation")
	@Override
	public int getWeakPower(BlockState blockState, IBlockReader world, BlockPos pos, Direction sideOfAdjacentBlock)
	{
		return HyperboxTileEntity.get(world, pos)
			.map(te -> te.getPower(false, sideOfAdjacentBlock.getOpposite()))
			.orElseGet(() -> super.getWeakPower(blockState, world, pos, sideOfAdjacentBlock));
	}

	@SuppressWarnings("deprecation")
	@Override
	public int getStrongPower(BlockState blockState, IBlockReader world, BlockPos pos, Direction sideOfAdjacentBlock)
	{
		return HyperboxTileEntity.get(world, pos)
			.map(te -> te.getPower(true, sideOfAdjacentBlock.getOpposite()))
			.orElseGet(() -> super.getStrongPower(blockState, world, pos, sideOfAdjacentBlock));
	}
	
	// called after an adjacent blockstate changes	
	@Override
	@Deprecated
	public void neighborChanged(BlockState thisState, World world, BlockPos thisPos, Block fromBlock, BlockPos fromPos, boolean isMoving)
	{
		this.onNeighborUpdated(thisState, world, thisPos, world.getBlockState(fromPos), fromPos);
		super.neighborChanged(thisState, world, thisPos, fromBlock, fromPos, isMoving);
	}

	// called when a neighboring te's data changes
	@Override
	public void onNeighborChange(BlockState thisState, IWorldReader world, BlockPos thisPos, BlockPos neighborPos)
	{
		this.onNeighborUpdated(thisState, world, thisPos, world.getBlockState(neighborPos), neighborPos);
		// does nothing by default
		super.onNeighborChange(thisState, world, thisPos, neighborPos);
	}
	
	protected void onNeighborUpdated(BlockState thisState, IBlockReader world, BlockPos thisPos, BlockState neighborState, BlockPos neighborPos)
	{
		if (world instanceof ServerWorld)
		{
			@Nullable Direction directionToNeighbor = DirectionHelper.getDirectionToNeighborPos(thisPos, neighborPos);
			if (directionToNeighbor != null)
			{
				ServerWorld serverWorld = (ServerWorld)world;
				this.getApertureTileEntityForFace(thisState, serverWorld,thisPos,directionToNeighbor).ifPresent(te -> {
					te.updatePower(serverWorld, neighborPos, neighborState, directionToNeighbor);
				});
			}
		}
		
	}
	
	public Optional<ApertureTileEntity> getApertureTileEntityForFace(BlockState thisState, ServerWorld world, BlockPos thisPos, Direction directionToNeighbor)
	{
		return HyperboxTileEntity.get(world, thisPos)
			.flatMap(te -> te.getAperture(world.getServer(), directionToNeighbor));
	}

	public static boolean getIsNormalCube(BlockState state, IBlockReader world, BlockPos pos)
	{
		return false;
	}
}
