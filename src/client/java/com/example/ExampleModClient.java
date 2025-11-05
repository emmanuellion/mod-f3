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

public class ExampleModClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        HudRenderCallback.EVENT.register((GuiGraphics gfx, DeltaTracker delta) -> onHudRender(gfx, delta));
    }

    private void onHudRender(GuiGraphics gfx, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return;

        Font font = mc.font;

        // Contenu des lignes
        int fps = mc.getFps();
        String line1 = "" + fps;

        BlockPos bp = mc.player != null ? mc.player.blockPosition() : BlockPos.ZERO;
        double xExact = mc.player != null ? mc.player.getX() : 0.0;
        double yExact = mc.player != null ? mc.player.getY() : 0.0;
        double zExact = mc.player != null ? mc.player.getZ() : 0.0;
        String line2 = String.format("%.1f / %.1f / %.1f", xExact, yExact, zExact);

        Holder<Biome> biomeHolder = mc.level.getBiome(bp);
        String biomeId = biomeHolder.unwrapKey()
                .map(k -> k.location().toString()) // e.g. "minecraft:plains"
                .orElse("unknown:unknown");
        String line3 = biomeId.replace("minecraft:", "");

        String line4 = formatMcTime(mc.level.getDayTime());

        // Couleur de fond selon le biome
        int bgColor = pickBiomeTint(biomeId);    // ARGB
        int border = 0x66000000;                 // bordure sombre légère

        // Mesures
        int pad = 6;
        int gap = 2;
        int x = 4;
        int y = 4;

        int w1 = font.width(line1);
        int w2 = font.width(line2);
        int w3 = font.width(line3);
        int w4 = font.width(line4);
        int maxW = Math.max(Math.max(w1, w2), Math.max(w3, w4));

        int lineH = font.lineHeight;
        int lines = 4;
        int boxW = maxW + pad * 2;
        int boxH = (lineH * lines) + (gap * (lines - 1)) + pad * 2;

        // Dessin de l'encadré
        fillWithBorder(gfx, x, y, x + boxW, y + boxH, bgColor, border);

        // Texte: blanc avec ombre pour rester lisible
        int textColor = 0xFFFFFFFF;
        int ty = y + pad;
        drawStringShadow(gfx, font, line1, x + pad, ty, textColor);
        ty += lineH + gap;
        drawStringShadow(gfx, font, line2, x + pad, ty, textColor);
        ty += lineH + gap;
        drawStringShadow(gfx, font, line3, x + pad, ty, textColor);
        ty += lineH + gap;
        drawStringShadow(gfx, font, line4, x + pad, ty, textColor);
    }

    private String formatMcTime(long dayTime) {
        long ticksInDay = Math.floorMod(dayTime, 24000L);
        int hour = (int) ((ticksInDay / 1000L + 6) % 24);
        int minute = (int) ((ticksInDay % 1000L) * 60L / 1000L);
        String res = "";
        if((hour >= 6 || (hour == 5 && minute >= 30)) && hour < 19) {
            res += "☀";
        } else {
            res += "☽";
        }
        String sHour = hour >= 10 ? "" + hour : "0" + hour;
        String sMinute = minute >= 10 ? "" + minute : "0" + minute;
        res += " " + sHour + "h" + sMinute;
        return res;
    }

    // Encadré plein + petite bordure
    private void fillWithBorder(GuiGraphics gfx, int x1, int y1, int x2, int y2, int fill, int border) {
        // fond
        gfx.fill(x1, y1, x2, y2, fill);
        // bordure 1 px
        gfx.fill(x1, y1, x2, y1 + 1, border);
        gfx.fill(x1, y2 - 1, x2, y2, border);
        gfx.fill(x1, y1, x1 + 1, y2, border);
        gfx.fill(x2 - 1, y1, x2, y2, border);
    }

    // Texte avec ombre (lisibilité)
    private void drawStringShadow(GuiGraphics gfx, Font font, String txt, int x, int y, int color) {
        gfx.drawString(font, txt, x, y, color, true);
    }

    // Mapping très robuste par mots-clés du biomeId (namespace:path)
    private int pickBiomeTint(String biomeId) {
        String id = biomeId.toLowerCase();

        // Aquatique (bleu)
        if (containsAny(id, "ocean", "river", "beach", "lake", "reef", "mangrove_swamp", "swamp", "warm_ocean", "cold_ocean", "deep_ocean")) {
            return 0xAA2B77FF; // bleu semi-opaque
        }

        // Forêts / jungles / bois (vert)
        if (containsAny(id, "forest", "meadow", "pale_garden", "jungle", "taiga", "grove", "wood", "birch", "spruce", "dark_forest", "bamboo")) {
            return 0xAA34C759; // vert semi-opaque
        }

        // Neige / glace (blanc/gris très clair)
        if (containsAny(id, "snow", "snowy", "frozen", "ice", "icy", "glacier")) {
            return 0xAAFFFFFF; // blanc semi-opaque
        }

        // Désert / savane / badlands (orangé)
        if (containsAny(id, "desert", "savanna", "badlands", "mesa")) {
            return 0xAADFAE47; // sable/orangé
        }

        // Montagne / pics (gris bleuté)
        if (containsAny(id, "mountain", "peaks", "hills", "highlands", "stony")) {
            return 0xAA7A8C99;
        }

        // Nether (rouge sombre)
        if (containsAny(id, "nether", "basalt", "soul_", "crimson", "warped", "delta")) {
            return 0xAAE74C3C;
        }

        // The End (violet)
        if (containsAny(id, "the_end", ":end", "chorus", "end_highlands", "end_midlands", "end_barrens")) {
            return 0xAABB6BD9;
        }

        // Couleur par défaut (gris anthracite légèrement transparent)
        return 0xAA2C2C2C;
    }

    private boolean containsAny(String s, String... keys) {
        for (String k : keys) if (s.contains(k)) return true;
        return false;
    }
}
