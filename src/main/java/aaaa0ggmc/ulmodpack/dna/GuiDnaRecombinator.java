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

/**
 * DNA 重组机客户端界面：左侧 FE 能量条，中左为棉签槽与 2×2 原料槽，
 * 右侧动态显示当前样本的必选/可选原料清单、变体提示与「开始重组」按钮。
 */
@SideOnly(Side.CLIENT)
public class GuiDnaRecombinator extends GuiContainer {

    private static final int BTN_START = 1;
    private final TileEntityDnaRecombinator tile;
    private GuiButton startButton;

    public GuiDnaRecombinator(InventoryPlayer playerInv, TileEntityDnaRecombinator tile) {
        super(new ContainerDnaRecombinator(playerInv, tile));
        this.tile = tile;
        this.xSize = 176;
        this.ySize = 184;
    }

    @Override
    public void initGui() {
        super.initGui();
        this.buttonList.clear();
        this.startButton = addButton(new GuiButton(
                BTN_START, guiLeft + 74, guiTop + 74, 94, 18,
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
        Gui.drawRect(x + 5, y + 98, x + xSize - 5, y + ySize - 5, 0xFF222636);

        // FE 能量柱（x+8..18, y+22..86）
        int barX = x + 8;
        int barY = y + 22;
        int barW = 10;
        int barH = 64;
        Gui.drawRect(barX - 1, barY - 1, barX + barW + 1, barY + barH + 1, 0xFF12141E);
        Gui.drawRect(barX, barY, barX + barW, barY + barH, 0xFF1B2030);
        int maxE = Math.max(1, tile.getMaxEnergyStored());
        int fillH = (int) ((long) tile.getEnergyStored() * barH / maxE);
        if (fillH > 0) {
            Gui.drawRect(barX, barY + barH - fillH, barX + barW, barY + barH, 0xFF56D8E8);
        }

        // 绘制棉签槽 + 4 原料槽框
        drawSlotBox(x + 34, y + 24, 0xFF6B5A99);
        drawSlotBox(x + 25, y + 50, 0xFF47506B);
        drawSlotBox(x + 43, y + 50, 0xFF47506B);
        drawSlotBox(x + 25, y + 68, 0xFF47506B);
        drawSlotBox(x + 43, y + 68, 0xFF47506B);

        // 绘制玩家背包槽框
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlotBox(x + 8 + col * 18, y + 102 + row * 18, 0xFF3A4056);
            }
        }
        for (int col = 0; col < 9; col++) {
            drawSlotBox(x + 8 + col * 18, y + 160, 0xFF3A4056);
        }

        // 右侧信息面板底板
        Gui.drawRect(x + 70, y + 20, x + 170, y + 71, 0xFF1F2333);
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
        fontRenderer.drawString(feText, 122, 4, 0x7CE8F5);

        int infoX = 73;
        int infoY = 23;
        DnaRecipe recipe = tile.getActiveRecipe();
        if (recipe == null) {
            fontRenderer.drawSplitString(I18n.format("ul.dna.gui.insert_swab"), infoX, infoY, 94, 0xA0A8C0);
            return;
        }

        ItemStack swab = tile.getInventory().getStackInSlot(TileEntityDnaRecombinator.SLOT_SWAB);
        String name = swab.hasTagCompound() ? swab.getTagCompound().getString(ItemDnaSwab.NBT_ENTITY_NAME) : "";
        if (name.isEmpty()) {
            name = recipe.entityId;
        }
        fontRenderer.drawString(fontRenderer.trimStringToWidth(name, 94), infoX, infoY, 0xFFD98A);
        infoY += 10;

        for (DnaRecipe.ItemRequirement req : recipe.required) {
            int have = tile.countMatching(req);
            boolean ok = have >= req.count;
            String line = (ok ? "§a✔ " : "§c✘ ") + req.displayName() + " " + Math.min(have, req.count) + "/" + req.count;
            fontRenderer.drawString(fontRenderer.trimStringToWidth(line, 94), infoX, infoY, 0xFFFFFF);
            infoY += 10;
        }
        for (DnaRecipe.OptionalModifier opt : recipe.optional) {
            boolean hasOpt = tile.countMatching(opt.requirement) >= opt.requirement.count;
            String key = hasOpt ? opt.presentHintKey : opt.missingHintKey;
            String hint = key != null && !key.isEmpty()
                    ? I18n.format(key)
                    : (opt.requirement.displayName() + " x" + opt.requirement.count);
            fontRenderer.drawString(fontRenderer.trimStringToWidth(hint, 94), infoX, infoY,
                    hasOpt ? 0x8CF0B4 : 0xE8C87A);
            infoY += 10;
        }
    }
}
