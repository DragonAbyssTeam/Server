package org.cloudburstmc.server.entity.misc;

import com.nukkitx.math.vector.Vector3i;
import com.nukkitx.nbt.NbtMap;
import com.nukkitx.nbt.NbtMapBuilder;
import org.cloudburstmc.api.block.Block;
import org.cloudburstmc.api.block.BlockCategory;
import org.cloudburstmc.api.block.BlockState;
import org.cloudburstmc.api.entity.Entity;
import org.cloudburstmc.api.entity.EntityType;
import org.cloudburstmc.api.entity.misc.FallingBlock;
import org.cloudburstmc.api.event.entity.EntityBlockChangeEvent;
import org.cloudburstmc.api.event.entity.EntityDamageEvent;
import org.cloudburstmc.api.level.Location;
import org.cloudburstmc.api.level.gamerule.GameRules;
import org.cloudburstmc.server.block.util.BlockStateMetaMappings;
import org.cloudburstmc.server.entity.BaseEntity;
import org.cloudburstmc.server.level.Sound;
import org.cloudburstmc.server.registry.CloudBlockRegistry;
import org.cloudburstmc.server.registry.CloudItemRegistry;

import static com.nukkitx.protocol.bedrock.data.entity.EntityData.VARIANT;
import static com.nukkitx.protocol.bedrock.data.entity.EntityFlag.FIRE_IMMUNE;
import static org.cloudburstmc.api.block.BlockTypes.AIR;
import static org.cloudburstmc.api.block.BlockTypes.ANVIL;

/**
 * @author MagicDroidX
 */
public class EntityFallingBlock extends BaseEntity implements FallingBlock {

    public EntityFallingBlock(EntityType<FallingBlock> type, Location location) {
        super(type, location);
    }

    @Override
    protected void initEntity() {
        super.initEntity();

        this.fireProof = true;
        this.data.setFlag(FIRE_IMMUNE, true);
    }

    @Override
    public float getWidth() {
        return 0.98f;
    }

    @Override
    public float getLength() {
        return 0.98f;
    }

    @Override
    public float getHeight() {
        return 0.98f;
    }

    @Override
    public float getGravity() {
        return 0.04f;
    }

    @Override
    public float getDrag() {
        return 0.02f;
    }

    @Override
    protected float getBaseOffset() {
        return 0.49f;
    }

    @Override
    public boolean canCollide() {
        return false;
    }

    @Override
    public void loadAdditionalData(NbtMap tag) {
        super.loadAdditionalData(tag);

        int id;
        int meta;
        CloudBlockRegistry registry = CloudBlockRegistry.get();
        if (tag.containsKey("Tile") && tag.containsKey("Data")) {
            id = tag.getByte("Tile") & 0xff;
            meta = tag.getByte("Data");
        } else {
            NbtMap plantTag = tag.getCompound("FallingBlock");
            id = registry.getLegacyId(plantTag.getString("name"));
            meta = plantTag.getShort("val");
        }
        if (id == 0) {
            close();
            return;
        }

        this.data.setInt(VARIANT, registry.getRuntimeId(id, meta));
    }

    @Override
    public void saveAdditionalData(NbtMapBuilder tag) {
        super.saveAdditionalData(tag);

        BlockState blockState = getBlock();

        tag.putCompound("FallingBlock", NbtMap.builder()
                .putString("name", blockState.getType().toString())
                .putShort("val", (short) BlockStateMetaMappings.getMetaFromState(blockState)) //TODO: check
                .build());
    }

    public boolean canCollideWith(Entity entity) {
        return false;
    }

    @Override
    public boolean attack(EntityDamageEvent source) {
        return source.getCause() == EntityDamageEvent.DamageCause.VOID && super.attack(source);
    }

    @Override
    public boolean onUpdate(int currentTick) {

        if (closed) {
            return false;
        }

        this.timing.startTiming();

        int tickDiff = currentTick - lastUpdate;
        if (tickDiff <= 0 && !justCreated) {
            return true;
        }

        lastUpdate = currentTick;

        boolean hasUpdate = entityBaseTick(tickDiff);

        if (isAlive()) {
            this.motion = this.motion.sub(0, getGravity(), 0);

            move(this.motion);

            float friction = 1 - getDrag();

            this.motion = this.motion.mul(friction, 1 - this.getDrag(), friction);

            Vector3i pos = this.getPosition().sub(0.5, 0, 0.5).round().toInt();

            if (onGround) {
                close();
                Block b = level.getBlock(pos);
                BlockState blockState = b.getState();
                if (blockState.getType() != AIR && blockState.inCategory(BlockCategory.TRANSPARENT) && !blockState.getBehavior().canBeReplaced(b)) {
                    if (this.level.getGameRules().get(GameRules.DO_ENTITY_DROPS)) {
                        getLevel().dropItem(this.getPosition(), CloudItemRegistry.get().getItem(this.getBlock()));
                    }
                } else {
                    EntityBlockChangeEvent event = new EntityBlockChangeEvent(this, b, this.getBlock());
                    server.getEventManager().fire(event);
                    if (!event.isCancelled()) {
                        getLevel().setBlockState(pos, event.getTo(), true);

                        if (event.getTo().getType() == ANVIL) {
                            getLevel().addSound(pos, Sound.RANDOM_ANVIL_LAND);
                        }
                    }
                }
                hasUpdate = true;
            }

            updateMovement();
        }

        this.timing.stopTiming();

        return hasUpdate || !onGround || this.motion.length() > 0.00001;
    }

    public BlockState getBlock() {
        return CloudBlockRegistry.get().getBlock(this.data.getInt(VARIANT));
    }

    @Override
    public void setBlock(BlockState blockState) {
        int runtimeId = CloudBlockRegistry.get().getRuntimeId(blockState);
        this.data.setInt(VARIANT, runtimeId);
    }

    @Override
    public boolean canBeMovedByCurrents() {
        return false;
    }
}
