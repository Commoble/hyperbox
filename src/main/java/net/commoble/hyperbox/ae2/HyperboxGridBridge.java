package net.commoble.hyperbox.ae2;

import appeng.api.AECapabilities;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.util.AECableType;
import net.commoble.hyperbox.blocks.ApertureBlock;
import net.commoble.hyperbox.blocks.ApertureBlockEntity;
import net.commoble.hyperbox.blocks.HyperboxBlock;
import net.commoble.hyperbox.blocks.HyperboxBlockEntity;
import net.commoble.hyperbox.dimension.HyperboxChunkGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import javax.annotation.Nullable;
import java.util.*;

/**
 * AE2 integration: each hyperbox face is a quantum gate.
 * <p>
 * external_cable ←(in-world)→ [FaceHost] ←(quantum link)→ [ApertureHost] ←(in-world)→ internal_cable
 * <p>
 * Both FaceHost and ApertureHost are proper InWorldGridNodes in their own
 * dimension. The quantum link is a directionless GridConnection (same as
 * AE2's Quantum Network Bridge). No cross-dimensional in-world connections.
 */
public class HyperboxGridBridge {

    private static final IdentityHashMap<HyperboxBlockEntity, FaceHost> FACE_HOSTS = new IdentityHashMap<>();
    private static final IdentityHashMap<ApertureBlockEntity, ApertureHost> APERTURE_HOSTS = new IdentityHashMap<>();

    /**
     * Register IN_WORLD_GRID_NODE_HOST on both block entity types.
     * Must be called BEFORE the generic capability loop so this registration
     * takes precedence — prevents the generic loop from leaking
     * cross-dimensional grid nodes.
     *
     * @return the capability that was registered (for the generic loop to skip)
     */
    public static BlockCapability<?, ?> register(RegisterCapabilitiesEvent event,
                                                  BlockEntityType<? extends HyperboxBlockEntity> hyperboxType,
                                                  BlockEntityType<? extends ApertureBlockEntity> apertureType) {
        event.registerBlockEntity(
            AECapabilities.IN_WORLD_GRID_NODE_HOST,
            hyperboxType,
            (be, context) -> getOrCreateFaceHost(be)
        );
        event.registerBlockEntity(
            AECapabilities.IN_WORLD_GRID_NODE_HOST,
            apertureType,
            (be, context) -> getOrCreateApertureHost(be)
        );
        return AECapabilities.IN_WORLD_GRID_NODE_HOST;
    }

    private static FaceHost getOrCreateFaceHost(HyperboxBlockEntity be) {
        FaceHost host = FACE_HOSTS.get(be);
        if (host != null && !be.isRemoved()) return host;
        if (host != null) host.destroy();
        host = new FaceHost(be);
        FACE_HOSTS.put(be, host);
        return host;
    }

    private static ApertureHost getOrCreateApertureHost(ApertureBlockEntity be) {
        ApertureHost host = APERTURE_HOSTS.get(be);
        if (host != null && !be.isRemoved()) return host;
        if (host != null) host.destroy();
        host = new ApertureHost(be);
        APERTURE_HOSTS.put(be, host);
        return host;
    }

    // ========================================================================
    // Face Host — parent world side of each quantum gate
    // ========================================================================

    public static class FaceHost implements IInWorldGridNodeHost, IGridNodeListener<FaceHost> {
        private final HyperboxBlockEntity be;
        private final IManagedGridNode mainNode;
        private boolean nodeCreated = false;
        private boolean bridgeAttempted = false;

        FaceHost(HyperboxBlockEntity be) {
            this.be = be;
            this.mainNode = GridHelper.createManagedNode(this, this)
                .setVisualRepresentation(be.getBlockState().getBlock())
                .setExposedOnSides(EnumSet.allOf(Direction.class))
                .setInWorldNode(true)
                .setTagName("hyperbox_face");
        }

        private void ensureNodeCreated() {
            if (!nodeCreated && !be.isRemoved() && be.getLevel() instanceof ServerLevel) {
                nodeCreated = true; // set BEFORE create() to prevent reentrant calls
                mainNode.create(be.getLevel(), be.getBlockPos());
            }
        }

        /**
         * Attempt to create the quantum link to the matching aperture.
         * Called lazily after both nodes exist.
         */
        void tryBridge() {
            if (bridgeAttempted) return;
            if (mainNode.getNode() == null) return;
            if (!(be.getLevel() instanceof ServerLevel serverLevel)) return;

            ServerLevel targetLevel = be.getLevelIfKeySet(serverLevel.getServer());
            if (targetLevel == null) return;

            bridgeAttempted = true;

            for (Direction face : Direction.values()) {
                BlockPos aperturePos = HyperboxChunkGenerator.CENTER.relative(face, 7);
                BlockEntity targetBe = targetLevel.getBlockEntity(aperturePos);
                if (targetBe instanceof ApertureBlockEntity apertureBe) {
                    ApertureHost apertureHost = getOrCreateApertureHost(apertureBe);
                    apertureHost.ensureNodeCreated();
                    IGridNode myNode = mainNode.getNode();
                    IGridNode theirNode = apertureHost.mainNode.getNode();
                    if (myNode != null && theirNode != null) {
                        // Check if already connected
                        boolean alreadyConnected = false;
                        for (var conn : myNode.getConnections()) {
                            if (conn.getOtherSide(myNode) == theirNode) {
                                alreadyConnected = true;
                                break;
                            }
                        }
                        if (!alreadyConnected) {
                            try {
                                GridHelper.createConnection(myNode, theirNode);
                            } catch (Exception e) {
                                // Grid error — safe to ignore
                            }
                        }
                    }
                }
            }
        }

        @Override
        @Nullable
        public IGridNode getGridNode(Direction dir) {
            ensureNodeCreated();
            // Defer bridge to next tick on first query
            if (!bridgeAttempted && nodeCreated) {
                GridHelper.onFirstTick(be, ignored -> tryBridge());
            }
            return mainNode.getNode();
        }

        @Override
        public AECableType getCableConnectionType(Direction dir) {
            return AECableType.DENSE_SMART;
        }

        void destroy() {
            mainNode.destroy();
            FACE_HOSTS.remove(be);
            nodeCreated = false;
            bridgeAttempted = false;
        }

        @Override public void onSaveChanges(FaceHost host, IGridNode node) {}
        @Override public void onStateChanged(FaceHost host, IGridNode node, IGridNodeListener.State state) {}
    }

    // ========================================================================
    // Aperture Host — subdimension side of each quantum gate
    // ========================================================================

    public static class ApertureHost implements IInWorldGridNodeHost, IGridNodeListener<ApertureHost> {
        private final ApertureBlockEntity be;
        private final IManagedGridNode mainNode;
        private final Direction inwardFace;
        private boolean nodeCreated = false;

        ApertureHost(ApertureBlockEntity be) {
            this.be = be;
            // FACING already points inward (toward center of the hyperbox)
            this.inwardFace = be.getBlockState().getValue(ApertureBlock.FACING);
            this.mainNode = GridHelper.createManagedNode(this, this)
                .setVisualRepresentation(be.getBlockState().getBlock())
                .setExposedOnSides(EnumSet.of(inwardFace))
                .setInWorldNode(true)
                .setTagName("hyperbox_aperture");
        }

        void ensureNodeCreated() {
            if (!nodeCreated && !be.isRemoved() && be.getLevel() instanceof ServerLevel) {
                mainNode.create(be.getLevel(), be.getBlockPos());
                nodeCreated = true;
            }
        }

        @Override
        @Nullable
        public IGridNode getGridNode(Direction dir) {
            ensureNodeCreated();
            return mainNode.getNode();
        }

        @Override
        public AECableType getCableConnectionType(Direction dir) {
            return AECableType.DENSE_SMART;
        }

        void destroy() {
            mainNode.destroy();
            APERTURE_HOSTS.remove(be);
            nodeCreated = false;
        }

        @Override public void onSaveChanges(ApertureHost host, IGridNode node) {}
        @Override public void onStateChanged(ApertureHost host, IGridNode node, IGridNodeListener.State state) {}
    }
}
