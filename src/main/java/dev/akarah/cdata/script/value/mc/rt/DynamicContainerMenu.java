package dev.akarah.cdata.script.value.mc.rt;

import dev.akarah.cdata.registry.Resources;
import dev.akarah.cdata.registry.item.CustomItem;
import dev.akarah.cdata.registry.item.ItemEvents;
import dev.akarah.cdata.script.value.event.REntityItemEvent;
import dev.akarah.cdata.script.value.mc.REntity;
import dev.akarah.cdata.script.value.mc.RItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;

public class DynamicContainerMenu extends ChestMenu {
    public DynamicContainerMenu(MenuType<?> menuType, int i, Inventory inventory, Container container, int j) {
        super(menuType, i, inventory, container, j);
    }

    @Override
    public void clicked(int slot, int j, ClickType clickType, Player player) {
        if(this.getContainer() instanceof DynamicContainer dynamicContainer) {
            var item = dynamicContainer.getItem(slot);
            var p = (ServerPlayer) player;
            CustomItem.itemOf(item).flatMap(CustomItem::events).flatMap(ItemEvents::onMenuClick)
                    .ifPresent(events -> Resources.actionManager().callEvents(
                            events,
                            REntityItemEvent.of(REntity.of(p), RItem.of(item))
                    ));
            if(dynamicContainer.cancelClicks()) {
                this.sendAllDataToRemote();
                return;
            }
        }
        super.clicked(slot, j, clickType, player);
    }
}
