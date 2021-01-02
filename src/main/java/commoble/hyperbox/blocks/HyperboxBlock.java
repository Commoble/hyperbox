package commoble.hyperbox.blocks;

import java.util.Optional;

import javax.annotation.Nullable;

import commoble.hyperbox.DirectionHelper;
import commoble.hyperbox.Hyperbox;
import commoble.hyperbox.RotationHelper;
import commoble.hyperbox.SpawnPointHelper;
import commoble.hyperbox.dimension.DelayedTeleportWorldData;
import commoble.hyperbox.dimension.HyperboxChunkGenerator;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.state.DirectionProperty;
import net.minecraft.state.IntegerProperty;
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
	public static final DirectionProperty ATTACHMENT_DIRECTION = BlockStateProperties.FACING;
	public static final IntegerProperty ROTATION = IntegerProperty.create("rotation", 0,3);

	public HyperboxBlock(Properties properties)
	{
		super(properties);
		// this default state results in the "north" face of the model pointing north
		this.setDefaultState(this.stateContainer.getBaseState()
			.with(ATTACHMENT_DIRECTION, Direction.DOWN)
			.with(ROTATION, 0));
	}

	@Override
	protected void fillStateContainer(Builder<Block, BlockState> builder)
	{
		super.fillStateContainer(builder);
		builder.add(ATTACHMENT_DIRECTION, ROTATION);
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
	public BlockState getStateForPlacement(BlockItemUseContext context)
	{
		BlockState defaultState = this.getDefaultState();
		BlockPos placePos = context.getPos();
		Direction faceOfAdjacentBlock = context.getFace();
		Direction directionTowardAdjacentBlock = faceOfAdjacentBlock.getOpposite();
		Vector3d relativeHitVec = context.getHitVec().subtract(Vector3d.copy(placePos));
		return getStateForPlacement(defaultState, placePos, directionTowardAdjacentBlock, relativeHitVec);
	}
	
	public static BlockState getStateForPlacement(BlockState defaultState, BlockPos placePos, Direction directionTowardAdjacentBlock, Vector3d relativeHitVec)
	{
		Direction outputDirection = RotationHelper.getOutputDirectionFromRelativeHitVec(relativeHitVec, directionTowardAdjacentBlock);
		int rotationIndex = RotationHelper.getRotationIndexForDirection(directionTowardAdjacentBlock, outputDirection);
		
		if (defaultState.hasProperty(ATTACHMENT_DIRECTION) && defaultState.hasProperty(ROTATION))
		{
			return defaultState.with(ATTACHMENT_DIRECTION, directionTowardAdjacentBlock).with(ROTATION, rotationIndex);
		}
		else
		{
			return defaultState;
		}
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
					BlockPos posAdjacentToAperture = this.getPosAdjacentToAperture(state, hit.getFace());
					BlockPos spawnPoint = SpawnPointHelper.getBestSpawnPosition(
						targetWorld,
						posAdjacentToAperture,
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
		}
	}

	@Override
	@Deprecated
	public void onReplaced(BlockState state, World worldIn, BlockPos pos, BlockState newState, boolean isMoving)
	{
		// the supercall removes the tile entity
		super.onReplaced(state, worldIn, pos, newState, isMoving);
		notifyNeighborsOfStrongSignalChange(state, worldIn, pos);
	}

	// given the side of a hyperbox in absolute world space,
	// return the position interior-adjacent to the aperture
	// on the corresponding side of the inner dimension
	public BlockPos getPosAdjacentToAperture(BlockState state, Direction worldSpaceFace)
	{
		Direction originalFace = this.getOriginalFace(state, worldSpaceFace);
		// the hyperbox dimension chunk is a 15x15x15 space, with bedrock walls, a corner at 0,0,0, and the center at 7,7,7
		// we want to get the position of the block adjacent to the relevant aperture
		// if side is e.g. west (the west side of the parent block)
		// then the target position is the block one space to the east of the western aperture
		// or six spaces to the west of the center
		return HyperboxChunkGenerator.CENTER.offset(originalFace, 6);
	}

	@Override
	public boolean canProvidePower(BlockState state)
	{
		return true;
	}

	@Override
	public int getWeakPower(BlockState blockState, IBlockReader world, BlockPos pos, Direction sideOfAdjacentBlock)
	{
		Direction originalFace = this.getOriginalFace(blockState, sideOfAdjacentBlock.getOpposite());
		return HyperboxTileEntity.get(world, pos)
			.map(te -> te.getPower(false, originalFace))
			.orElse(0);
	}

	@Override
	public int getStrongPower(BlockState blockState, IBlockReader world, BlockPos pos, Direction sideOfAdjacentBlock)
	{
		Direction originalFace = this.getOriginalFace(blockState, sideOfAdjacentBlock.getOpposite());
		return HyperboxTileEntity.get(world, pos)
			.map(te -> te.getPower(true, originalFace))
			.orElse(0);
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
	
	public static void notifyNeighborsOfStrongSignalChange(BlockState state, World world, BlockPos pos)
	{
		// propagate a neighbor update such that blocks two spaces away from this block get notified of neighbor changes
		// this ensures that blocks that were receiving a strong signal conducted through a solid block are
		// correctly updated
		for (Direction direction : Direction.values())
		{
			world.notifyNeighborsOfStateChange(pos.offset(direction), state.getBlock());
		}
	}
	
	public Optional<ApertureTileEntity> getApertureTileEntityForFace(BlockState thisState, ServerWorld world, BlockPos thisPos, Direction directionToNeighbor)
	{
		Direction originalFace = this.getOriginalFace(thisState, directionToNeighbor);
		return HyperboxTileEntity.get(world, thisPos)
			.flatMap(te -> te.getAperture(world.getServer(), originalFace));
	}
	
	public Direction getOriginalFace(BlockState thisState, Direction worldSpaceFace)
	{
		// okay, we have these inputs:
		// -- the side absolute directional side of the hyperbox that was activated
		// -- a blockstate that represents one of 24 orientations
		// we need to get which original/unrotated side of the hyperbox was activated
		// we can use the ATTACHMENT_DIRECTION to determine if we activated the "down" or "up" face
		// if not, then we need to use ROTATION to determine whether we activated the north/east/south/west face
		Direction downRotated = thisState.get(ATTACHMENT_DIRECTION);
		if (downRotated == worldSpaceFace)
		{
			return Direction.DOWN;
		}
		else if (downRotated.getOpposite() == worldSpaceFace)
		{
			return Direction.UP;
		}
		else
		{
			int rotationIndex = thisState.get(ROTATION);
			// get the direction that the original "north" face is now pointing
			Direction newNorth = RotationHelper.getOutputDirection(downRotated, rotationIndex);
			if (newNorth == worldSpaceFace)
			{
				return Direction.NORTH;
			}
			else if (newNorth.getOpposite() == worldSpaceFace)
			{
				return Direction.SOUTH;
			}
			else
			{
				Direction newEast = RotationHelper.getInputDirection(downRotated, rotationIndex, 1);
				return newEast == worldSpaceFace ? Direction.EAST : Direction.WEST;
			}
		}
	}
	
	// return the hyperbox's current direction in worldspace of the given unrotated face
	public Direction getCurrentFacing(BlockState thisState, Direction originalFace)
	{
		Direction currentDown = thisState.get(ATTACHMENT_DIRECTION);
		int rotation = thisState.get(ROTATION);
		return originalFace == Direction.DOWN
			? currentDown
			: originalFace == Direction.UP
				? currentDown.getOpposite()
				: RotationHelper.getInputDirection(currentDown, rotation, RotationHelper.getRotationIndexForHorizontal(originalFace));
	}

	public static boolean getIsNormalCube(BlockState state, IBlockReader world, BlockPos pos)
	{
		return false;
	}
}
