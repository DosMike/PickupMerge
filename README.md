# PickupMerge
Convert items on pickup via Forges OreDict

** This is a SpongeForge plugin **

I think we all know the hassle with installing a lot of technical mods on our 
forge server. Like IC2, ThermalFoundation, BuildCraft, ... And we all know how 
may different copper ores, ingots, dusts, plates, and stuff may come along 
with those.

This Sponge Plugin tries to reduce the clutter by scanning through items and 
converting them into only one type. In order to archive this, this plugin hooks 
into the Forge OreDictionary that already aliases the ores to simplify crafting
and turning these items into a perferred variant.

So let's say we continue the example with copper ore. The next time you mine 
`IC2 Copper Ore` for example, this plugin will look up the OreDict entry 
`oreCopper` and, depending on the configuration, replace it with let's say 
`ThermalFoundation Copper Ore`, allowing you to stack them.

When you open a container of any sort the plugin will also scan through it and 
convert each item it has a perferred variant for so you can easily pick stacks 
of items from the container.

If you do not want items to convert automatically you can always turn it off 
with the disable commmand. - Note that this will only disable conversion until 
you disconnect to enable again.

But, you might scream now, what if a mod does not use the OreDict and requires 
it's special type of copper ore? Of course we thought about this problem and
introduced the convert command. Converting materials is as easy as possible: 
Just use the command `/PickupMerge <oreDictName> <oreVariant>`. The command has 
the aliases `/merge`, `/convert`, and `/ore` and both the OreDict-Names as well 
as the variants support auto-completion, so you won't have problems with typos.

With our example we could use `/convert oreCopper IC2` to convert the copper 
ore in our inventory back into `TC2 Copper Ore`. Note that this will also halt 
the automatic item conversion for you until you reconnect or enable it with 
`/convert auto` again

## TL;DR
Stuff you picked up will convert automatically acording to config.   
Commands (`/PickupMerge`, `/merge`, `/convert`, `/ore`):   
`/convert disable` - Stop converting ores for you   
`/convert auto` - Reenable conversion of ores for you   
`/convert <oreDict> <variant>` - manually convert ores in your inventory   
`/convert reload` - Reload the config   
Opening containers will convert the contents.

## Permissions
The permission system is supposed to filter out broken ores, but you can use it 
any other way you can think of.

`pickupmerge.command.plugin.reload` - Allows usage of `/convert reload`   
`pickupmerge.command.plugin.toggleauto` - Allows usage of `/convert disable` 
and `/convert auto`   
`pickupmerge.command.merge.<oreDict>` - Allows usage of 
`/convert <oreDict> ...` sub-commands   
`pickupmerge.merge.<oreDict>.<variant>` - Allows conversion for <oreDict> into 
<variant>    

To allow all conversions give permissions 
`pickupmerge.commmand.plugin.toggleauto`, 
`pickupmerge.command.merge.*`, 
`pickupmerge.merge.*`

## Config

#### preferred
The configuration has to entries, the first one being the perferred variant map.
OreDict entries are mapped to a mod's item using the format   
`  "oreDict": "modid:item[meta]"`    
`oreDict` is the OreDict name (e.g. oreCopper, ingotTin, dustGold, ...)   
`modid:item` is the item name as you know it (e.g. minecraft:gold_ingot, 
thermalfoundation:material, ...)   
`meta` is the meta value and is always required! (0)

An example would be `"blockCopper":  "thermalfoundation:storage[0]"`

#### cmdoreprefixwhitelist
The second set in the config is the list of oredict entries to scan through for 
the conversion command. If a ore dict entry does not start with a prefix in the 
list is will not be included in the scan.

A mod could for example register other vines which would then be listed in 
the OreDict as both vines, resulting in `/convert vines minecraft:vines` being 
possible.

The default config should provide a reasonable list of prefixes to pass thorugh.

<big>Note: This plugin will not be actively developed beyond this point. I will 
accept PullRequests tho, if they improve functionallity</big>