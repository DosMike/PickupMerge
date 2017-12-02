package com.itwookie.pickupmerge;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandException;
import org.spongepowered.api.command.CommandMapping;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.command.args.GenericArguments;
import org.spongepowered.api.command.spec.CommandExecutor;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.config.DefaultConfig;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.Order;
import org.spongepowered.api.event.game.GameReloadEvent;
import org.spongepowered.api.event.game.state.GamePostInitializationEvent;
import org.spongepowered.api.event.item.inventory.ChangeInventoryEvent;
import org.spongepowered.api.event.item.inventory.DropItemEvent;
import org.spongepowered.api.event.item.inventory.InteractInventoryEvent;
import org.spongepowered.api.event.network.ClientConnectionEvent;
import org.spongepowered.api.item.inventory.Inventory;
import org.spongepowered.api.item.inventory.ItemStackSnapshot;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.common.item.inventory.util.ItemStackUtil;

import com.google.inject.Inject;

import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.loader.ConfigurationLoader;

@Plugin(id="pickupmerge", name="Pickup Merge", authors={"DosMike"}, version="1.0", description="Merge Ores / Ore-Based Items on Pickup/Container travel")
public class PickupMerge implements CommandExecutor {
	
	private static Map<String, UniversalItemType> itemTypes = new HashMap<>(); //convertion map on pickup -> oreDictName to MinecraftItemId/Type
	private static Set<String> orePrefix = new HashSet<>(); //allowed ore types for ore dict conversions via command
	private static Map<String, Map<String,UniversalItemType>> cmdChoiceMap = new HashMap<>(); //this mapps the possible choices for ores and variants to make it easier to retrieve the actual uItemType later within the command
	private static Optional<CommandMapping> previousCommandMapping = Optional.empty();
	
	private static Set<UUID> tempOff = new HashSet<>();
	
	public static class MergeCommandExecutor implements CommandExecutor {
		private String oreDict;
		public MergeCommandExecutor(String ore) {
			oreDict=ore;
		}
		public String getOreDictEntry() {
			return oreDict;
		}
		@Override
		public CommandResult execute(CommandSource src, CommandContext args) throws CommandException {
			if (!(src instanceof Player)) {
				throw new CommandException(Text.of("Sorry, you don't have a inventory"));
			}
			
			Inventory inv = ((Player)src).getInventory();
			UniversalItemType target;
			Collection<UniversalItemType> source = new HashSet<>();
			Map<String, UniversalItemType> selection = cmdChoiceMap.get(oreDict);
			//target = selection.get(args.getOne("targetore").orElse(null));
			target = args.<UniversalItemType>getOne("targetore").orElse(null);
			selection.values().forEach(uit -> source.add(uit));
			
			if (!(src.hasPermission("pickupmerge.merge."+oreDict+"."+target.getModId()))) {
				throw new CommandException(Text.of("You are not allowed to convert "+oreDict+" into items from "+target.getModId())); 
			}
			
			for (UniversalItemType item : source) {		//for each possible variant
				for (Inventory slot : inv.slots()) {	//we check all slots
					org.spongepowered.api.item.inventory.ItemStack sitem = slot.peek().orElse(null);
					if (item.matches(sitem)) {			//that contain a variant
						if (sitem == null) continue;	//and if we can retrieve the item at this slot
						if (sitem.isEmpty()) continue;	//if the slot is empty we skip it
						slot.set(target.force(sitem));	//we force the target item type upon it
					}
				};
			}
			src.sendMessage(Text.of(TextColors.GREEN, "[PickupMerge] ", TextColors.RESET, "Converted all "+oreDict+" ItemStacks in your Inventory to "+target.getId()));
			
			if (tempOff.add(((Player)src).getUniqueId()))
				src.sendMessage(Text.of(TextColors.GREEN, "[PickupMerge] ", TextColors.GOLD, "Item conversion was disabled, use /PickupMerge auto to turn it back on"));
			return CommandResult.success();
		}
	}
	
//	private static PickupMerge instance; public static PickupMerge getInstance() { return instance; }
	
	@Listener
	public void onServerStart(GamePostInitializationEvent event) {
		// instance = this;
		loadConfig();
		rebuildCommand();
	}
	
	private void rebuildCommand() {
		previousCommandMapping.ifPresent(command->Sponge.getCommandManager().removeMapping(command));
		CommandSpec.Builder spec = CommandSpec.builder()
				.description(Text.of("Merge ores into one instance to save inventory space"))
				.executor(PickupMerge.this)
				.arguments(
					GenericArguments.firstParsing(
							GenericArguments.literal(Text.of("reload"), "reload"),
							GenericArguments.literal(Text.of("convertOn"), "auto"),
							GenericArguments.literal(Text.of("convertOff"), "disable")
					)
				);
		
		for (String ore : OreDictionary.getOreNames()) {
			boolean valid=false;
			for (String prefix : orePrefix) {
				if (ore.startsWith(prefix) && !ore.equals(prefix)) { valid=true; break; }
			}
			if (!valid) continue;
			
			Collection<net.minecraft.item.ItemStack> ao = OreDictionary.getOres(ore);
			Map<String,UniversalItemType> ct = new HashMap<>();
			ao.forEach(e->{
				UniversalItemType uit = new UniversalItemType(e);
				ct.put(uit.getModId(), uit); //so we only need to type the modID to get the item type (assuming each mod registeres a ore once)
				ct.put(uit.getId(), uit); //register a alias in case the mod does NOT register a ore once and you want a specific one
			});
			if (ct.isEmpty()) {
				//w ("No variants found for OreDict entry "+ore);
				continue;
			}
			if (ct.size()<=2) continue; //per mod we get 2 entries. if only one variant exists we get 2 entries, mergin is not possible in that case
			cmdChoiceMap.put(ore, ct);
			spec.child(CommandSpec.builder()
					.description(Text.of("Convert a '"+ore+"' to another mod"))
					.permission("pickupmerge.command.merge."+ore)
					.executor(new MergeCommandExecutor(ore))
					.arguments(
						GenericArguments.choices(Text.of("targetore"), ct)
					).build(), ore); //all register children for this ore
		}
		previousCommandMapping = Sponge.getCommandManager().register(this, spec.build(), "pickupmerge", "merge", "convert", "ore");
	}
	
	
	@Listener
	public void onReload(GameReloadEvent event) {
		loadConfig();
		rebuildCommand();
	}
	
	@Inject
	@DefaultConfig(sharedRoot = true)
	private ConfigurationLoader<CommentedConfigurationNode> configManager;
	
	private void loadConfig() {
		try {
			CommentedConfigurationNode root = configManager.load();	
			
			itemTypes.clear();
			orePrefix.clear();
			if (root.getNode("preferred").isVirtual()) {
				root.getNode("preferred").setComment("Values in this map should be oreDictEntry to modid:item[meta]");
				configManager.save(root);
			} else {
				root.getNode("preferred").getChildrenMap().forEach((K,V)->{
					try {
						itemTypes.put((String)K, UniversalItemType.fromString(V.getString()));
					} catch (Exception e) {
						e("%s", e.getMessage());
					}
				});
				root.getNode("cmdoreprefixwhitelist").getChildrenList().forEach(V->{
					orePrefix.add(V.getString());
				});
			}
			l("Loaded %d ores to replace on pickup and %d item types to convert via command", itemTypes.size(), orePrefix.size());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	
	
	public static void l(String format, Object... args) {
		Sponge.getServer().getBroadcastChannel().send(Text.of(TextColors.GREEN, "[PickupMerge] ", TextColors.RESET, String.format(format, args)));
	}
	public static void w(String format, Object... args) {
		Sponge.getServer().getBroadcastChannel().send(Text.of(TextColors.GREEN, "[PickupMerge] ", TextColors.GOLD, String.format(format, args)));
	}
	public static void e(String format, Object... args) {
		Sponge.getServer().getBroadcastChannel().send(Text.of(TextColors.GREEN, "[PickupMerge] ", TextColors.DARK_RED, String.format(format, args)));
	}
	
	private ItemStackSnapshot convert(ItemStackSnapshot original) {
		//Weak conversion from Sponge ItemStack to Forge ItemStack
		//We only care for the ItemType to get the OreDict entry
		
		ItemStack forge = ItemStackUtil.toNative(original.createStack());
		
		//get the oreDict IDs for this Type 
		int[] oreIDs = OreDictionary.getOreIDs(forge);
		if (oreIDs.length==0) return original; //this block type is no ore/oredict item
		
		//get the first ore name entry, for example: if an ore is oreMercury and oreQuicksilver this will return (unsafe) the first one to be registered. e.g. oreQuicksilver
		String ore = OreDictionary.getOreName(oreIDs[0]);
		
		//Look up target ItemType (the one we want to connvert to)
		UniversalItemType type = itemTypes.get(ore);
		if (type == null) return original; //we do not want to replace this one
		
		//retrieve all registered ItemTypes for this ore
		List<ItemStack> possibleItems = OreDictionary.getOres(ore, false);
		if (possibleItems.size()<=1) return original; //only one variant for this ore
		
		if (!type.containedIn(possibleItems)) {
			w("Converting %s to %s drops ore type %s", original.getType().getId(), type.toString(), ore);
		}
		
		return type.force(original);
	}
	private org.spongepowered.api.item.inventory.ItemStack convert(org.spongepowered.api.item.inventory.ItemStack original) {
		//Weak conversion from Sponge ItemStack to Forge ItemStack
		//We only care for the ItemType to get the OreDict entry
		
		net.minecraft.item.ItemStack forge = ItemStackUtil.toNative(original);
		
		//get the oreDict IDs for this Type 
		int[] oreIDs = OreDictionary.getOreIDs(forge);
		if (oreIDs.length==0) return original; //this block type is no ore/oredict item
		
		//get the first ore name entry, for example: if an ore is oreMercury and oreQuicksilver this will return (unsafe) the first one to be registered. e.g. oreQuicksilver
		String ore = OreDictionary.getOreName(oreIDs[0]);
		
		//Look up target ItemType (the one we want to connvert to)
		UniversalItemType type = itemTypes.get(ore);
		if (type == null) return original; //we do not want to replace this one
		
		//retrieve all registered ItemTypes for this ore
		List<net.minecraft.item.ItemStack> possibleItems = OreDictionary.getOres(ore, false);
		if (possibleItems.size()<=1) return original; //only one variant for this ore
		
		if (!type.containedIn(possibleItems)) {
			w("Converting %s to %s drops ore type %s", original.getType().getId(), type.toString(), ore);
		}
		
		return type.force(original);
	}
	
	/*@Listener(order = Order.LATE)
	public void onSpawnItemStack(ChangeBlockEvent.Break event) {
		event.getTransactions().forEach(trans->{
			trans.setCustom(convert(trans.getFinal()));
		});
	}*/
	@Listener(order = Order.LATE) //would work - i guess the implementation does not put the manupulated list back as block/player drops? 
	public void onSpawnItemStack(DropItemEvent.Pre event) {
		if (event.getSource() instanceof Player) {
			if (tempOff.contains(((Player)event.getSource()).getUniqueId())) return; //feature turned off
		}
		List<ItemStackSnapshot> drops = event.getDroppedItems();
		for (int i = 0; i < drops.size(); i++) {
			ItemStackSnapshot conv = convert(drops.get(i));
			event.getDroppedItems().set(i, conv);
		}
	}
	
	@Listener(order = Order.LATE)
	public void onPickupItem(ChangeInventoryEvent.Pickup event) {
		Player holder = event.getCause().first(Player.class).orElse(null);
		if (holder == null) {
			return;
		}
		if (tempOff.contains(holder.getUniqueId())) return; // player disabled this feature
		
		event.getTransactions().forEach((trans)->{
//			ItemStackSnapshot conv = convert(trans.getFinal());
//			l("Pick up: %s -> %s", trans.getFinal().getType().getId(), conv.getType().getId());
//			trans.setCustom(conv);
			trans.setCustom(convert(trans.getFinal()));
		});
	}
	
	@Listener(order = Order.LATE)
	public void onOpenInventory(InteractInventoryEvent.Open event) {
		event.getCause().first(Player.class).ifPresent(player->{
			if (tempOff.contains(player.getUniqueId())) return; //player turned off feature
			
			for (Inventory slot : event.getTargetInventory().slots()) {	//we check all slots
				org.spongepowered.api.item.inventory.ItemStack sitem = slot.peek().orElse(null);
				if (sitem == null) continue; //if the slot is empty we skip it
				if (sitem.isEmpty()) continue; //if the slot is empty we skip it
				slot.set(convert(sitem));	
			};
		});
	}
	
	@Listener
	public void onDisconnect(ClientConnectionEvent.Disconnect event) {
		tempOff.remove(event.getTargetEntity().getUniqueId());
	}
	
	@Override
	public CommandResult execute(CommandSource src, CommandContext args) throws CommandException {
		if (args.hasAny("reload")) {
			args.checkPermission(src, "pickupmerge.command.plugin.reload");
			loadConfig();
			rebuildCommand();
			return CommandResult.success();
		} else if (args.hasAny("convertOn")) {
			if (!(src instanceof Player)) throw new CommandException(Text.of("Only players can toggle auto conversion"));
			args.checkPermission(src, "pickupmerge.command.plugin.toggleauto");
			tempOff.remove(((Player)src).getUniqueId());
			src.sendMessage(Text.of(TextColors.GREEN, "[PickupMerge] ", TextColors.RESET, "Item will now automatically convert for you"));
			return CommandResult.success();
		} else if (args.hasAny("convertOff")) {
			if (!(src instanceof Player)) throw new CommandException(Text.of("Only players can toggle auto conversion"));
			args.checkPermission(src, "pickupmerge.command.plugin.toggleauto");
			if (tempOff.add(((Player)src).getUniqueId()))
				src.sendMessage(Text.of(TextColors.GREEN, "[PickupMerge] ", TextColors.RESET, "Item will no longer convert for you"));
			else
				src.sendMessage(Text.of(TextColors.GREEN, "[PickupMerge] ", TextColors.RESET, "Item conversion is turned off already, use /PickupMerge auto to turn it back on"));
			return CommandResult.success();
		} else throw new CommandException(Text.of("Unknown Error [Literal expectation failed]", true));
	}
	
}
