package shit.zen.modules.impl.combat;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Squid;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.GL11;
import shit.zen.ClientBase;
import shit.zen.ZenClient;
import shit.zen.event.impl.PreMotionEvent;
import shit.zen.event.impl.RenderEvent;
import shit.zen.event.impl.SprintEvent;
import shit.zen.event.impl.TickEvent;
import shit.zen.event.impl.WorldChangeEvent;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.modules.impl.player.AutoWebPlace;
import shit.zen.modules.impl.world.Teams;
import shit.zen.settings.impl.BooleanSetting;
import shit.zen.settings.impl.ModeSetting;
import shit.zen.settings.impl.NumberSetting;
import shit.zen.utils.game.RotationUtil;
import shit.zen.utils.rotation.Rotation;
import shit.zen.utils.rotation.RotationHandler;
import shit.zen.event.EventTarget;

public class KillAura extends Module {
    public static KillAura INSTANCE;
    public static Entity target;
    public static Entity aimingTarget;
    public static List<Entity> targetList = new ArrayList<>();

    public final BooleanSetting attackPlayer;
    public final BooleanSetting attackInvisible;
    public final BooleanSetting attackAnimals;
    public final BooleanSetting attackMobs;
    public final BooleanSetting multiAttack;
    public final BooleanSetting infSwitch;
    public final BooleanSetting preferBaby;
    public final BooleanSetting targetHud;
    public final BooleanSetting sprintSync;
    public final BooleanSetting targetEsp;
    public final BooleanSetting noUseItem;
    public final BooleanSetting aboveTarget;
    public final NumberSetting attackRange;
    public final NumberSetting aps;
    public final NumberSetting switchAttackTimes;
    public final NumberSetting switchSize;
    public final NumberSetting switchDelay;
    public final NumberSetting fov;
    public final NumberSetting hurtTime;
    public final ModeSetting rotationsMode;
    public final ModeSetting sortMode;
    public final ModeSetting attackMode;

    private RotationUtil.BestHitInfo prevBestHit;
    private RotationUtil.BestHitInfo currentBestHit;
    private final int attackTimes;
    private float attacks;
    private int targetIndex;
    public int sprintTickCounter;
    private int sprintCounter;
    public Rotation rotation;

    public KillAura() {
        super("KillAura", Category.COMBAT);
        this.attackPlayer = new BooleanSetting("Attack Player", true);
        this.attackInvisible = new BooleanSetting("Attack Invisible", false);
        this.attackAnimals = new BooleanSetting("Attack Animals", false);
        this.attackMobs = new BooleanSetting("Attack Mobs", false);
        this.multiAttack = new BooleanSetting("Multi Attack", false);
        this.infSwitch = new BooleanSetting("Inf Switch", false);
        this.preferBaby = new BooleanSetting("Prefer Baby", false);
        this.targetHud = new BooleanSetting("Target HUD", false);
        this.sprintSync = new BooleanSetting("Sprint Sync", false);
        this.targetEsp = new BooleanSetting("Target ESP", false);
        this.noUseItem = new BooleanSetting("No Use Item", false);
        this.aboveTarget = new BooleanSetting("Above Target", false);
        this.attackRange = new NumberSetting("Attack Range", 5.0, 1.0, 6.0, 0.1);
        this.aps = new NumberSetting("APS", 10.0, 1.0, 20.0, 1.0);
        this.switchAttackTimes = new NumberSetting("Switch Attack Times", 10.0, 1.0, 20.0, 1.0);
        this.switchSize = new NumberSetting("Switch Size", 1.0, 1.0, 5.0, 1.0,
                () -> !(Boolean) this.infSwitch.getValue());
        this.switchDelay = new NumberSetting("Switch Delay", 1.0, 1.0, 10.0, 1.0);
        this.fov = new NumberSetting("FOV", 360.0, 10.0, 360.0, 1.0);
        this.hurtTime = new NumberSetting("Hurt Time", 10.0, 0.0, 10.0, 1.0);
        this.rotationsMode = new ModeSetting("Rotations", "Smooth", "Snap").withDefault("Smooth");
        this.sortMode = new ModeSetting("Sort", "Distance", "FOV", "Health", "None").withDefault("Distance");
        this.attackMode = new ModeSetting("Attack Mode", "Single", "Switch", "Multi", "None").withDefault("Single");
        this.attackTimes = 0;
        this.attacks = 0.0f;
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        this.rotation = null;
        this.targetIndex = 0;
        this.attacks = 0.0f;
        target = null;
        aimingTarget = null;
        targetList.clear();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        this.attacks = 0.0f;
        target = null;
        aimingTarget = null;
        this.sprintTickCounter = 0;
        this.sprintCounter = 0;
        super.onDisable();
    }

    @EventTarget
    public void onWorldChange(WorldChangeEvent event) {
        target = null;
        aimingTarget = null;
        this.attacks = 0.0f;
        this.setEnabled(false);
    }

    @EventTarget
    public void onRender(RenderEvent event) {
        if (!this.targetEsp.getValue() || mc.player == null) {
            return;
        }
        PoseStack poseStack = event.poseStack();
        float partialTick = event.partialTick();
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        RenderSystem.setShader(GameRenderer::getPositionShader);
        for (Entity entity : targetList) {
            if (!(entity instanceof LivingEntity)) continue;
            if (entity == target) {
                RenderSystem.setShaderColor(200.0f / 255.0f, 0.0f, 0.0f, 60.0f / 255.0f);
            } else {
                RenderSystem.setShaderColor(0.0f, 200.0f / 255.0f, 0.0f, 60.0f / 255.0f);
            }
            double motionX = entity.getX() - entity.xo;
            double motionY = entity.getY() - entity.yo;
            double motionZ = entity.getZ() - entity.zo;
            AABB box = entity.getBoundingBox()
                    .move(-motionX, -motionY, -motionZ)
                    .move(partialTick * motionX, partialTick * motionY, partialTick * motionZ);
            shit.zen.utils.render.RenderUtil.drawSolidBox(box, poseStack);
        }
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        poseStack.popPose();
    }

    @EventTarget
    public void onSprint(SprintEvent event) {
        if (this.sprintSync.getValue()) {
            ++this.sprintTickCounter;
            if (this.sprintTickCounter % 2 == 0 && mc.player != null) {
                mc.player.setSprinting(false);
            }
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (mc.screen != null) {
            target = null;
            aimingTarget = null;
            this.rotation = null;
            this.currentBestHit = null;
            this.prevBestHit = null;
            targetList.clear();
            return;
        }
        boolean isSwitch = this.switchSize.getValue().intValue() > 1;
        this.updateTargets();
        aimingTarget = this.getTarget();
        this.prevBestHit = this.currentBestHit;
        this.currentBestHit = null;
        this.rotation = null;
        if (aimingTarget != null) {
            RotationUtil.BestHitInfo hit = RotationUtil.getBestHit(aimingTarget);
            this.currentBestHit = hit;
            if (hit != null && hit.rotation() != null) {
                this.rotation = hit.rotation();
            }
        }
        if (targetList.isEmpty()) {
            target = null;
            return;
        }
        if (this.targetIndex > targetList.size() - 1) {
            this.targetIndex = 0;
        }
        boolean switchByAttacks = this.attacks >= this.switchAttackTimes.getValue().floatValue();
        boolean switchByDistance = this.currentBestHit != null && this.currentBestHit.distance() > 3.0;
        if (targetList.size() > 1 && (switchByAttacks || switchByDistance)) {
            this.attacks = 0.0f;
            for (int i = 0; i < targetList.size(); ++i) {
                ++this.targetIndex;
                if (this.targetIndex > targetList.size() - 1) {
                    this.targetIndex = 0;
                }
                Entity nextTarget = targetList.get(this.targetIndex);
                RotationUtil.BestHitInfo nextHit = RotationUtil.getBestHit(nextTarget);
                if (nextHit != null && nextHit.distance() < 3.0) {
                    break;
                }
            }
        }
        if (this.targetIndex > targetList.size() - 1 || !isSwitch) {
            this.targetIndex = 0;
        }
        target = targetList.get(this.targetIndex);
        this.attacks += this.aps.getValue().floatValue() / 20.0f;
    }

    @EventTarget
    public void onPreMotion(PreMotionEvent event) {
        if (mc.player == null) return;
        if (this.isWebPlacing()) {
            this.attacks = 0.0f;
            return;
        }
        if (mc.player.getUseItem().isEmpty()
                && mc.screen == null
                && (this.targetEsp.getValue() || ClientBase.delayPackets.isEmpty())) {
            while (this.attacks >= 1.0f) {
                this.doAttack();
                this.attacks -= 1.0f;
            }
        } else {
            this.attacks = 0.0f;
        }
    }

    public void doAttack() {
        if (mc.player == null || mc.gameMode == null || targetList.isEmpty()) {
            return;
        }
        HitResult hitResult = mc.hitResult;
        if (hitResult != null && hitResult.getType() == HitResult.Type.ENTITY) {
            Entity hitEntity = ((EntityHitResult) hitResult).getEntity();
            AntiBots antiBots = AntiBots.INSTANCE;
            if (antiBots != null && antiBots.isEnabled() && AntiBots.isBot(hitEntity)) {
                return;
            }
        }
        if (this.multiAttack.getValue()) {
            int attacked = 0;
            for (Entity entity : targetList) {
                if (!this.isValidAttack(entity)) continue;
                Vec3 closest = RotationUtil.closestPoint(mc.player.getEyePosition(), entity.getBoundingBox());
                if (closest.distanceTo(mc.player.getEyePosition()) >= 3.0) continue;
                this.attackEntity(entity);
                if (++attacked >= 2) {
                    break;
                }
            }
        } else if (hitResult != null && hitResult.getType() == HitResult.Type.ENTITY) {
            Entity hitEntity = ((EntityHitResult) hitResult).getEntity();
            if (this.isValidAttack(hitEntity)) {
                this.attackEntity(hitEntity);
            }
        } else if (target != null && this.isValidAttack(target)) {
            this.attackEntity(target);
        }
    }

    public Entity getTarget() {
        Entity entity = target;
        if (entity == null) {
            List<Entity> list = this.getTargets();
            if (list != null && !list.isEmpty()) {
                entity = list.get(0);
            }
        }
        if (entity != null) {
            AntiBots antiBots = AntiBots.INSTANCE;
            if (antiBots != null && antiBots.isEnabled() && AntiBots.isBot(entity)) {
                return null;
            }
        }
        return entity;
    }

    public void updateTargets() {
        targetList = this.getTargets();
    }

    public boolean isValidTarget(Entity entity) {
        if (!ZenClient.isReady()) return false;
        if (entity == mc.player) return false;
        if (entity instanceof LivingEntity livingEntity) {
            AntiBots antiBots = AntiBots.INSTANCE;
            if (antiBots != null && antiBots.isEnabled() && (AntiBots.isBot(entity) || AntiBots.isBedWarsBot(entity))) {
                return false;
            }
            if (livingEntity.isDeadOrDying() || livingEntity.getHealth() <= 0.0f) return false;
            if (entity instanceof ArmorStand) return false;
            if (entity.isInvisible() && !(Boolean) this.attackInvisible.getValue()) return false;
            if (entity instanceof Player player) {
                if (this.aboveTarget.getValue() && player.getY() >= mc.player.getY() + 0.05f) {
                    return true;
                }
                if (ZenClient.isOwner(entity.getName().getString())) return false;
            }
            if (Teams.isSameTeam(entity)) return false;
            if (entity instanceof Player && !(Boolean) this.attackPlayer.getValue()) return false;
            if (entity instanceof Player && (entity.getBbWidth() < 0.5 || livingEntity.isSleeping())) return false;
            if ((entity instanceof Mob || entity instanceof Slime || entity instanceof Bat || entity instanceof AbstractGolem)
                    && !(Boolean) this.attackMobs.getValue()) {
                return false;
            }
            if ((entity instanceof Animal || entity instanceof Squid) && !(Boolean) this.attackAnimals.getValue()) {
                return false;
            }
            if (entity instanceof Villager && !(Boolean) this.attackAnimals.getValue()) return false;
            return !(entity instanceof Player) || !entity.isSpectator();
        }
        return false;
    }

    public boolean isValidAttack(Entity entity) {
        if (mc.player == null) return false;
        if (!this.isValidTarget(entity)) return false;
        if (entity instanceof LivingEntity le && le.hurtTime > this.hurtTime.getValue().intValue()) {
            return false;
        }
        Vec3 vec3 = RotationUtil.closestPoint(mc.player.getEyePosition(), entity.getBoundingBox());
        if (vec3.distanceTo(mc.player.getEyePosition()) > this.attackRange.getValue().floatValue()) {
            return false;
        }
        return RotationUtil.isEntityInFov(entity, this.fov.getValue().floatValue() / 2.0f);
    }

    public void attackEntity(Entity entity) {
        if (mc.player == null || mc.gameMode == null || entity == null) {
            return;
        }
        float currentYaw = mc.player.getYRot();
        float currentPitch = mc.player.getXRot();
        Rotation targetRot = RotationHandler.targetRotation != null
                ? RotationHandler.targetRotation
                : (this.rotation != null ? this.rotation : new Rotation(currentYaw, currentPitch));
        mc.player.setYRot(targetRot.getYaw());
        mc.player.setXRot(targetRot.getPitch());
        mc.gameMode.attack(mc.player, entity);
        mc.player.swing(InteractionHand.MAIN_HAND);
        mc.player.setYRot(currentYaw);
        mc.player.setXRot(currentPitch);
    }

    private boolean isWebPlacing() {
        return AutoWebPlace.INSTANCE != null && AutoWebPlace.INSTANCE.isEnabled() && AutoWebPlace.targetRotation != null;
    }

    private List<Entity> getTargets() {
        if (mc.player == null || mc.level == null) {
            return new ArrayList<>();
        }
        Stream<Entity> stream = StreamSupport.stream(mc.level.entitiesForRendering().spliterator(), true)
                .filter(this::isValidAttack);
        List<Entity> possibleTargets = stream.collect(Collectors.toList());
        String mode = this.sortMode.getValue();
        if ("Distance".equalsIgnoreCase(mode)) {
            possibleTargets.sort(Comparator.comparingDouble(KillAura::getDistanceToPlayer));
        } else if ("FOV".equalsIgnoreCase(mode)) {
            possibleTargets.sort(Comparator.comparingDouble(KillAura::getAngleDiffToTarget));
        } else if ("Health".equalsIgnoreCase(mode)) {
            possibleTargets.sort(Comparator.comparingDouble(KillAura::getEntityHealth));
        }
        if (this.preferBaby.getValue()
                && possibleTargets.stream().anyMatch(KillAura::isBaby)) {
            possibleTargets.removeIf(KillAura::isNotBaby);
        }
        possibleTargets.sort(Comparator.comparing(KillAura::getCrystalPriority));
        if (this.infSwitch.getValue()) {
            return possibleTargets;
        }
        int limit = (int) Math.min(possibleTargets.size(), this.switchSize.getValue().intValue());
        return new ArrayList<>(possibleTargets.subList(0, limit));
    }

    private static Integer getCrystalPriority(Entity entity) {
        return entity instanceof EndCrystal ? 0 : 1;
    }

    private static boolean isNotBaby(Entity entity) {
        return !(entity instanceof LivingEntity) || !((LivingEntity) entity).isBaby();
    }

    private static boolean isBaby(Entity entity) {
        return entity instanceof LivingEntity && ((LivingEntity) entity).isBaby();
    }

    private static double getEntityHealth(Entity entity) {
        if (entity instanceof LivingEntity le) {
            return le.getHealth();
        }
        return 0.0;
    }

    private static double getAngleDiffToTarget(Entity entity) {
        return RotationUtil.angleDiff(RotationHandler.targetRotation.getYaw(), RotationUtil.entityRotation(entity).getYaw());
    }

    private static double getDistanceToPlayer(Entity entity) {
        return entity.distanceTo(mc.player);
    }

    private static boolean isLivingEntity(Entity entity) {
        return entity instanceof LivingEntity;
    }
}
