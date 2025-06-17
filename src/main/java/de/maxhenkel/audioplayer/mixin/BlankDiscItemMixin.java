package de.maxhenkel.audioplayer.mixin;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.tags.TagKey; // Added import
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Item.class)
public abstract class BlankDiscItemMixin {

    // Define the tag for blank discs
    private static final TagKey<Item> BLANK_DISCS_TAG = TagKey.create(net.minecraft.core.registries.Registries.ITEM, new ResourceLocation("blank_discs", "blank_discs")); // Created TagKey

    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    public void audioplayer_onUse(Level level, Player player, InteractionHand interactionHand, CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        ItemStack itemInHand = player.getItemInHand(interactionHand);

        // Check if the item in hand is a blank disc
        if (itemInHand.is(BLANK_DISCS_TAG)) { // Check if item is in the tag
            if (level.isClientSide) { // Only send message on client side
                Component message = Component.literal("Enter the URL and custom name for your disc: ")
                        .append(Component.literal("/audioplayer urlwithname <url> \"<custom_name>\"")
                                .withStyle(ChatFormatting.GRAY)
                                .withStyle(style -> style
                                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/audioplayer urlwithname "))
                                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to fill command"))
                                        )
                                )
                        )
                        .append(".");
                Minecraft.getInstance().player.sendSystemMessage(message); // Send message to player
            }
            cir.setReturnValue(InteractionResultHolder.consume(itemInHand)); // Consume the item use
        }
    }
}
