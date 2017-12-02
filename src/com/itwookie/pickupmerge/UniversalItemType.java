package com.itwookie.pickupmerge;

import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.spongepowered.api.data.DataContainer;
import org.spongepowered.api.data.DataQuery;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.item.ItemType;
import org.spongepowered.api.item.inventory.ItemStack;
import org.spongepowered.api.item.inventory.ItemStackSnapshot;
import org.spongepowered.common.item.inventory.util.ItemStackUtil;
import org.spongepowered.common.registry.type.ItemTypeRegistryModule;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.GameRegistry;

public class UniversalItemType {
	private static ItemTypeRegistryModule reg = ItemTypeRegistryModule.getInstance();
	private static Pattern validItem = Pattern.compile("([\\w:]+)\\[([0-9]+)\\]");
	
	private static DataQuery SpongeTypeAccessor = DataQuery.of("ItemType");
	private static DataQuery SpongeMetaAccessor = DataQuery.of("UnsafeDamage");
	
	private String modid="minecraft";
	public String getModId() { return modid; }
	private String id;
	public String getId() { return id.toLowerCase(); } 
	private int damage;
	public int getMeta() { return damage; }
	
	public UniversalItemType(String id, int meta) {
		this.id=id;
		if (id.indexOf(':')>0)
			modid = id.substring(0, id.indexOf(':'));
		damage=meta;
	}
	public UniversalItemType(org.spongepowered.api.item.inventory.ItemStack stack) {
		this.id=stack.getType().getId();
		if (id.indexOf(':')>0)
			modid = id.substring(0, id.indexOf(':'));
		damage= (int) stack.toContainer().get(SpongeMetaAccessor).orElse(0);
	}
	public UniversalItemType(net.minecraft.item.ItemStack item) {
		this(ItemStackUtil.fromNative(item));
	}
	
	public net.minecraft.item.ItemStack toForge() {
		return GameRegistry.makeItemStack(id, damage, 1, null);
	}
	public org.spongepowered.api.item.inventory.ItemStack toSponge() {
		return org.spongepowered.api.item.inventory.ItemStack.builder()
				.itemType(reg.getById(id).orElseThrow(()->{
					return new RuntimeException("No such item "+id);
				}))
				.add(Keys.ITEM_DURABILITY, damage)
				.build();
	}
	
	/** forces item-type and meta onto the Sponge ItemStack */
	public org.spongepowered.api.item.inventory.ItemStack force(org.spongepowered.api.item.inventory.ItemStack stack) {
		DataContainer cont = stack.toContainer();
		cont.set(SpongeTypeAccessor, id);
		cont.set(SpongeMetaAccessor, damage);
		return ItemStack.builder().fromContainer(cont).build();
	}
	/** forces item-type and meta onto the Sponge ItemStackSnapshot */
	public ItemStackSnapshot force(ItemStackSnapshot stack) {
		DataContainer cont = stack.toContainer();
		cont.set(SpongeTypeAccessor, id);
		cont.set(SpongeMetaAccessor, damage);
		return ItemStack.builder().fromContainer(cont).build().createSnapshot();
	}
	
	public ItemType toSpongeType() {
		return reg.getById(id).get();
	}
	public net.minecraft.item.Item toForgeType() {
		return GameRegistry.findRegistry(net.minecraft.item.Item.class).getValue(new ResourceLocation(id));
	}
	
	public String toString() {
		return String.format("%s[%d]", id, damage);
	}
	/** String is required to be formatted like modid:item[meta] */
	public static UniversalItemType fromString(String serial) {
		Matcher m = validItem.matcher(serial);
		try {
			if (!m.matches()) throw new Exception("Matcher did not match pattern"); 
			return new UniversalItemType(m.group(1), Integer.parseInt(m.group(2)));
		} catch (Exception e) {
			throw new RuntimeException("Invalid item identifier \""+serial+"\". Required format is modid:item[meta]", e);
		}
	}
	
	public boolean containedIn(Collection<net.minecraft.item.ItemStack> items) {
		net.minecraft.item.ItemStack compareable = toForge();
		for (net.minecraft.item.ItemStack item : items) {
			if (ItemStackUtil.compareIgnoreQuantity(item, compareable)) return true;
		}
		return false;
	}
	
	public boolean matches(org.spongepowered.api.item.inventory.ItemStack stack) {
		if (stack == null) return false;
		return id.equalsIgnoreCase(stack.getType().getId()) &&
				((int)stack.toContainer().get(SpongeMetaAccessor).orElse(0)) == damage;
	}
	
	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof UniversalItemType)) return false;
		UniversalItemType other = (UniversalItemType)obj;
		return this.modid == other.modid && this.damage == other.damage; 
	}
}
