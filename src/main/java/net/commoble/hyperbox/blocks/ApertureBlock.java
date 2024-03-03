package net.commoble.hyperbox.blocks;

import java.util.Optional;

import net.commoble.hyperbox.Hyperbox;
import net.commoble.hyperbox.dimension.DelayedTeleportData;
import net.commoble.hyperbox.dimension.HyperboxSaveData;
import net.commoble.hyperbox.dimension.SpawnPointHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class ApertureBlock extends Block implements EntityBlock
{
	// direction of facing of aperture
	public static final DirectionProperty FACING = BlockStateProperties.FACING;

	public ApertureBlock(Properties properties)
	{
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> builder)
	{
		super.createBlockStateDefinition(builder);
		builder.add(FACING);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
	{
		return Hyperbox.INSTANCE.apertureBlockEntityType.get().create(pos, state);
	}
	
	@Override
	@Deprecated
	public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)
	{
		if (player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel)
		{
			MinecraftServer server = serverPlayer.server;
			
			HyperboxSaveData data = HyperboxSaveData.getOrCreate(serverLevel);
			ResourceKey<Level> parentKey = data.getParentWorld();
			BlockPos parentPos = data.getParentPos();
			BlockPos targetPos = parentPos;
			ServerLevel destinationLevel = server.getLevel(parentKey);
			if (destinationLevel == null)
			{
				destinationLevel = server.getLevel(Level.OVERWORLD);
			}
			Direction apertureFacing = state.getValue(FACING);
			BlockState parentState = destinationLevel.getBlockState(parentPos);
			Block parentBlock = parentState.getBlock();
			if (parentBlock instanceof HyperboxBlock hyperboxBlock)
			{
				Direction hyperboxFacing = hyperboxBlock.getCurrentFacing(parentState, apertureFacing.getOpposite()); 
				targetPos = parentPos.relative(hyperboxFacing);
				if (destinationLevel.getBlockState(targetPos).getDestroySpeed(destinationLevel, targetPos) < 0)
				{	// if this face of the exit block faces bedrock, make the initial spawn search target be the exit block instead of the adjacent position
					// (we do this so the spawn finder doesn't skip through the bedrock)
					targetPos = parentPos;
				}
				targetPos = SpawnPointHelper.getBestSpawnPosition(destinationLevel, targetPos, targetPos.offset(-3,-3,-3), targetPos.offset(3,3,3));
			}
			// else if parent pos is no longer a hyperblock for whatever reason, just teleport them to parentpos
			
			
			DelayedTeleportData.getOrCreate(serverPlayer.serverLevel()).schedulePlayerTeleport(serverPlayer, destinationLevel.dimension(), Vec3.atCenterOf(targetPos));
		}
		return InteractionResult.SUCCESS;
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
			// get power from neighbor
			Direction directionToNeighbor = thisState.getValue(FACING);
			int weakPower = neighborState.getSignal(level, neighborPos, directionToNeighbor);
			int strongPower = neighborState.getDirectSignal(level, neighborPos, directionToNeighbor);
			getLinkedHyperbox(serverLevel,thisPos).ifPresent(hyperbox -> {
				hyperbox.updatePower(weakPower, strongPower, directionToNeighbor.getOpposite());
				hyperbox.setChanged(); // invokes onNeighborChanged on adjacent blocks, so we can propagate neighbor changes, update capabilities, etc
			});
		}
		
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
		return level.getBlockEntity(pos) instanceof ApertureBlockEntity aperture
			? aperture.getPower(false)
			: 0;
	}

	@Override
	@Deprecated
	public int getDirectSignal(BlockState blockState, BlockGetter level, BlockPos pos, Direction sideOfAdjacentBlock)
	{
		return sideOfAdjacentBlock.getOpposite() == blockState.getValue(FACING)
			&& level.getBlockEntity(pos) instanceof ApertureBlockEntity aperture
				? aperture.getPower(true)
				: 0;
	}
	
	// get the hyperbox TE this aperture's world is linked to
	public static Optional<HyperboxBlockEntity> getLinkedHyperbox(ServerLevel level, BlockPos thisPos)
	{
		MinecraftServer server = level.getServer();
		HyperboxSaveData data = HyperboxSaveData.getOrCreate(level);
		BlockPos parentPos = data.getParentPos();
		ResourceKey<Level> parentLevelKey = data.getParentWorld();
		ServerLevel parentLevel = server.getLevel(parentLevelKey);
		BlockEntity blockEntity = parentLevel.getBlockEntity(parentPos);
		return blockEntity instanceof HyperboxBlockEntity hyperbox ? Optional.of(hyperbox) : Optional.empty();
	}
}
