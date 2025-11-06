package com.example;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;

public class ExampleModClient implements ClientModInitializer {

    // EMA (exponential moving average) du vecteur de déplacement pour lisser l’inclinaison
    private Vec3 smoothedVel = Vec3.ZERO;
    private static final double VEL_EMA_ALPHA = 0.35; // 0..1 (plus grand = plus réactif)
    private static final double HORIZ_SPEED_EPS = 0.02; // seuil pour ignorer l’angle si quasi immobile

    @Override
    public void onInitializeClient() {
        HudRenderCallback.EVENT.register((GuiGraphics gfx, DeltaTracker delta) -> onHudRender(gfx));
    }

    private void onHudRender(GuiGraphics gfx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return;

        Font font = mc.font;

        int fps = mc.getFps();
        String line1 = "" + fps;

        String lineCompasss = compassLine(mc);
        BlockPos bp = mc.player != null ? mc.player.blockPosition() : BlockPos.ZERO;
        double xExact = mc.player != null ? mc.player.getX() : 0.0;
        double yExact = mc.player != null ? mc.player.getY() : 0.0;
        double zExact = mc.player != null ? mc.player.getZ() : 0.0;
        String line2 = String.format("%.1f / %.1f / %.1f", xExact, yExact, zExact);
        line2 += " " + lineCompasss;

        Holder<Biome> biomeHolder = mc.level.getBiome(bp);
        String biomeId = biomeHolder.unwrapKey()
                .map(k -> k.location().toString())
                .orElse("unknown:unknown");
        String line3 = biomeId.replace("minecraft:", "");

        String line4 = formatMcTime(mc.level.getDayTime());

        int bgColor = pickBiomeTint(biomeId);
        int border = 0x66000000;

        // Mesures
        int pad = 6;
        int gap = 2;
        int x = 4;
        int y = 4;

        int w1 = font.width(line1);
        int w2 = 142;
        int w3 = font.width(line3);
        int w4 = font.width(line4);
        int maxW = Math.max(Math.max(w1, w2), Math.max(w3, w4));

        int lineH = font.lineHeight;
        int lines = 4;
        int boxW = maxW + pad * 2;
        int boxH = (lineH * lines) + (gap * (lines - 1)) + pad * 2;

        fillWithBorder(gfx, x, y, x + boxW, y + boxH, bgColor, border);

        int textColor = 0xFFFFFFFF;
        int ty = y + pad;
        drawStringShadow(gfx, font, line1, x + pad, ty, textColor);
        ty += lineH + gap;
        drawStringShadow(gfx, font, line2, x + pad, ty, textColor);
        ty += lineH + gap;
        drawStringShadow(gfx, font, line3, x + pad, ty, textColor);
        ty += lineH + gap;
        drawStringShadow(gfx, font, line4, x + pad, ty, textColor);

        if (mc.player != null) {
            ItemStack chest = mc.player.getItemBySlot(EquipmentSlot.CHEST);
            if (!chest.isEmpty() && chest.is(Items.ELYTRA)) {
                int iconX = x + pad;
                int iconY = y + boxH + 4;

                // Icône Elytra
                gfx.renderItem(chest, iconX, iconY);

                // Durabilité
                int max = chest.getMaxDamage();
                int used = chest.getDamageValue();
                int left = Math.max(0, max - used);
                float pct = max > 0 ? (left / (float) max) : 0f;
                int duraColor = (pct >= 0.60f) ? 0xFF34C759
                        : (pct >= 0.25f) ? 0xFFF2C94C
                        : 0xFFFF3B30;

                int textX = iconX + 18;
                int textY = iconY + 4;
                drawStringShadow(gfx, font, "Elytra: " + left, textX, textY, duraColor);

                if (mc.player.isFallFlying()) {
                    Vec3 v = mc.player.getDeltaMovement();
                    double speed = v.length() * 20.0; // blocs/s
                    String sp = String.format("✈ %.1f m/s", speed);

                    int y1 = textY + lineH + 2;
                    drawStringShadow(gfx, font, sp, textX, y1, 0xFFFFFFFF);

                    // NOUVEAU : texte "towards +X, -Z" comme dans F3 (partie droite de "Facing")
                    String pitchDeg = facingPitchDeg(mc);  // ex: "-24.6"
                    int y2 = y1 + lineH + 2;
                    drawStringShadow(gfx, font, pitchDeg, textX, y2, 0xFFFFFFFF);
                }
            }
        }
    }

    private String compassLine(Minecraft mc) {
        if (mc.player == null) return "↔ ?";
        double deg = Mth.wrapDegrees(mc.player.getYRot() - 180.0F);
        String[] dirs   = {"N","NE","E","SE","S","SW","W","NW"};
        String[] arrows = {"↑","↗","→","↘","↓","↙","←","↖"};
        int idx = (int) Math.floor((deg + 22.5) / 45.0);
        idx = ((idx % 8) + 8) % 8;
        return arrows[idx] + " " + dirs[idx];
    }

    private String formatMcTime(long dayTime) {
        long ticksInDay = Math.floorMod(dayTime, 24000L);
        int hour = (int) ((ticksInDay / 1000L + 6) % 24);
        int minute = (int) ((ticksInDay % 1000L) * 60L / 1000L);
        String res = "";
        if ((hour >= 6 || (hour == 5 && minute >= 30)) && hour < 19) res += "☀"; else res += "☽";
        String sHour = hour >= 10 ? "" + hour : "0" + hour;
        String sMinute = minute >= 10 ? "" + minute : "0" + minute;
        res += " " + sHour + "h" + sMinute;
        return res;
    }

    private void fillWithBorder(GuiGraphics gfx, int x1, int y1, int x2, int y2, int fill, int border) {
        gfx.fill(x1, y1, x2, y2, fill);
        gfx.fill(x1, y1, x2, y1 + 1, border);
        gfx.fill(x1, y2 - 1, x2, y2, border);
        gfx.fill(x1, y1, x1 + 1, y2, border);
        gfx.fill(x2 - 1, y1, x2, y2, border);
    }

    private void drawStringShadow(GuiGraphics gfx, Font font, String txt, int x, int y, int color) {
        gfx.drawString(font, txt, x, y, color, true);
    }

    private int pickBiomeTint(String biomeId) {
        String id = biomeId.toLowerCase();

        if (containsAny(id, "ocean", "river", "frozen_river", "snwoy_beach", "beach", "mangrove_swamp", "swamp", "warm_ocean", "cold_ocean", "deep_ocean", "lukewarm_ocean", "deep_lukewarm_ocean", "deep_cold_ocean", "frozen_ocean", "deep_frozen_ocean")) {
            return 0xAA2B77FF;
        }
        if (containsAny(id, "mushroom_fields", "plains", "sunflower_plains", "forest", "flower_forest", "meadow", "pale_garden", "jungle", "sparse_jungle", "taiga", "old_growth_pine_taiga", "old_growth_spruce_taiga", "dark_forest", "bamboo_jungle", "birch_forest", "old_growth_birch_forest")) {
            return 0xAA34C759;
        }
        if (containsAny(id, "cherry_grove")) { return 0xAAFF05CD; }
        if (containsAny(id, "deep_dark")) { return 0xAA000000; }
        if (containsAny(id, "dripstone_caves")) { return 0xAA7A3737; }
        if (containsAny(id, "lush_caves")) { return 0xAAC8FA00; }
        if (containsAny(id, "jagged_peaks", "frozen_peaks", "grove", "snowy_slopes", "snowy_taiga", "snowy_plains", "ice_spikes")) {
            return 0xAAFFFFFF;
        }
        if (containsAny(id, "desert", "savanna", "badlands", "mesa")) { return 0xAADFAE47; }
        if (containsAny(id, "stony_peaks", "stony_shore", "windswept_hills", "windswept_gravelly_hills", "windswept_forest")) {
            return 0xAA7A8C99;
        }
        if (containsAny(id, "nether", "basalt", "soul_", "crimson", "warped", "delta")) { return 0xAAE74C3C; }
        if (containsAny(id, "the_end", ":end", "chorus", "end_highlands", "end_midlands", "end_barrens")) { return 0xAABB6BD9; }
        return 0xAA2C2C2C;
    }

    private boolean containsAny(String s, String... keys) {
        for (String k : keys) if (s.contains(k)) return true;
        return false;
    }

    private String facingPitchDeg(Minecraft mc) {
        if (mc.player == null) return "";
        float pitch = mc.player.getXRot();   // pitch caméra: haut négatif, bas positif
        return String.format("%.1f", pitch); // même style que F3
    }

}
