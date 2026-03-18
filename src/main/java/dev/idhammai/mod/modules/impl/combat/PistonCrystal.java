package dev.idhammai.mod.modules.impl.combat;

import dev.idhammai.api.events.eventbus.EventListener;
import dev.idhammai.api.events.impl.UpdateEvent;
import dev.idhammai.api.events.impl.Render3DEvent;
import dev.idhammai.api.utils.combat.CombatUtil;
import dev.idhammai.api.utils.math.Timer;
import dev.idhammai.api.utils.player.EntityUtil;
import dev.idhammai.api.utils.player.InventoryUtil;
import dev.idhammai.api.utils.render.RenderUtil;
import dev.idhammai.api.utils.world.BlockUtil;
import dev.idhammai.mod.modules.Module;
import dev.idhammai.mod.modules.settings.impl.BooleanSetting;
import dev.idhammai.mod.modules.settings.impl.SliderSetting;

import net.minecraft.block.*;
import net.minecraft.entity.*;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.util.math.*;

import java.util.ArrayList;
import java.util.List;

public class PistonCrystal extends Module {

    public static PistonCrystal INSTANCE;

    public final SliderSetting delay = this.add(new SliderSetting("Delay", 50, 0, 500));
    public final SliderSetting attackWait = this.add(new SliderSetting("AttackWait", 100, 0, 500));

    public final SliderSetting targetRange = this.add(new SliderSetting("TargetRange", 6.0, 1.0, 12.0));
    public final SliderSetting placeRange = this.add(new SliderSetting("PlaceRange", 5.0, 1.0, 8.0));
    public final SliderSetting breakRange = this.add(new SliderSetting("BreakRange", 5.0, 1.0, 8.0));

    public final SliderSetting minDamage = this.add(new SliderSetting("MinDamage", 6.0, 0.0, 20.0));
    public final SliderSetting maxSelfDamage = this.add(new SliderSetting("MaxSelfDamage", 10.0, 0.0, 20.0));

    public final BooleanSetting selfExtrapolate = this.add(new BooleanSetting("SelfExtrapolate", true));
    public final BooleanSetting grim = this.add(new BooleanSetting("Grim", true));
    public final BooleanSetting checkInAir = this.add(new BooleanSetting("CheckInAir", true));
    public final BooleanSetting airPlace = this.add(new BooleanSetting("AirPlace", true));
    public final BooleanSetting strictDirection = this.add(new BooleanSetting("StrictDirection", true));

    public final BooleanSetting render = this.add(new BooleanSetting("Render", true));

    private final BooleanSetting rotate = this.add(new BooleanSetting("Rotate", false));
    private final BooleanSetting inventory = this.add(new BooleanSetting("InventorySwap", true));

    private final Timer timer = new Timer();
    private final Timer attackTimer = new Timer();

    private final List<BlockPos> renderList = new ArrayList<>();

    private PlayerEntity target;

    public PistonCrystal() {
        super("PistonCrystal", Category.Combat);
        INSTANCE = this;
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {

        target = CombatUtil.getClosestEnemy(targetRange.getValue());
        if (target == null) return;

        if (!timer.passedMs(delay.getValueInt())) return;
        timer.reset();

        renderList.clear();

        BlockPos pos = EntityUtil.getEntityPos(target, true);

        if (selfExtrapolate.getValue()) {
            pos = pos.add(
                    (int)(target.getVelocity().x * 2),
                    0,
                    (int)(target.getVelocity().z * 2)
            );
        }

        if (checkInAir.getValue() && !target.isOnGround()) return;

        if (mc.player.squaredDistanceTo(pos.toCenterPos())
                > targetRange.getValue() * targetRange.getValue()) return;

        if (attackTimer.passedMs(attackWait.getValueInt())) {

            for (int i = 0; i <= 2; i++) {
                BlockPos check = pos.up(i);

                if (mc.player.squaredDistanceTo(check.toCenterPos())
                        > breakRange.getValue() * breakRange.getValue()) continue;

                for (Entity entity : mc.world.getEntities()) {

                    if (!(entity instanceof EndCrystalEntity)) continue;
                    if (!entity.getBlockPos().equals(check)) continue;

                    float damage = AutoCrystal.INSTANCE.calculateDamage(entity.getPos(), target, target);
                    float self = AutoCrystal.INSTANCE.calculateDamage(entity.getPos(), mc.player, mc.player);

                    if (damage < minDamage.getValue() || self > maxSelfDamage.getValue()) continue;

                    if (grim.getValue()) {
                        mc.player.swingHand(mc.player.getActiveHand());
                    }

                    CombatUtil.attackCrystal(check, rotate.getValue(), true);
                    attackTimer.reset();
                    return;
                }
            }
        }

        for (Direction dir : Direction.values()) {

            BlockPos[] positions = new BlockPos[]{
                    pos.up(2),
                    pos.up(1),
                    pos,
                    pos.down()
            };

            for (BlockPos basePos : positions) {

                BlockPos crystalPos = basePos.offset(dir);
                BlockPos pistonPos;

                if (dir == Direction.UP) {
                    pistonPos = basePos.up(2);
                } else if (dir == Direction.DOWN) {
                    if (mc.world.getBlockState(basePos.down()).isAir()) continue;
                    pistonPos = basePos.down();
                } else {
                    pistonPos = basePos.offset(dir, 2);
                }

                boolean strictCrystal = isStrictDirection(crystalPos, dir.getOpposite());
                if (strictDirection.getValue() && !strictCrystal && !airPlace.getValue()) continue;

                boolean hasBlock = !mc.world.isAir(crystalPos.down());

                if (hasBlock) {
                    int crystal = findItem(Items.END_CRYSTAL);
                    if (crystal != -1) {
                        doSwap(crystal);
                        BlockUtil.placeCrystal(crystalPos, true);
                        renderList.add(crystalPos);
                    }
                } else if (airPlace.getValue()) {
                    int obs = findBlock(Blocks.OBSIDIAN);
                    if (obs != -1) {
                        doSwap(obs);
                        BlockUtil.placeBlock(crystalPos, true, true, true);
                        renderList.add(crystalPos);
                    }

                    int crystal = findItem(Items.END_CRYSTAL);
                    if (crystal != -1) {
                        doSwap(crystal);
                        BlockUtil.placeCrystal(crystalPos, true);
                    }
                } else continue;

                boolean strictPiston = isStrictDirection(pistonPos, dir.getOpposite());
                if (strictDirection.getValue() && !strictPiston && !airPlace.getValue()) continue;

                int piston = findClass(PistonBlock.class);
                if (piston == -1) return;

                doSwap(piston);

                BlockUtil.placeBlock(pistonPos, true, true, true);
                renderList.add(pistonPos);

                int redstone = findBlock(Blocks.REDSTONE_BLOCK);
                if (redstone != -1) {
                    doSwap(redstone);
                    BlockUtil.placeBlock(pistonPos.offset(dir.getOpposite()), true, true, true);
                    renderList.add(pistonPos.offset(dir.getOpposite()));
                }

                return;
            }
        }
    }

    @EventListener
    public void onRender3D(Render3DEvent event) {
        if (!render.getValue()) return;

        for (BlockPos pos : renderList) {
            RenderUtil.drawBox(event.getMatrixStack(), pos, 0x55FF0000);
        }
    }

    private boolean isStrictDirection(BlockPos pos, Direction dir) {
        BlockPos side = pos.offset(dir);
        return !mc.world.getBlockState(side).isAir();
    }

    private void doSwap(int slot) {
        if (inventory.getValue()) {
            InventoryUtil.inventorySwap(slot, mc.player.getInventory().selectedSlot);
        } else {
            InventoryUtil.switchToSlot(slot);
        }
    }

    public int findItem(Item item) {
        return inventory.getValue() ? InventoryUtil.findItemInventorySlot(item) : InventoryUtil.findItem(item);
    }

    public int findBlock(Block block) {
        return inventory.getValue() ? InventoryUtil.findBlockInventorySlot(block) : InventoryUtil.findBlock(block);
    }

    public int findClass(Class<?> clazz) {
        return inventory.getValue() ? InventoryUtil.findClassInventorySlot(clazz) : InventoryUtil.findClass(clazz);
    }
}
