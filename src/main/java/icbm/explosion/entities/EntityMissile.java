package icbm.explosion.entities;

import icbm.Reference;
import icbm.Settings;
import icbm.api.ExplosiveType;
import icbm.api.IExplosive;
import icbm.api.IExplosiveContainer;
import icbm.api.ILauncherContainer;
import icbm.api.IMissile;
import icbm.api.ITarget;
import icbm.api.RadarRegistry;
import icbm.api.ExplosionEvent.ExplosivePreDetonationEvent;
import icbm.core.DamageUtility;
import icbm.core.ICBMCore;
import icbm.core.Vector2;
import icbm.core.implement.IChunkLoadHandler;
import icbm.explosion.ICBMExplosion;
import icbm.explosion.ex.Explosion;
import icbm.explosion.explosive.ExplosiveRegistry;
import icbm.explosion.machines.TileCruiseLauncher;
import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

import mekanism.api.Pos3D;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.ForgeChunkManager.Ticket;
import net.minecraftforge.common.ForgeChunkManager.Type;
import net.minecraftforge.common.MinecraftForge;
import cpw.mods.fml.common.registry.IEntityAdditionalSpawnData;

/** @Author - Calclavia */
public class EntityMissile extends Entity implements IChunkLoadHandler, IExplosiveContainer, IEntityAdditionalSpawnData, IMissile, ITarget
{
    public enum MissileType
    {
        MISSILE,
        CruiseMissile,
        LAUNCHER
    }

    public static final float SPEED = 0.012F;
    
    public boolean createdSound = false;

    public int explosiveID = 0;
    public int maxHeight = 200;
    public Pos3D targetVector = null;
    public Pos3D startPos = null;
    public Pos3D launcherPos = null;
    public boolean isExpoding = false;

    public int targetHeight = 0;
    public int feiXingTick = -1;
    // Difference
    public double deltaPathX;
    public double deltaPathY;
    public double deltaPathZ;
    // Flat Distance
    public double flatDistance;
    // The flight time in ticks
    public float missileFlightTime;
    // Acceleration
    public float acceleration;
    // Hp
    public float damage = 0;
    public float max_damage = 10;
    // Protection Time
    public int protectionTime = 2;

    private Ticket chunkTicket;

    // For anti-ballistic missile
    public Entity lockedTarget;
    // Has this missile lock it's target before?
    public boolean didTargetLockBefore = false;
    // Tracking
    public int trackingVar = -1;
    // For cluster missile
    public int missileCount = 0;

    public double daoDanGaoDu = 2;

    private boolean setExplode;
    private boolean setNormalExplode;

    // Missile Type
    public MissileType missileType = MissileType.MISSILE;

    public Pos3D xiaoDanMotion = new Pos3D();

    private double qiFeiGaoDu = 3;

    // Used for the rocket launcher preventing the players from killing themselves.
    private final HashSet<Entity> ignoreEntity = new HashSet<Entity>();

    public NBTTagCompound nbtData = new NBTTagCompound();

    public EntityMissile(World par1World)
    {
        super(par1World);
        this.setSize(1F, 1F);
        this.renderDistanceWeight = 3;
        this.isImmuneToFire = true;
        this.ignoreFrustumCheck = true;
    }

    /** Spawns a traditional missile and cruise missiles
     * 
     * @param explosiveId - Explosive ID
     * @param startPos - Starting Position
     * @param launcherPos - Missile Launcher Position */
    public EntityMissile(World world, Pos3D startPos, Pos3D launcherPos, int explosiveId)
    {
        this(world);
        this.explosiveID = explosiveId;
        this.startPos = startPos;
        this.launcherPos = launcherPos;

        this.setPosition(this.startPos.xPos, this.startPos.yPos, this.startPos.zPos);
        this.setRotation(0, 90);
    }

    /** For rocket launchers
     * 
     * @param explosiveId - Explosive ID
     * @param startPos - Starting Position
     * @param yaw - The yaw of the missle
     * @param pitch - the pitch of the missle */
    public EntityMissile(World world, Pos3D startPos, int explosiveId, float yaw, float pitch)
    {
        this(world);
        this.explosiveID = explosiveId;
        this.launcherPos = this.startPos = startPos;
        this.missileType = MissileType.LAUNCHER;
        this.protectionTime = 0;

        this.setPosition(this.startPos.xPos, this.startPos.yPos, this.startPos.zPos);
        this.setRotation(yaw, pitch);
    }

    @Override
    public String getCommandSenderName()
    {
        return ExplosiveRegistry.get(this.explosiveID).getMissileName();
    }

    @Override
    public void writeSpawnData(ByteBuf data)
    {
        try
        {
            data.writeInt(this.explosiveID);
            data.writeInt(this.missileType.ordinal());

            data.writeDouble(this.startPos.xPos);
            data.writeDouble(this.startPos.yPos);
            data.writeDouble(this.startPos.zPos);

            data.writeInt((int)this.launcherPos.xPos);
            data.writeInt((int)this.launcherPos.yPos);
            data.writeInt((int)this.launcherPos.zPos);

            data.writeFloat(rotationYaw);
            data.writeFloat(rotationPitch);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    @Override
    public void readSpawnData(ByteBuf data)
    {
        try
        {
            this.explosiveID = data.readInt();
            this.missileType = MissileType.values()[data.readInt()];
            this.startPos = new Pos3D(data.readDouble(), data.readDouble(), data.readDouble());
            this.launcherPos = new Pos3D(data.readInt(), data.readInt(), data.readInt());

            rotationYaw = data.readFloat();
            rotationPitch = data.readFloat();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    @Override
    public void launch(Pos3D target)
    {
        this.startPos = new Pos3D(this);
        this.targetVector = target;
        this.targetHeight = (int)this.targetVector.yPos;
        ((Explosion) ExplosiveRegistry.get(this.explosiveID)).launch(this);
        this.feiXingTick = 0;
        this.recalculatePath();
        this.worldObj.playSoundAtEntity(this, Reference.PREFIX + "missilelaunch", 4F, (1.0F + (this.worldObj.rand.nextFloat() - this.worldObj.rand.nextFloat()) * 0.2F) * 0.7F);
        // TODO add an event system here
        RadarRegistry.register(this);
        ICBMCore.LOGGER.info("Launching " + this.getCommandSenderName() + " (" + this.getEntityId() + ") from " + (int)startPos.xPos + ", " + (int)startPos.yPos + ", " + (int)startPos.zPos + " to " + (int)targetVector.xPos + ", " + (int)targetVector.yPos + ", " + (int)targetVector.zPos);
    }

    @Override
    public void launch(Pos3D target, int height)
    {
        this.qiFeiGaoDu = height;
        this.launch(target);
    }

    public EntityMissile ignore(Entity entity)
    {
        ignoreEntity.add(entity);
        return this;
    }

    /** Recalculates required parabolic path for the missile Registry */
    public void recalculatePath()
    {
        if (this.targetVector != null)
        {
            // Calculate the distance difference of the missile
            this.deltaPathX = this.targetVector.xPos - this.startPos.xPos;
            this.deltaPathY = this.targetVector.yPos - this.startPos.yPos;
            this.deltaPathZ = this.targetVector.zPos - this.startPos.zPos;

            // TODO: Calculate parabola and relative out the height.
            // Calculate the power required to reach the target co-ordinates
            // Ground Displacement
            this.flatDistance = Vector2.distance(new Vector2(this.startPos), new Vector2(this.targetVector));
            // Parabolic Height
            this.maxHeight = 160 + (int) (this.flatDistance * 3);
            // Flight time
            this.missileFlightTime = (float) Math.max(100, 2 * this.flatDistance) - this.feiXingTick;
            // Acceleration
            this.acceleration = (float) this.maxHeight * 2 / (this.missileFlightTime * this.missileFlightTime);
        }
    }

    @Override
    public void entityInit()
    {
        this.dataWatcher.addObject(16, -1);
        this.dataWatcher.addObject(17, 0);
        this.chunkLoaderInit(ForgeChunkManager.requestTicket(ICBMExplosion.instance, this.worldObj, Type.ENTITY));
    }

    @Override
    public void chunkLoaderInit(Ticket ticket)
    {
        if (!this.worldObj.isRemote)
        {
            if (ticket != null)
            {
                if (this.chunkTicket == null)
                {
                    this.chunkTicket = ticket;
                    this.chunkTicket.bindEntity(this);
                    this.chunkTicket.getModData();
                }

                ForgeChunkManager.forceChunk(this.chunkTicket, new ChunkCoordIntPair(this.chunkCoordX, this.chunkCoordZ));
            }
        }
    }

    final List<ChunkCoordIntPair> loadedChunks = new ArrayList<ChunkCoordIntPair>();

    public void updateLoadChunk(int newChunkX, int newChunkZ)
    {
        if (!this.worldObj.isRemote && Settings.LOAD_CHUNKS && this.chunkTicket != null)
        {
            for (ChunkCoordIntPair chunk : loadedChunks)
                ForgeChunkManager.unforceChunk(chunkTicket, chunk);

            loadedChunks.clear();
            loadedChunks.add(new ChunkCoordIntPair(newChunkX, newChunkZ));
            loadedChunks.add(new ChunkCoordIntPair(newChunkX + 1, newChunkZ + 1));
            loadedChunks.add(new ChunkCoordIntPair(newChunkX - 1, newChunkZ - 1));
            loadedChunks.add(new ChunkCoordIntPair(newChunkX + 1, newChunkZ - 1));
            loadedChunks.add(new ChunkCoordIntPair(newChunkX - 1, newChunkZ + 1));
            loadedChunks.add(new ChunkCoordIntPair(newChunkX + 1, newChunkZ));
            loadedChunks.add(new ChunkCoordIntPair(newChunkX, newChunkZ + 1));
            loadedChunks.add(new ChunkCoordIntPair(newChunkX - 1, newChunkZ));
            loadedChunks.add(new ChunkCoordIntPair(newChunkX, newChunkZ - 1));

            for (ChunkCoordIntPair chunk : loadedChunks)
                ForgeChunkManager.forceChunk(chunkTicket, chunk);

        }
    }

    @Override
    public boolean canBeCollidedWith()
    {
        return true;
    }

    /** Called to update the entity's position/logic. */
    @Override
    public void onUpdate()
    {
        if (!this.worldObj.isRemote)
        {
            ExplosivePreDetonationEvent evt = new ExplosivePreDetonationEvent(worldObj, posX, posY, posZ, ExplosiveType.AIR, ExplosiveRegistry.get(explosiveID));
            MinecraftForge.EVENT_BUS.post(evt);

            if (evt.isCanceled())
            {
                if (this.feiXingTick >= 0)
                {
                    this.dropMissileAsItem();
                }

                this.setDead();
                return;
            }
        }
        else {
        	if(!createdSound)
        	{
        		ICBMExplosion.proxy.playSound(this);
        		createdSound = true;
        	}
        }

        try
        {
            if (this.worldObj.isRemote)
            {
                this.feiXingTick = this.dataWatcher.getWatchableObjectInt(16);
                int status = this.dataWatcher.getWatchableObjectInt(17);

                switch (status)
                {
                    case 1:
                        setNormalExplode = true;
                        break;
                    case 2:
                        setExplode = true;
                        break;
                }
            }
            else
            {
                this.dataWatcher.updateObject(16, feiXingTick);
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        if (setNormalExplode)
        {
            normalExplode();
            return;
        }

        if (setExplode)
        {
            explode();
            return;
        }

        if (this.feiXingTick >= 0)
        {
            RadarRegistry.register(this);

            if (!this.worldObj.isRemote)
            {
                if (this.missileType == MissileType.CruiseMissile || this.missileType == MissileType.LAUNCHER)
                {
                    if (this.feiXingTick == 0 && this.xiaoDanMotion != null)
                    {
                        this.xiaoDanMotion = new Pos3D(this.deltaPathX / (missileFlightTime * 0.3), this.deltaPathY / (missileFlightTime * 0.3), this.deltaPathZ / (missileFlightTime * 0.3));
                        this.motionX = this.xiaoDanMotion.xPos;
                        this.motionY = this.xiaoDanMotion.yPos;
                        this.motionZ = this.xiaoDanMotion.zPos;
                    }

                    this.rotationPitch = (float) (Math.atan(this.motionY / (Math.sqrt(this.motionX * this.motionX + this.motionZ * this.motionZ))) * 180 / Math.PI);

                    // Look at the next point
                    this.rotationYaw = (float) (Math.atan2(this.motionX, this.motionZ) * 180 / Math.PI);

                    ((Explosion) ExplosiveRegistry.get(this.explosiveID)).update(this);

                    Block block = worldObj.getBlock((int)this.posX, (int)this.posY, (int)this.posZ);

                    if (this.protectionTime <= 0 && ((!worldObj.isAirBlock((int)this.posX, (int)this.posY, (int)this.posZ) && !(block instanceof BlockLiquid)) || this.posY > 1000 || this.isCollided || this.feiXingTick > 20 * 1000 || (this.motionX == 0 && this.motionY == 0 && this.motionZ == 0)))
                    {
                        setExplode();
                        return;
                    }

                    this.moveEntity(this.motionX, this.motionY, this.motionZ);
                }
                else
                {
                    // Start the launch
                    if (this.qiFeiGaoDu > 0)
                    {
                        this.motionY = SPEED * this.feiXingTick * (this.feiXingTick / 2);
                        this.motionX = 0;
                        this.motionZ = 0;
                        this.qiFeiGaoDu -= this.motionY;
                        this.moveEntity(this.motionX, this.motionY, this.motionZ);

                        if (this.qiFeiGaoDu <= 0)
                        {
                            this.motionY = this.acceleration * (this.missileFlightTime / 2);
                            this.motionX = this.deltaPathX / missileFlightTime;
                            this.motionZ = this.deltaPathZ / missileFlightTime;
                        }
                    }
                    else
                    {
                        this.motionY -= this.acceleration;

                        this.rotationPitch = (float) (Math.atan(this.motionY / (Math.sqrt(this.motionX * this.motionX + this.motionZ * this.motionZ))) * 180 / Math.PI);

                        // Look at the next point
                        this.rotationYaw = (float) (Math.atan2(this.motionX, this.motionZ) * 180 / Math.PI);

                        ((Explosion) ExplosiveRegistry.get(this.explosiveID)).update(this);

                        this.moveEntity(this.motionX, this.motionY, this.motionZ);

                        // If the missile contacts anything, it will explode.
                        if (this.isCollided)
                        {
                            this.explode();
                        }

                        // If the missile is commanded to explode before impact
                        if (this.targetHeight > 0 && this.motionY < 0)
                        {
                            // Check the block below it.
                            Block blockBelow = this.worldObj.getBlock((int) this.posX, (int) this.posY - targetHeight, (int) this.posZ);

                            if (blockBelow != null)
                            {
                                this.targetHeight = 0;
                                this.explode();
                            }
                        }
                    } // end else
                }
            }
            else
            {
                this.rotationPitch = (float) (Math.atan(this.motionY / (Math.sqrt(this.motionX * this.motionX + this.motionZ * this.motionZ))) * 180 / Math.PI);
                // Look at the next point
                this.rotationYaw = (float) (Math.atan2(this.motionX, this.motionZ) * 180 / Math.PI);
            }

            this.lastTickPosX = this.posX;
            this.lastTickPosY = this.posY;
            this.lastTickPosZ = this.posZ;

            this.spawnMissileSmoke();
            this.protectionTime--;
            this.feiXingTick++;
        }
        else if (this.missileType != MissileType.LAUNCHER)
        {
            // Check to find the launcher in which this missile belongs in.
            ILauncherContainer launcher = this.getLauncher();

            if (launcher != null)
            {
                launcher.setContainingMissile(this);

                /** Rotate the missile to the cruise launcher's rotation. */
                if (launcher instanceof TileCruiseLauncher)
                {
                    this.missileType = MissileType.CruiseMissile;
                    this.noClip = true;

                    if (this.worldObj.isRemote)
                    {
                        this.rotationYaw = -((TileCruiseLauncher) launcher).rotationYaw + 90;
                        this.rotationPitch = ((TileCruiseLauncher) launcher).rotationPitch;
                    }

                    this.posY = ((TileCruiseLauncher) launcher).yCoord + 1;
                }
            }
            else
            {
                this.setDead();
            }
        }

        super.onUpdate();
    }

    @Override
    public ILauncherContainer getLauncher()
    {
        if (this.launcherPos != null)
        {
            TileEntity tileEntity = worldObj.getTileEntity((int)launcherPos.xPos, (int)launcherPos.yPos, (int)launcherPos.zPos);

            if (tileEntity != null && tileEntity instanceof ILauncherContainer)
            {
                if (!tileEntity.isInvalid())
                {
                    return (ILauncherContainer) tileEntity;
                }
            }
        }

        return null;
    }

    @Override
    public boolean interactFirst(EntityPlayer entityPlayer)
    {
        if (((Explosion) ExplosiveRegistry.get(this.explosiveID)) != null)
        {
            if (((Explosion) ExplosiveRegistry.get(this.explosiveID)).onInteract(this, entityPlayer))
            {
                return true;
            }
        }

        if (!this.worldObj.isRemote && (this.riddenByEntity == null || this.riddenByEntity == entityPlayer))
        {
            entityPlayer.mountEntity(this);
            return true;
        }

        return false;
    }

    @Override
    public double getMountedYOffset()
    {
        if (this.missileFlightTime <= 0 && this.missileType == MissileType.MISSILE)
        {
            return height;
        }
        else if (this.missileType == MissileType.CruiseMissile)
        {
            return height / 10;
        }

        return height / 2 + motionY;
    }

    private void spawnMissileSmoke()
    {
        if (this.worldObj.isRemote)
        {
            Pos3D position = new Pos3D(this);
            // The distance of the smoke relative
            // to the missile.
            double distance = -this.daoDanGaoDu - 0.2f;
            Pos3D delta = new Pos3D();
            // The delta Y of the smoke.
            delta.yPos = Math.sin(Math.toRadians(this.rotationPitch)) * distance;
            // The horizontal distance of the
            // smoke.
            double dH = Math.cos(Math.toRadians(this.rotationPitch)) * distance;
            // The delta X and Z.
            delta.xPos = Math.sin(Math.toRadians(this.rotationYaw)) * dH;
            delta.zPos = Math.cos(Math.toRadians(this.rotationYaw)) * dH;

            position.translate(delta);
            this.worldObj.spawnParticle("flame", position.xPos, position.yPos, position.zPos, 0, 0, 0);
            ICBMExplosion.proxy.spawnParticle("missile_smoke", this.worldObj, position, 4, 2);
            position.scale(1 - 0.001 * Math.random());
            ICBMExplosion.proxy.spawnParticle("missile_smoke", this.worldObj, position, 4, 2);
            position.scale(1 - 0.001 * Math.random());
            ICBMExplosion.proxy.spawnParticle("missile_smoke", this.worldObj, position, 4, 2);
            position.scale(1 - 0.001 * Math.random());
            ICBMExplosion.proxy.spawnParticle("missile_smoke", this.worldObj, position, 4, 2);
        }
    }

    /** Checks to see if and entity is touching the missile. If so, blow up! */
    @Override
    public AxisAlignedBB getCollisionBox(Entity entity)
    {
        if (ignoreEntity.contains(entity))
            return null;

        // Make sure the entity is not an item
        if (!(entity instanceof EntityItem) && entity != this.riddenByEntity && this.protectionTime <= 0)
        {
            if (entity instanceof EntityMissile)
            {
                ((EntityMissile) entity).setNormalExplode();
            }

            this.setExplode();
        }

        return null;
    }

    @Override
    public Pos3D getPredictedPosition(int t)
    {
        Pos3D guJiDiDian = new Pos3D(this);
        double tempMotionY = this.motionY;

        if (this.feiXingTick > 20)
        {
            for (int i = 0; i < t; i++)
            {
                if (this.missileType == MissileType.CruiseMissile || this.missileType == MissileType.LAUNCHER)
                {
                    guJiDiDian.xPos += this.xiaoDanMotion.xPos;
                    guJiDiDian.yPos += this.xiaoDanMotion.yPos;
                    guJiDiDian.zPos += this.xiaoDanMotion.zPos;
                }
                else
                {
                    guJiDiDian.xPos += this.motionX;
                    guJiDiDian.yPos += tempMotionY;
                    guJiDiDian.zPos += this.motionZ;

                    tempMotionY -= this.acceleration;
                }
            }
        }

        return guJiDiDian;
    }

    @Override
    public void setNormalExplode()
    {
        setNormalExplode = true;
        dataWatcher.updateObject(17, 1);
    }

    @Override
    public void setExplode()
    {
        setExplode = true;
        dataWatcher.updateObject(17, 2);
    }

    @Override
    public void setDead()
    {
        RadarRegistry.unregister(this);

        if (chunkTicket != null)
        {
            ForgeChunkManager.releaseTicket(chunkTicket);
        }

        super.setDead();
    }

    @Override
    public void explode()
    {
        try
        {
            // Make sure the missile is not already exploding
            if (!this.isExpoding)
            {
                if (this.explosiveID == 0)
                {
                    if (!this.worldObj.isRemote)
                    {
                        this.worldObj.createExplosion(this, this.posX, this.posY, this.posZ, 5F, true);
                    }
                }
                else
                {
                    ((Explosion) ExplosiveRegistry.get(this.explosiveID)).createExplosion(this.worldObj, this.posX, this.posY, this.posZ, this);
                }

                this.isExpoding = true;

                ICBMCore.LOGGER.info(this.getCommandSenderName() + " (" + this.getEntityId() + ") exploded in " + (int) this.posX + ", " + (int) this.posY + ", " + (int) this.posZ);
            }

            setDead();

        }
        catch (Exception e)
        {
            ICBMCore.LOGGER.severe("Missile failed to explode properly. Report this to the developers.");
            e.printStackTrace();
        }
    }

    @Override
    public void normalExplode()
    {
        if (!this.isExpoding)
        {
            isExpoding = true;

            if (!this.worldObj.isRemote)
            {
                worldObj.createExplosion(this, this.posX, this.posY, this.posZ, 5F, true);
            }

            setDead();
        }
    }

    @Override
    public void dropMissileAsItem()
    {
        if (!this.isExpoding && !this.worldObj.isRemote)
        {
            EntityItem entityItem = new EntityItem(this.worldObj, this.posX, this.posY, this.posZ, new ItemStack(ICBMExplosion.itemMissile, 1, this.explosiveID));

            float var13 = 0.05F;
            Random random = new Random();
            entityItem.motionX = ((float) random.nextGaussian() * var13);
            entityItem.motionY = ((float) random.nextGaussian() * var13 + 0.2F);
            entityItem.motionZ = ((float) random.nextGaussian() * var13);
            this.worldObj.spawnEntityInWorld(entityItem);
        }

        this.setDead();
    }

    /** (abstract) Protected helper method to read subclass entity data from NBT. */
    @Override
    protected void readEntityFromNBT(NBTTagCompound nbt)
    {
        this.startPos = Pos3D.read(nbt.getCompoundTag("kaiShi"));
        this.targetVector = Pos3D.read(nbt.getCompoundTag("muBiao"));
        this.launcherPos = Pos3D.read(nbt.getCompoundTag("faSheQi"));
        this.acceleration = nbt.getFloat("jiaSu");
        this.targetHeight = nbt.getInteger("baoZhaGaoDu");
        this.explosiveID = nbt.getInteger("haoMa");
        this.feiXingTick = nbt.getInteger("feiXingTick");
        this.qiFeiGaoDu = nbt.getDouble("qiFeiGaoDu");
        this.missileType = MissileType.values()[nbt.getInteger("xingShi")];
        this.nbtData = nbt.getCompoundTag("data");
    }

    /** (abstract) Protected helper method to write subclass entity data to NBT. */
    @Override
    protected void writeEntityToNBT(NBTTagCompound nbt)
    {
        if (this.startPos != null)
        {
            nbt.setTag("kaiShi", this.startPos.write(new NBTTagCompound()));
        }
        if (this.targetVector != null)
        {
            nbt.setTag("muBiao", this.targetVector.write(new NBTTagCompound()));
        }

        if (this.launcherPos != null)
        {
            nbt.setTag("faSheQi", this.launcherPos.write(new NBTTagCompound()));
        }

        nbt.setFloat("jiaSu", this.acceleration);
        nbt.setInteger("haoMa", this.explosiveID);
        nbt.setInteger("baoZhaGaoDu", this.targetHeight);
        nbt.setInteger("feiXingTick", this.feiXingTick);
        nbt.setDouble("qiFeiGaoDu", this.qiFeiGaoDu);
        nbt.setInteger("xingShi", this.missileType.ordinal());
        nbt.setTag("data", this.nbtData);
    }

    @Override
    public float getShadowSize()
    {
        return 1.0F;
    }

    @Override
    public int getTicksInAir()
    {
        return this.feiXingTick;
    }

    @Override
    public IExplosive getExplosiveType()
    {
        return ExplosiveRegistry.get(this.explosiveID);
    }

    @Override
    public boolean attackEntityFrom(DamageSource source, float damage)
    {
        if (DamageUtility.canHarm(this, source, damage))
        {
            this.damage += damage;
            if (this.damage >= this.max_damage)
            {
                this.setDead();
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean canBeTargeted(Object turret)
    {
        return this.getTicksInAir() > 0;
    }

    @Override
    public TargetType getType()
    {
        return TargetType.MISSILE;
    }

    @Override
    public NBTTagCompound getTagCompound()
    {
        return this.nbtData;
    }

}