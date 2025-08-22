package io.github.prismwork.emitrades.export;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import dev.emi.emi.EmiPort;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOffers;
import net.minecraft.village.VillagerProfession;
import net.minecraft.village.VillagerType;
import net.minecraft.world.poi.PointOfInterestTypes;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TradeExporter {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    public static class TradeData {
        public String professionId;
        public String professionName;
        public int level;
        public JsonObject input1;
        public JsonObject input2;
        public JsonObject output;
        public int maxUses;
        public int villagerXp;
        public float priceMultiplier;
        public String workstation;
        
        public TradeData(String profession, int level, TradeOffer offer) {
            this.professionId = profession;
            this.professionName = getProfessionDisplayName(profession);
            this.level = level;
            this.input1 = itemStackToJson(offer.getOriginalFirstBuyItem());
            this.input2 = itemStackToJson(offer.getSecondBuyItem());
            this.output = itemStackToJson(offer.getSellItem());
            this.maxUses = offer.getMaxUses();
            this.villagerXp = offer.getMerchantExperience();
            this.priceMultiplier = offer.getPriceMultiplier();
            this.workstation = "none";
        }
        
        public TradeData(VillagerProfession profession, int level, TradeOffer offer) {
            this.professionId = profession.id();
            this.professionName = getProfessionDisplayName(profession.id());
            this.level = level;
            this.input1 = itemStackToJson(offer.getOriginalFirstBuyItem());
            this.input2 = itemStackToJson(offer.getSecondBuyItem());
            this.output = itemStackToJson(offer.getSellItem());
            this.maxUses = offer.getMaxUses();
            this.villagerXp = offer.getMerchantExperience();
            this.priceMultiplier = offer.getPriceMultiplier();
            // Get the workstation for this profession
            this.workstation = getWorkstationForProfession(profession);
        }
        
        private static String getWorkstationForProfession(VillagerProfession profession) {
            // Map professions to their workstation blocks
            String profId = profession.id();
            if (profId.contains("armorer")) return "minecraft:blast_furnace";
            if (profId.contains("butcher")) return "minecraft:smoker";
            if (profId.contains("cartographer")) return "minecraft:cartography_table";
            if (profId.contains("cleric")) return "minecraft:brewing_stand";
            if (profId.contains("farmer")) return "minecraft:composter";
            if (profId.contains("fisherman")) return "minecraft:barrel";
            if (profId.contains("fletcher")) return "minecraft:fletching_table";
            if (profId.contains("leatherworker")) return "minecraft:cauldron";
            if (profId.contains("librarian")) return "minecraft:lectern";
            if (profId.contains("mason") || profId.contains("stone_mason")) return "minecraft:stonecutter";
            if (profId.contains("shepherd")) return "minecraft:loom";
            if (profId.contains("toolsmith")) return "minecraft:smithing_table";
            if (profId.contains("weaponsmith")) return "minecraft:grindstone";
            if (profId.contains("nitwit") || profId.contains("none")) return "none";
            return "unknown";
        }
        
        private static String getProfessionDisplayName(String professionId) {
            // Use the same translation system as EMITrades
            if (professionId.equals("wandering_trader")) {
                return EmiPort.translatable("emi.emitrades.placeholder.wandering_trader").getString();
            }
            String professionName = professionId.substring(professionId.lastIndexOf(":") + 1);
            return EmiPort.translatable("entity.minecraft.villager." + professionName).getString();
        }
        
        private static JsonObject itemStackToJson(ItemStack stack) {
            if (stack == null || stack.isEmpty()) {
                return null;
            }
            JsonObject obj = new JsonObject();
            obj.addProperty("id", Registries.ITEM.getId(stack.getItem()).toString());
            obj.addProperty("name", stack.getName().getString());
            obj.addProperty("count", stack.getCount());
            if (stack.hasNbt() && stack.getNbt() != null) {
                obj.addProperty("nbt", stack.getNbt().toString());
            }
            return obj;
        }
        
        public JsonObject toJson() {
            JsonObject obj = new JsonObject();
            obj.addProperty("professionId", professionId);
            obj.addProperty("professionName", professionName);
            obj.addProperty("level", level);
            if (input1 != null) obj.add("input1", input1);
            if (input2 != null) obj.add("input2", input2);
            if (output != null) obj.add("output", output);
            obj.addProperty("maxUses", maxUses);
            obj.addProperty("villagerXp", villagerXp);
            obj.addProperty("priceMultiplier", priceMultiplier);
            obj.addProperty("workstation", workstation);
            return obj;
        }
    }
    
    private final List<TradeData> allTrades = new ArrayList<>();
    private final Map<String, Map<Integer, List<TradeData>>> professionTrades = new HashMap<>();
    private int totalVillagerTrades = 0;
    private int totalWanderingTrades = 0;
    
    public void addTrade(VillagerProfession profession, int level, TradeOffer offer) {
        TradeData trade = new TradeData(profession, level, offer);
        allTrades.add(trade);
        
        String profId = profession.id();
        professionTrades.computeIfAbsent(profId, k -> new HashMap<>())
                        .computeIfAbsent(level, k -> new ArrayList<>())
                        .add(trade);
        totalVillagerTrades++;
    }
    
    public void addWanderingTrade(int level, TradeOffer offer) {
        TradeData trade = new TradeData("wandering_trader", level, offer);
        allTrades.add(trade);
        
        professionTrades.computeIfAbsent("wandering_trader", k -> new HashMap<>())
                        .computeIfAbsent(level, k -> new ArrayList<>())
                        .add(trade);
        totalWanderingTrades++;
    }
    
    public void exportToFile(File file) throws IOException {
        JsonObject root = new JsonObject();
        
        // Add metadata
        JsonObject metadata = new JsonObject();
        metadata.addProperty("version", "1.0.0");
        metadata.addProperty("exportDate", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        metadata.addProperty("totalTrades", allTrades.size());
        metadata.addProperty("totalVillagerTrades", totalVillagerTrades);
        metadata.addProperty("totalWanderingTrades", totalWanderingTrades);
        metadata.addProperty("totalProfessions", professionTrades.size());
        root.add("metadata", metadata);
        
        // Add trades organized by profession and level
        JsonObject tradesObj = new JsonObject();
        for (Map.Entry<String, Map<Integer, List<TradeData>>> profEntry : professionTrades.entrySet()) {
            JsonObject professionObj = new JsonObject();
            
            for (Map.Entry<Integer, List<TradeData>> levelEntry : profEntry.getValue().entrySet()) {
                JsonArray levelArray = new JsonArray();
                for (TradeData trade : levelEntry.getValue()) {
                    levelArray.add(trade.toJson());
                }
                professionObj.add("level_" + levelEntry.getKey(), levelArray);
            }
            
            tradesObj.add(profEntry.getKey(), professionObj);
        }
        root.add("trades", tradesObj);
        
        // Write to file
        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(root, writer);
        }
    }
}