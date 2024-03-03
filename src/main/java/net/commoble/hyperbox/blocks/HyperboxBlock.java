package net.commoble.hyperbox.blocks;

import java.util.Optional;

import javax.annotation.Nullable;

import net.commoble.hyperbox.Hyperbox;
import net.commoble.hyperbox.RotationHelper;
import net.commoble.hyperbox.dimension.HyperboxChunkGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class HyperboxBlock extends Block implements EntityBlock
{	
	public static final DirectionProperty ATTACHMENT_DIRECTION = BlockStateProperties.FACING;
	public static final IntegerProperty ROTATION = IntegerProperty.create("rotation", 0,3);

	public HyperboxBlock(Properties properties)
	{
		super(properties);
		// this default state results in the "north" face of the model pointing north
		this.registerDefaultState(this.stateDefinition.any()
			.setValue(ATTACHMENT_DIRECTION, Direction.DOWN)
			.setValue(ROTATION, 0));
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> builder)
	{
		super.createBlockStateDefinition(builder);
		builder.add(ATTACHMENT_DIRECTION, ROTATION);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
	{
		return Hyperbox.INSTANCE.hyperboxBlockEntityType.get().create(pos, state);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context)
	{
		BlockState defaultState = this.defaultBlockState();
		BlockPos placePos = context.getClickedPos();
		Direction faceOfAdjacentBlock = context.getClickedFace();
		Direction directionTowardAdjacentBlock = faceOfAdjacentBlock.getOpposite();
		Vec3 relativeHitVec = context.getClickLocation().subtract(Vec3.atLowerCornerOf(placePos));
		return getStateForPlacement(defaultState, placePos, directionTowardAdjacentBlock, relativeHitVec);
	}
	
	public static BlockState getStateForPlacement(BlockState defaultState, BlockPos placePos, Direction directionTowardAdjacentBlock, Vec3 relativeHitVec)
	{
		Direction outputDirection = RotationHelper.getOutputDirectionFromRelativeHitVec(relativeHitVec, directionTowardAdjacentBlock);
		int rotationIndex = RotationHelper.getRotationIndexForDirection(directionTowardAdjacentBlock, outputDirection);
		
		if (defaultState.hasProperty(ATTACHMENT_DIRECTION) && defaultState.hasProperty(ROTATION))
		{
			return defaultState.setValue(ATTACHMENT_DIRECTION, directionTowardAdjacentBlock).setValue(ROTATION, rotationIndex);
		}
		else
		{
			return defaultState;
		}
	}

	@Override
	@Deprecated
	public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)
	{
		if (player instanceof ServerPlayer serverPlayer
			&& level.getBlockEntity(pos) instanceof HyperboxBlockEntity hyperbox)
		{
			hyperbox.teleportPlayerOrOpenMenu(serverPlayer, hit.getDirection());
		}
		
		return InteractionResult.SUCCESS;
	}

	// called by BlockItems after the block is placed into the world
	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack)
	{
		// if item was named via anvil+nametag, convert that to a TE name
		if (level.getBlockEntity(pos) instanceof HyperboxBlockEntity hyperbox)
		{
			Item item = stack.getItem();
			// setting the color on the client world ensures that the te
			// gets its color set properly after we place it on the client
			// so that it renders correctly
			if (item instanceof HyperboxBlockItem hyperboxItem)
			{
				hyperbox.setColor(hyperboxItem.getColor(stack));
			}
			if (!level.isClientSide)
			{
				if (stack.hasCustomHoverName())
				{
					hyperbox.setName(stack.getHoverName());
				}
				if (hyperbox.getLevelKey().isPresent())
				{
					hyperbox.updateDimensionAfterPlacingBlock();
				}
			}
		}
	}

	@Override
	@Deprecated
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving)
	{
		// the supercall removes the tile entity
		super.onRemove(state, level, pos, newState, isMoving);
		notifyNeighborsOfStrongSignalChange(state, level, pos);
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
		return HyperboxChunkGenerator.CENTER.relative(originalFace, 6);
	}

	@Override
	@Deprecated
	public boolean isSignalSource(BlockState state)
	{
		return true;
	}

	@Override
	@Deprecated
	public int getSignal(BlockState blockState, BlockGetter level, BlockPos pos, Direction sideOfAdjacentBlock)
	{
		Direction originalFace = this.getOriginalFace(blockState, sideOfAdjacentBlock.getOpposite());
		return level.getBlockEntity(pos) instanceof HyperboxBlockEntity hyperbox
			? hyperbox.getPower(false, originalFace)
			: 0;
	}

	@Override
	@Deprecated
	public int getDirectSignal(BlockState blockState, BlockGetter level, BlockPos pos, Direction sideOfAdjacentBlock)
	{
		Direction originalFace = this.getOriginalFace(blockState, sideOfAdjacentBlock.getOpposite());
		return level.getBlockEntity(pos) instanceof HyperboxBlockEntity hyperbox
			? hyperbox.getPower(true, originalFace)
			: 0;
	}
	
	// called after an adjacent blockstate changes	
	@Override
	@Deprecated
	public void neighborChanged(BlockState thisState, Level level, BlockPos thisPos, Block fromBlock, BlockPos fromPos, boolean isMoving)
	{
		this.onNeighborUpdated(thisState, level, thisPos, level.getBlockState(fromPos), fromPos);
		super.neighborChanged(thisState, level, thisPos, fromBlock, fromPos, isMoving);
	}

	// called when a neighboring te's data changes
	@Override
	public void onNeighborChange(BlockState thisState, LevelReader level, BlockPos thisPos, BlockPos neighborPos)
	{
		this.onNeighborUpdated(thisState, level, thisPos, level.getBlockState(neighborPos), neighborPos);
		// does nothing by default
		super.onNeighborChange(thisState, level, thisPos, neighborPos);
	}
	
	protected void onNeighborUpdated(BlockState thisState, BlockGetter level, BlockPos thisPos, BlockState neighborState, BlockPos neighborPos)
	{
		if (level instanceof ServerLevel serverLevel)
		{
			BlockPos offsetToNeighbor = neighborPos.subtract(thisPos);
			@Nullable Direction directionToNeighbor = Direction.fromDelta(offsetToNeighbor.getX(), offsetToNeighbor.getY(), offsetToNeighbor.getZ());
			if (directionToNeighbor != null)
			{
				this.getApertureTileEntityForFace(thisState, serverLevel,thisPos,directionToNeighbor).ifPresent(te -> {
					te.updatePower(serverLevel, neighborPos, neighborState, directionToNeighbor);
					te.setChanged(); // invokes onNeighborChanged on adjacent blocks, so we can propagate neighbor changes, update capabilities, etc
				});
			}
		}
		
	}
	
	public static void notifyNeighborsOfStrongSignalChange(BlockState state, Level world, BlockPos pos)
	{
		// propagate a neighbor update such that blocks two spaces away from this block get notified of neighbor changes
		// this ensures that blocks that were receiving a strong signal conducted through a solid block are
		// correctly updated
		for (Direction direction : Direction.values())
		{
			world.updateNeighborsAt(pos.relative(direction), state.getBlock());
		}
	}
	
	public Optional<ApertureBlockEntity> getApertureTileEntityForFace(BlockState thisState, ServerLevel world, BlockPos thisPos, Direction directionToNeighbor)
	{
		Direction originalFace = this.getOriginalFace(thisState, directionToNeighbor);
		return world.getBlockEntity(thisPos) instanceof HyperboxBlockEntity hyperbox
			? hyperbox.getAperture(world.getServer(), originalFace)
			: Optional.empty();
	}
	
	public Direction getOriginalFace(BlockState thisState, Direction worldSpaceFace)
	{
		// okay, we have these inputs:
		// -- the side absolute directional side of the hyperbox that was activated
		// -- a blockstate that represents one of 24 orientations
		// we need to get which original/unrotated side of the hyperbox was activated
		// we can use the ATTACHMENT_DIRECTION to determine if we activated the "down" or "up" face
		// if not, then we need to use ROTATION to determine whether we activated the north/east/south/west face
		Direction downRotated = thisState.getValue(ATTACHMENT_DIRECTION);
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
			int rotationIndex = thisState.getValue(ROTATION);
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
		Direction currentDown = thisState.getValue(ATTACHMENT_DIRECTION);
		int rotation = thisState.getValue(ROTATION);
		return originalFace == Direction.DOWN
			? currentDown
			: originalFace == Direction.UP
				? currentDown.getOpposite()
				: RotationHelper.getInputDirection(currentDown, rotation, RotationHelper.getRotationIndexForHorizontal(originalFace));
	}

	public static boolean getIsNormalCube(BlockState state, BlockGetter world, BlockPos pos)
	{
		return false;
	}
	
	/**
	 * Returns the blockstate with the given rotation from the passed blockstate. If
	 * inapplicable, returns the passed blockstate.
	 * 
	 * @deprecated call via {@link BlockState#withRotation(Rotation)} whenever
	 *             possible. Implementing/overriding is fine.
	 */
	@Override
	@Deprecated
	public BlockState rotate(BlockState state, Rotation rotation)
	{
		if (state.hasProperty(ATTACHMENT_DIRECTION) && state.hasProperty(ROTATION))
		{
			Direction attachmentDirection = state.getValue(ATTACHMENT_DIRECTION);
			int rotationIndex = state.getValue(ROTATION);

			Direction newAttachmentDirection = rotation.rotate(attachmentDirection);
			int newRotationIndex = RotationHelper.getRotatedRotation(attachmentDirection, rotationIndex, rotation);

			return state.setValue(ATTACHMENT_DIRECTION, newAttachmentDirection).setValue(ROTATION, newRotationIndex);
		}
		else
		{
			return state;
		}
	}

	/**
	 * Returns the blockstate with the given mirror of the passed blockstate. If
	 * inapplicable, returns the passed blockstate.
	 * 
	 * @deprecated call via {@link BlockState#withMirror(Mirror)} whenever
	 *             possible. Implementing/overriding is fine.
	 */
	@Override
	@Deprecated
	public BlockState mirror(BlockState state, Mirror mirror)
	{
		if (state.hasProperty(ATTACHMENT_DIRECTION) && state.hasProperty(ROTATION))
		{
			Direction attachmentDirection = state.getValue(ATTACHMENT_DIRECTION);
			int rotationIndex = state.getValue(ROTATION);

			Direction newAttachmentDirection = mirror.mirror(attachmentDirection);
			int newRotationIndex = RotationHelper.getMirroredRotation(attachmentDirection, rotationIndex, mirror);

			return state.setValue(ATTACHMENT_DIRECTION, newAttachmentDirection).setValue(ROTATION, newRotationIndex);
		}
		else
		{
			return state;
		}
	}
}
