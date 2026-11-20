package aaaa0ggmc.ulmodpack.dna;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.IOException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * DNA 重组机客户端界面：左侧 FE 能量条（支持悬停显示精确电量），中左为棉签槽与 3×3 无序原料槽，
 * 右侧动态自动换行显示当前样本的必选/可选原料清单、变体提示与「开始重组」按钮。
 */
@SideOnly(Side.CLIENT)
public class GuiDnaRecombinator extends GuiContainer {

    private static final int BTN_START = 1;
    private static final int BAR_X = 8;
    private static final int BAR_Y = 20;
    private static final int BAR_W = 8;
    private static final int BAR_H = 74;

    private final TileEntityDnaRecombinator tile;
    private GuiButton startButton;

    public GuiDnaRecombinator(InventoryPlayer playerInv, TileEntityDnaRecombinator tile) {
        super(new ContainerDnaRecombinator(playerInv, tile));
        this.tile = tile;
        this.xSize = 200;
        this.ySize = 196;
    }

    @Override
    public void initGui() {
        super.initGui();
        this.buttonList.clear();
        this.startButton = addButton(new GuiButton(
                BTN_START, guiLeft + 80, guiTop + 76, 113, 18,
                I18n.format("ul.dna.gui.start")));
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == BTN_START && tile.canStartRecombine()) {
            DnaNetwork.sendStartRecombine(tile.getPos());
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        if (startButton != null) {
            startButton.enabled = tile.canStartRecombine();
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
        renderHoveredToolTip(mouseX, mouseY);
        renderEnergyTooltip(mouseX, mouseY);
    }

    private void renderEnergyTooltip(int mouseX, int mouseY) {
        int bx = guiLeft + BAR_X - 1;
        int by = guiTop + BAR_Y - 1;
        if (mouseX < bx || mouseX > bx + BAR_W + 2 || mouseY < by || mouseY > by + BAR_H + 2) {
            return;
        }
        NumberFormat fmt = NumberFormat.getIntegerInstance(Locale.US);
        List<String> lines = new ArrayList<>();
        lines.add("§b" + fmt.format(tile.getEnergyStored()) + " / " + fmt.format(tile.getMaxEnergyStored()) + " FE");
        DnaRecipe recipe = tile.getActiveRecipe();
        if (recipe != null) {
            lines.add("§7Cost: " + fmt.format(recipe.energyFe) + " FE");
        }
        drawHoveringText(lines, mouseX, mouseY);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        int x = guiLeft;
        int y = guiTop;

        // 外框与深色科技底板
        Gui.drawRect(x - 1, y - 1, x + xSize + 1, y + ySize + 1, 0xFF1A1D29);
        Gui.drawRect(x, y, x + xSize, y + ySize, 0xFF2B3042);
        // 顶部标题栏
        Gui.drawRect(x, y, x + xSize, y + 15, 0xFF1E2233);
        // 玩家背包区底板
        Gui.drawRect(x + 15, y + 110, x + xSize - 15, y + ySize - 5, 0xFF222636);

        // FE 能量柱
        int barX = x + BAR_X;
        int barY = y + BAR_Y;
        Gui.drawRect(barX - 1, barY - 1, barX + BAR_W + 1, barY + BAR_H + 1, 0xFF12141E);
        Gui.drawRect(barX, barY, barX + BAR_W, barY + BAR_H, 0xFF1B2030);
        int maxE = Math.max(1, tile.getMaxEnergyStored());
        int fillH = (int) ((long) tile.getEnergyStored() * BAR_H / maxE);
        if (fillH > 0) {
            Gui.drawRect(barX, barY + BAR_H - fillH, barX + BAR_W, barY + BAR_H, 0xFF56D8E8);
        }

        // 棉签槽 + 3×3 无序原料槽框
        drawSlotBox(x + 40, y + 20, 0xFF6B5A99);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                drawSlotBox(x + 22 + col * 18, y + 42 + row * 18, 0xFF47506B);
            }
        }

        // 玩家背包槽框（居中于 xSize=200）
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlotBox(x + 19 + col * 18, y + 114 + row * 18, 0xFF3A4056);
            }
        }
        for (int col = 0; col < 9; col++) {
            drawSlotBox(x + 19 + col * 18, y + 172, 0xFF3A4056);
        }

        // 右侧信息面板底板
        Gui.drawRect(x + 80, y + 20, x + 193, y + 73, 0xFF1F2333);
    }

    private static void drawSlotBox(int sx, int sy, int border) {
        Gui.drawRect(sx - 1, sy - 1, sx + 17, sy + 17, border);
        Gui.drawRect(sx, sy, sx + 16, sy + 16, 0xFF181B26);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        String title = I18n.format("tile.aaaa0ggmc_ul_modpack.dna_recombinator.name");
        fontRenderer.drawString(title, 8, 4, 0xE6E9F5);

        String feText = (tile.getEnergyStored() / 1000) + "k/" + (tile.getMaxEnergyStored() / 1000) + "k";
        int feW = fontRenderer.getStringWidth(feText);
        fontRenderer.drawString(feText, xSize - 8 - feW, 4, 0x7CE8F5);

        int infoX = 83;
        int infoY = 23;
        int infoW = 107;
        int maxY = 71;
        DnaRecipe recipe = tile.getActiveRecipe();
        if (recipe == null) {
            fontRenderer.drawSplitString(I18n.format("ul.dna.gui.insert_swab"), infoX, infoY, infoW, 0xA0A8C0);
            return;
        }

        ItemStack swab = tile.getInventory().getStackInSlot(TileEntityDnaRecombinator.SLOT_SWAB);
        String name = swab.hasTagCompound() ? swab.getTagCompound().getString(ItemDnaSwab.NBT_ENTITY_NAME) : "";
        if (name.isEmpty()) {
            name = recipe.entityId;
        }
        infoY = drawWrapped(name, infoX, infoY, infoW, maxY, 0xFFD98A);

        for (DnaRecipe.ItemRequirement req : recipe.required) {
            int have = tile.countMatching(req);
            boolean ok = have >= req.count;
            String line = (ok ? "§a✔ " : "§c✘ ") + req.displayName() + " " + Math.min(have, req.count) + "/" + req.count;
            infoY = drawWrapped(line, infoX, infoY, infoW, maxY, 0xFFFFFF);
        }
        for (DnaRecipe.OptionalModifier opt : recipe.optional) {
            boolean hasOpt = tile.countMatching(opt.requirement) >= opt.requirement.count;
            String key = hasOpt ? opt.presentHintKey : opt.missingHintKey;
            String hint = key != null && !key.isEmpty()
                    ? I18n.format(key)
                    : (opt.requirement.displayName() + " x" + opt.requirement.count);
            infoY = drawWrapped(hint, infoX, infoY, infoW, maxY, hasOpt ? 0x8CF0B4 : 0xE8C87A);
        }
        if (recipe.ageable) {
            boolean hasGem = tile.hasReversalGem();
            String gemHint = I18n.format(hasGem ? "ul.dna.hint.baby_ready" : "ul.dna.hint.baby_opt");
            drawWrapped(gemHint, infoX, infoY, infoW, maxY, hasGem ? 0xF299FF : 0xA8B0C8);
        }
    }

    private int drawWrapped(String text, int x, int y, int width, int maxY, int color) {
        for (String line : fontRenderer.listFormattedStringToWidth(text, width)) {
            if (y + fontRenderer.FONT_HEIGHT > maxY) {
                break;
            }
            fontRenderer.drawString(line, x, y, color);
            y += 9;
        }
        return y;
    }
}
