/*
 * File created ~ 19 - 9 - 2026
 */

package leaf.soulhome.entity;

import leaf.soulhome.SoulHome;
import leaf.soulhome.registry.EntityRegistry;
import leaf.soulhome.structures.VesselLifecycleService;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.world.ForgeChunkManager;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

/**
 * The body a player leaves behind when they enter their soul (#182), sitting where they were
 * standing. Deliberately not a {@code Mob}: no goal selector, no navigation, no spawn egg, no
 * natural spawning - the only ways one exists are {@link VesselLifecycleService}'s own calls.
 *
 * <h2>One health pool, and it is not this entity's (rule 1 of #181)</h2>
 *
 * The vessel carries no health anyone can meaningfully reduce. {@link #hurt} never lowers it and
 * never kills it - forwarding a hit to the owner is #185's job, not this class's. It still returns
 * {@code true} from a real hit, because vanilla combat code only applies knockback when the target
 * entity's own {@code hurt()} succeeds; a body that could not even be shoved would not read as a
 * body. Because health never moves, every other damage path baseTick would otherwise route through
 * {@code hurt()} - fire, drowning, starvation, the void - falls out for free with no extra
 * overrides.
 *
 * <h2>The chunk ticket (rule 3 of #181)</h2>
 *
 * A vessel left alone in single player, or in any area a server has nobody else standing in, would
 * have its chunk unload within seconds without this: an unloaded vessel cannot be hit, so damage
 * transfer, the spill and Soulgaze would all quietly do nothing. {@link #onAddedToWorld} force-
 * loads the vessel's own chunk, ticking, under this mod's id with the vessel's own UUID as the
 * ticket owner; {@link #tick} keeps that ticket following the vessel if knockback ever carries it
 * into a neighbouring chunk; {@link #onRemovedFromWorld} always releases it.
 */
public class SoulVesselEntity extends LivingEntity
{
    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER_ID =
            SynchedEntityData.defineId(SoulVesselEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    private static final EntityDataAccessor<String> DATA_OWNER_NAME =
            SynchedEntityData.defineId(SoulVesselEntity.class, EntityDataSerializers.STRING);

    private static final String NBT_OWNER_ID = "OwnerId";
    private static final String NBT_OWNER_NAME = "OwnerName";
    private static final String NBT_FRAGILITY = "Fragility";

    /** How hard an incoming hit should eventually land on the owner - read only once #185 exists. */
    private float fragility = 1.0f;

    /**
     * {@code LivingEntity} leaves equipment storage to its subclass rather than {@code Mob}'s own
     * arrays - a snapshot taken once at spawn (see {@link #spawn}), cosmetic per #182 and never
     * written again.
     */
    private final NonNullList<ItemStack> equipment =
            NonNullList.withSize(EquipmentSlot.values().length, ItemStack.EMPTY);

    /** The chunk currently held by this vessel's own forced-chunk ticket, or null before it spawns one. */
    @Nullable
    private ChunkPos forcedChunk;

    /**
     * Set once, by {@link #remove} itself, so that a normal return through the vessel does not
     * also read as a disturbance. Everything else that can end a vessel - killed, {@code /kill}'d,
     * a stale ticket cleaned up after a crash - leaves this false, which is what tells
     * {@link VesselLifecycleService} to eject the owner rather than let their trip resolve quietly.
     */
    private boolean returningPeacefully;

    public SoulVesselEntity(EntityType<? extends SoulVesselEntity> type, Level level)
    {
        super(type, level);
    }

    /** Every attribute this entity reads has to exist on its supplier or {@code getAttribute} throws. */
    public static AttributeSupplier.Builder createAttributes()
    {
        return LivingEntity.createLivingAttributes()
                .add(Attributes.MAX_HEALTH, 20.0d)
                .add(Attributes.MOVEMENT_SPEED, 0.0d)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.0d);
    }

    /**
     * Spawns and adds a vessel at the owner's own position - {@link VesselLifecycleService} is the
     * only caller, so that every entry path funnels through one place regardless of which item or
     * ability started it.
     */
    public static SoulVesselEntity spawn(ServerLevel level, ServerPlayer owner, float fragility)
    {
        final SoulVesselEntity vessel = new SoulVesselEntity(EntityRegistry.SOUL_VESSEL.get(), level);

        vessel.moveTo(owner.getX(), owner.getY(), owner.getZ(), owner.getYRot(), 0.0f);
        vessel.setYHeadRot(owner.getYHeadRot());
        vessel.setOwner(owner);
        vessel.fragility = fragility;

        for (EquipmentSlot slot : EquipmentSlot.values())
        {
            // a snapshot, not a live mirror - cosmetic per #182, so it is not worth a per-tick sync
            vessel.setItemSlot(slot, owner.getItemBySlot(slot).copy());
        }

        level.addFreshEntity(vessel);
        return vessel;
    }

    private void setOwner(ServerPlayer owner)
    {
        this.entityData.set(DATA_OWNER_ID, Optional.of(owner.getUUID()));
        this.entityData.set(DATA_OWNER_NAME, owner.getGameProfile().getName());
    }

    public Optional<UUID> getOwnerId()
    {
        return this.entityData.get(DATA_OWNER_ID);
    }

    public String getOwnerName()
    {
        return this.entityData.get(DATA_OWNER_NAME);
    }

    /** See {@link #returningPeacefully}. Called only by {@link VesselLifecycleService}. */
    public void markReturningPeacefully()
    {
        this.returningPeacefully = true;
    }

    public boolean isReturningPeacefully()
    {
        return this.returningPeacefully;
    }

    public float getFragility()
    {
        return this.fragility;
    }

    @Override
    protected void defineSynchedData()
    {
        super.defineSynchedData();
        this.entityData.define(DATA_OWNER_ID, Optional.empty());
        this.entityData.define(DATA_OWNER_NAME, "");
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag)
    {
        super.addAdditionalSaveData(tag);

        this.getOwnerId().ifPresent(id -> tag.putUUID(NBT_OWNER_ID, id));
        tag.putString(NBT_OWNER_NAME, this.getOwnerName());
        tag.putFloat(NBT_FRAGILITY, this.fragility);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);

        if (tag.hasUUID(NBT_OWNER_ID))
        {
            this.entityData.set(DATA_OWNER_ID, Optional.of(tag.getUUID(NBT_OWNER_ID)));
        }

        this.entityData.set(DATA_OWNER_NAME, tag.getString(NBT_OWNER_NAME));

        if (tag.contains(NBT_FRAGILITY))
        {
            this.fragility = tag.getFloat(NBT_FRAGILITY);
        }
    }

    @Override
    public void tick()
    {
        super.tick();

        if (!this.level().isClientSide && this.level() instanceof ServerLevel serverLevel)
        {
            this.updateForcedChunk(serverLevel);
        }
    }

    @Override
    public void onAddedToWorld()
    {
        super.onAddedToWorld();

        if (!this.level().isClientSide && this.level() instanceof ServerLevel serverLevel)
        {
            this.updateForcedChunk(serverLevel);
            VesselLifecycleService.onVesselAdded(this);
        }
    }

    @Override
    public void onRemovedFromWorld()
    {
        super.onRemovedFromWorld();

        if (!this.level().isClientSide && this.level() instanceof ServerLevel serverLevel)
        {
            this.releaseForcedChunk(serverLevel);
            VesselLifecycleService.onVesselRemoved(this);
        }
    }

    private void updateForcedChunk(ServerLevel serverLevel)
    {
        final ChunkPos current = this.chunkPosition();

        if (current.equals(this.forcedChunk))
        {
            return;
        }

        if (this.forcedChunk != null)
        {
            ForgeChunkManager.forceChunk(
                    serverLevel, SoulHome.MODID, this.getUUID(), this.forcedChunk.x, this.forcedChunk.z, false, true);
        }

        ForgeChunkManager.forceChunk(serverLevel, SoulHome.MODID, this.getUUID(), current.x, current.z, true, true);
        this.forcedChunk = current;
    }

    private void releaseForcedChunk(ServerLevel serverLevel)
    {
        if (this.forcedChunk == null)
        {
            return;
        }

        ForgeChunkManager.forceChunk(
                serverLevel, SoulHome.MODID, this.getUUID(), this.forcedChunk.x, this.forcedChunk.z, false, true);
        this.forcedChunk = null;
    }

    /**
     * No health of its own to lose (rule 1 of #181) - a real hit still plays the hurt sound and
     * animation, and still returns {@code true}, because vanilla melee code only knocks a target
     * back once its own {@code hurt()} has succeeded. Forwarding the amount to the owner is #185's
     * hole in the soul dimension's blanket damage cancel, not this method's.
     */
    @Override
    public boolean hurt(DamageSource source, float amount)
    {
        if (this.level().isClientSide || this.isRemoved() || this.isInvulnerableTo(source))
        {
            return false;
        }

        this.hurtDuration = 10;
        this.hurtTime = this.hurtDuration;
        this.playHurtSound(source);
        this.level().broadcastEntityEvent(this, (byte) 2);
        return true;
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot)
    {
        return this.equipment.get(slot.getIndex());
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack)
    {
        this.equipment.set(slot.getIndex(), stack);
    }

    @Override
    public Iterable<ItemStack> getArmorSlots()
    {
        return Arrays.stream(EquipmentSlot.values())
                .filter(EquipmentSlot::isArmor)
                .map(this::getItemBySlot)
                .toList();
    }

    /** No hand ever swings, but {@code LivingEntity} demands an answer for which one would. */
    @Override
    public HumanoidArm getMainArm()
    {
        return HumanoidArm.RIGHT;
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source)
    {
        // no health to lose, and no dust cloud for a body that never actually lands hard
        return false;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source)
    {
        return SoundEvents.PLAYER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound()
    {
        // never actually played - see the class javadoc - but LivingEntity wants a non-null answer
        return SoundEvents.PLAYER_DEATH;
    }

    @Override
    public MobType getMobType()
    {
        return MobType.UNDEFINED;
    }

    @Override
    public boolean isPushable()
    {
        return true;
    }

    @Override
    public boolean isAttackable()
    {
        return true;
    }

    @Override
    public boolean isPickable()
    {
        return true;
    }

    /** A body sitting cross-legged has no business flying open a door or clicking a button. */
    @Override
    public InteractionResult interactAt(Player player, Vec3 vec, InteractionHand hand)
    {
        return InteractionResult.PASS;
    }
}
