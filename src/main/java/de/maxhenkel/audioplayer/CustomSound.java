package de.maxhenkel.audioplayer;

import de.maxhenkel.configbuilder.entry.ConfigEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SkullBlock;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

public class CustomSound {

    public static final String CUSTOM_SOUND = "CustomSound";
    public static final String CUSTOM_SOUND_RANGE = "CustomSoundRange";
    public static final String CUSTOM_SOUND_STATIC = "IsStaticCustomSound";

    public static final String DEFAULT_HEAD_LORE = "Has custom audio";

    protected UUID soundId;
    @Nullable
    protected Float range;
    protected boolean staticSound;

    public CustomSound(UUID soundId, @Nullable Float range, boolean staticSound) {
        this.soundId = soundId;
        this.range = range;
        this.staticSound = staticSound;
    }

    @Nullable
    public static CustomSound of(ItemStack item) {
        CompoundTag tag = item.getTag();
        if (tag == null) {
            return null;
        }
        return of(tag);
    }

    @Nullable
    public static CustomSound of(CompoundTag tag) {
        UUID soundId;
        if (tag.contains(CUSTOM_SOUND)) {
            soundId = tag.getUUID(CUSTOM_SOUND);
        } else {
            return null;
        }
        Float range = null;
        if (tag.contains(CUSTOM_SOUND_RANGE)) {
            range = tag.getFloat(CUSTOM_SOUND_RANGE);
        }
        boolean staticSound = false;
        if (tag.contains(CUSTOM_SOUND_STATIC)) {
            staticSound = tag.getBoolean(CUSTOM_SOUND_STATIC);
        }
        return new CustomSound(soundId, range, staticSound);
    }

    public UUID getSoundId() {
        return soundId;
    }

    public Optional<Float> getRange() {
        return Optional.ofNullable(range);
    }

    public float getRange(PlayerType playerType) {
        return getRangeOrDefault(playerType.getDefaultRange(), playerType.getMaxRange());
    }

    public float getRangeOrDefault(ConfigEntry<Float> defaultRange, ConfigEntry<Float> maxRange) {
        if (range == null) {
            return defaultRange.get();
        } else if (range > maxRange.get()) {
            return maxRange.get();
        } else {
            return range;
        }
    }

    public boolean isStaticSound() {
        return staticSound;
    }

    public void saveToNbt(CompoundTag tag) {
        if (soundId != null) {
            tag.putUUID(CUSTOM_SOUND, soundId);
        } else {
            tag.remove(CUSTOM_SOUND);
        }
        if (range != null) {
            tag.putFloat(CUSTOM_SOUND_RANGE, range);
        } else {
            tag.remove(CUSTOM_SOUND_RANGE);
        }
        if (staticSound) {
            tag.putBoolean(CUSTOM_SOUND_STATIC, true);
        } else {
            tag.remove(CUSTOM_SOUND_STATIC);
        }
    }

    public void saveToItemIgnoreLore(ItemStack stack) {
        saveToItem(stack, null, null, false);
    }

    public void saveToItem(ItemStack stack) {
        saveToItem(stack, null, null, true);
    }

    public void saveToItem(ItemStack stack, @Nullable String customName) {
        saveToItem(stack, customName, null, true);
    }
    
    // New method to be called from your command to include the player's name
    public void saveToItem(ItemStack stack, @Nullable String customName, String playerName) {
        saveToItem(stack, customName, playerName, true);
    }

    private void saveToItem(ItemStack stack, @Nullable String customName, @Nullable String playerName, boolean applyLore) {
        CompoundTag tag = stack.getOrCreateTag();
        saveToNbt(tag);

        if (stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof SkullBlock) {
            CompoundTag blockEntityTag = stack.getOrCreateTagElement(BlockItem.BLOCK_ENTITY_TAG);
            saveToNbt(blockEntityTag);
        }
        
        if (!applyLore) {
            return;
        }

        // Get or create the main 'display' NBT tag
        CompoundTag display = stack.getOrCreateTagElement(ItemStack.TAG_DISPLAY);
        ListTag lore = new ListTag();

        // Set the custom name if one was provided
        if (customName != null) {
            display.putString(ItemStack.TAG_DISPLAY_NAME, Component.Serializer.toJson(Component.literal(customName).withStyle(ChatFormatting.RESET)));
        }

        // Generate the dynamic lore text
        String loreText;
        if (playerName != null) {
            loreText = "Custom music disc pressed by %s!".formatted(playerName);
        } else {
            loreText = DEFAULT_HEAD_LORE;
        }
        lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(loreText).withStyle(style -> style.withItalic(false)).withStyle(ChatFormatting.GRAY))));

        // Apply the lore list to the display tag
        display.put(ItemStack.TAG_LORE, lore);

        // Hide the internal NBT data from the tooltip
        tag.putInt("HideFlags", ItemStack.TooltipPart.ADDITIONAL.getMask());
    }

    public CustomSound asStatic(boolean staticSound) {
        return new CustomSound(soundId, range, staticSound);
    }

    public static boolean clearItem(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) {
            return false;
        }
        if (!tag.contains(CUSTOM_SOUND)) {
            return false;
        }
        tag.remove(CUSTOM_SOUND);
        tag.remove(CUSTOM_SOUND_RANGE);
        tag.remove(CUSTOM_SOUND_STATIC);
        if (stack.getItem() instanceof BlockItem) {
            CompoundTag blockEntityTag = stack.getOrCreateTagElement(BlockItem.BLOCK_ENTITY_TAG);
            blockEntityTag.remove(CUSTOM_SOUND);
            blockEntityTag.remove(CUSTOM_SOUND_RANGE);
            blockEntityTag.remove(CUSTOM_SOUND_STATIC);
        }
        return true;
    }

}
