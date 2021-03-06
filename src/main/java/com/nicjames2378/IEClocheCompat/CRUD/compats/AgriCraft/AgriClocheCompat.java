package com.nicjames2378.IEClocheCompat.CRUD.compats.AgriCraft;

import blusunrize.immersiveengineering.api.tool.BelljarHandler;
import blusunrize.immersiveengineering.common.IEContent;
import blusunrize.immersiveengineering.common.items.ItemMaterial;
import com.infinityraider.agricraft.api.v1.AgriApi;
import com.infinityraider.agricraft.api.v1.plant.IAgriPlant;
import com.infinityraider.agricraft.api.v1.soil.IAgriSoil;
import com.nicjames2378.IEClocheCompat.Main;
import com.nicjames2378.IEClocheCompat.config.Configurator;
import com.nicjames2378.IEClocheCompat.utils.ConversionUtils;
import com.nicjames2378.IEClocheCompat.utils.MathUtils;
import com.nicjames2378.IEClocheCompat.utils.RenderUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockCrops;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.*;

@SuppressWarnings("unchecked")
public class AgriClocheCompat {
    static class SeedAgriItem {
        private Item itemSeed;
        private String agriSeed = null;

        private SeedAgriItem(ItemStack seed) {
            if (seed.hasTagCompound()) {
                NBTTagCompound nbt = seed.getTagCompound();
                if (nbt.hasKey("agri_seed")) {
                    agriSeed = nbt.getString("agri_seed");
                }
            }
            itemSeed = seed.getItem();
        }

        private String getAgriSeed() {
            return agriSeed;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (other == null || getClass() != other.getClass()) return false;
            SeedAgriItem that = (SeedAgriItem) other;
            return Objects.equals(itemSeed, that.itemSeed) &&
                    Objects.equals(agriSeed, that.agriSeed);
        }

        @Override
        public int hashCode() {
            return Objects.hash(itemSeed, agriSeed);
        }
    }

    static class MR {
        public byte method;
        public ItemStack stack;

        public MR(byte method, ItemStack stack) {
            this.method = method;
            this.stack = stack;
        }
    }

    private static ArrayList<String> filterList;
    private static HashMap<String, MR> manualRenders = new HashMap<>();

    //Map seed ItemStacks to their corresponding IAgriPlants to make things easier
    private static HashMap<SeedAgriItem, IAgriPlant> plantMap = new HashMap();
    private static HashMap<IAgriPlant, ItemStack[]> soilMap = new HashMap();
    private static HashMap<IAgriPlant, ItemStack[]> outputMap = new HashMap<>();
    //private static HashMap<IAgriPlant, IBlockState[]> renderMap = new HashMap<>();

    static class AgricraftCropHandler implements BelljarHandler.IPlantHandler {
        private HashSet<IAgriPlant> validSeeds = new HashSet<>();

        protected HashSet<IAgriPlant> getSeedSet() {
            return validSeeds;
        }

        @Override
        public boolean isValid(ItemStack seed) {
            IAgriPlant plant = null;

            if (plantMap.containsKey(new SeedAgriItem(seed))) {
                plant = plantMap.get(new SeedAgriItem(seed));
            }

            return getSeedSet().contains(plant);
        }

        @Override
        public boolean isCorrectSoil(ItemStack seed, ItemStack soil) {
            for (ItemStack reqSoil : soilMap.get(plantMap.get(new SeedAgriItem(seed)))) {
                if (ItemStack.areItemsEqual(reqSoil, soil)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public float getGrowthStep(ItemStack seed, ItemStack soil, float growth, TileEntity tile, float fertilizer, boolean render) {
            byte modifier = 0;
            if (seed.hasTagCompound()) {
                if (seed.getTagCompound().hasKey("agri_growth")) {
                    modifier = seed.getTagCompound().getByte("agri_growth");
                }
            }

            if (modifier == 0) { // If a seed somehow has a modifier below the lowest, make sure it doesn't negatively impact growth
                return (.003125f * (fertilizer + Configurator.statAgricraftStrengthMultiplier));
            }
            return (.003125f * (fertilizer + ((Configurator.statAgricraftStrengthMultiplier * modifier) - Configurator.statAgricraftStrengthMultiplier)));
        }

        @Override
        public ItemStack[] getOutput(ItemStack seed, ItemStack soil, TileEntity tile) {
            ArrayList<ItemStack> retVal = new ArrayList<>();

            if (Configurator.verboseDebugging) {
                Main.log.info("Output: " + seed + ":, " + seed.getTagCompound().toString());
            }

            int seedGain = (seed.getTagCompound() != null ? seed.getTagCompound().getByte("agri_gain") : 1); //if compound exists, get gain value. returns nbt value if it exists, 0 if it don't, and 1 if no compound is found
            seedGain = (seedGain <= 0 ? 1 : seedGain); //if 0 assigned above, make it a 1

            // Calculate Yields for each seed
            for (ItemStack outputStack : outputMap.get(plantMap.get(new SeedAgriItem(seed)))) {
                String seedNBTString = seed.getTagCompound().getString("agri_seed");
                if (seedNBTString.contains("vanilla:potato_plant")) { //if potato seeds
                    if (ItemStack.areItemsEqual(outputStack, new ItemStack(Items.POTATO))) { //if potato, use gains
                        retVal.add(new ItemStack(outputStack.getItem(), (int) Math.ceil(getGainYield(seedGain) * 0.7f), outputStack.getMetadata())); //Mutiplied to reduce amount. Might remove this.
                    } else if (ItemStack.areItemsEqual(outputStack, new ItemStack(Items.POISONOUS_POTATO))) { //if poison tater, use 1 in x chance
                        if (MathUtils.testChanceIn(1, 50)) {
                            retVal.add(new ItemStack(outputStack.getItem(), 1, outputStack.getMetadata())); //Copies vanilla's 2% chance for poisonous tater
                        }
                    }

                } else if (seedNBTString.contains("immersiveengineering")) { //if immersiveengineering hemp
                    if (outputStack.getItem() instanceof ItemMaterial) { //if is, use gains
                        retVal.add(new ItemStack(outputStack.getItem(), getGainYield(seedGain), outputStack.getMetadata()));
                    }

                } else { //if anything else
                    retVal.add(new ItemStack(outputStack.getItem(), getGainYield(seedGain), outputStack.getMetadata())); //Whatever output is, multiply it
                }
            }

            if (MathUtils.testChanceIn(1, Configurator.statAgricraftSeedDuplicationChance)) {
                retVal.add(seed.copy()); //Give chance for another seed
            }

            return retVal.toArray(new ItemStack[0]);
        }

        @SideOnly(Side.CLIENT)
        @Override
        public boolean overrideRender(ItemStack seed, ItemStack soil, float growth, TileEntity tile, BlockRendererDispatcher blockRenderer) {
            //TODO: FIX RENDERING

            IAgriPlant plant = plantMap.get(new SeedAgriItem(seed));
            if (testContains(plant.getId(), "immersiveengineering")) {
                currentState = new IBlockState[]{IEContent.blockCrop.getDefaultState()};
                return true;
            } else if (testContains(plant.getId(), "mysticalagriculture") || testContains(plant.getId(), "mysticalagradditions")) {
                currentState = new IBlockState[]{Block.getBlockFromName(plant.getId().replace("plant", "crop")).getDefaultState()};
                return false;
            } else if (manualRenders.containsKey(plant.getPlantName().toLowerCase())) {
                MR mr = manualRenders.get(plant.getPlantName().toLowerCase());
                switch (mr.method) {
                    case (byte) 0: {
                        currentState = BelljarHandler.cropHandler.getRenderedPlant(mr.stack, soil, growth, tile);
                        return true;
                    }
                    case (byte) 1: { //Bugged; Not used. TODO: Fix!!!
                        currentState = BelljarHandler.stemHandler.getRenderedPlant(mr.stack, soil, growth, tile);
                        return true;
                    }
                    case (byte) 2: { //Bugged; Not used.
                        currentState = BelljarHandler.stackingHandler.getRenderedPlant(mr.stack, soil, growth, tile);
                        return true;
                    }
                }
            } else {
                if (Configurator.cfgEnableExperiementalRenderer) {
                    try {
                        Tessellator tessellator = Tessellator.getInstance();
                        BlockPos blockPos = new BlockPos(0, 0, 0);

                        int index = (int) (MathUtils.roundToPortion(8f, growth) * 8);
                        ResourceLocation resourceLocation = new ResourceLocation("agricraft", "textures/" + plant.getPrimaryPlantTexture(index).toString().split(":")[1] + ".png");

//                        if (Minecraft.getMinecraft().world.getTotalWorldTime() % 12 == 1) {
//                            Main.log.info(MathUtils.roundToPortion(8f, growth) + "*8=" + MathUtils.roundToPortion(8f, growth) * 8 + "; " + index + "; growth=" + growth + "; " + resourceLocation.toString());
//                        }
                        RenderUtils.renderAgri(blockPos, resourceLocation, tessellator);

                        //renderLayer(blockPos, resourceLocation, tessellator);
                        return true;
                    } catch (Exception e) {
                        Main.log.error("ERROR RENDERING!! \n");
                        e.printStackTrace();
                    }
                }
            }

            currentState = new IBlockState[]{Blocks.ANVIL.getDefaultState()};
            return false;
        }

        private static IBlockState[] currentState;

        @Override
        @SideOnly(Side.CLIENT)
        public IBlockState[] getRenderedPlant(ItemStack seed, ItemStack soil, float growth, TileEntity tile) {
            //TODO: Clean this up?
            IBlockState[] states = currentState; //(renderMap.get(plantMap.get(new SeedAgriItem(seed))) == null ? new IBlockState[]{Blocks.ANVIL.getDefaultState()} : renderMap.get(plantMap.get(new SeedAgriItem(seed))));
            if (states != null) {
                IBlockState[] ret = new IBlockState[states.length];
                for (int i = 0; i < states.length; i++)
                    if (states[i] != null)
                        if (states[i].getBlock() instanceof BlockCrops) {
                            int max = ((BlockCrops) states[i].getBlock()).getMaxAge();
                            ret[i] = ((BlockCrops) states[i].getBlock()).withAge(Math.min(max, Math.round(max * growth)));
                        } else {
                            for (IProperty prop : states[i].getPropertyKeys())
                                if ("age".equals(prop.getName()) && prop instanceof PropertyInteger) {
                                    int max = 0;
                                    for (Integer allowed : ((PropertyInteger) prop).getAllowedValues())
                                        if (allowed != null && allowed > max)
                                            max = allowed;
                                    ret[i] = states[i].withProperty(prop, Math.min(max, Math.round(max * growth)));
                                }
                            if (ret[i] == null)
                                ret[i] = states[i];
                        }
                return ret;
            }
            return null;
        }

        public void register(IAgriPlant agriPlant) {
            ItemStack seed = agriPlant.getSeed();

            Main.log.info(String.format("Agricraft Compat - Registering IAgriPlant '%1$s' (%2$s)), with output '%3$s', with soil '%4$s'%5$s",
                    agriPlant.getPlantName(),
                    agriPlant.getId(),
                    ConversionUtils.ItemStackArrayToString(getPlantOutputs(agriPlant, true)),
                    ConversionUtils.ItemStackArrayToString(getPlantSoils(agriPlant, false)),
                    (Main.PROX == Main.SIDE.SERVER ? " " : agriPlant.getPrimaryPlantTexture(1))
            ));

            getSeedSet().add(agriPlant);
            plantMap.put(new SeedAgriItem(seed), agriPlant);
            soilMap.put(agriPlant, getPlantSoils(agriPlant, false));
            outputMap.put(agriPlant, getPlantOutputs(agriPlant, false));
            //renderMap.put(agriPlant, getPlantRender(agriPlant));
        }

        private int getGainYield(int gainValue) {
            /* Due to limitations in the AgriCraft API (or a lack of understanding it on my part),
                    crop yields will be determined by my own calculations... Lets call it a 'feature' :D
            */

            int normGain = Math.round(MathUtils.normalizeToRange(1, 10, 0, 2, gainValue));

            //Clamped to prevent possible issues with negative or very high values due to NBT exploits. Do note,
            //  raising this value in the config can allow for 'super' crops from pack rewards if desired
            return MathUtils.clampValue(1, Configurator.statAgricraftGainLimitHard, ((gainValue * Configurator.statAgricraftGainLimitMultiplier) + (MathUtils.getAmountToAddOrSubtract(normGain + 1, normGain) * Configurator.statAgricraftGainLimitMultiplier) * Configurator.statAgricraftGainLimitMultiplier));
        }
    }

    public static AgricraftCropHandler agricraftHandler = new AgricraftCropHandler() {
    };

    public static void registerHandler() {
        BelljarHandler.registerHandler(agricraftHandler);
    }

    public static void initialize() {
        filterList = new ArrayList<>(Arrays.asList(Configurator.statAgricraftList));

        manualRenders.put("Wheat".toLowerCase(), new MR((byte) 0, new ItemStack(Items.WHEAT_SEEDS)));
        manualRenders.put("Potato".toLowerCase(), new MR((byte) 0, new ItemStack(Items.POTATO)));
        manualRenders.put("Carrot".toLowerCase(), new MR((byte) 0, new ItemStack(Items.CARROT)));
        manualRenders.put("Beets".toLowerCase(), new MR((byte) 0, new ItemStack(Items.BEETROOT_SEEDS)));
        manualRenders.put("Nether Wart".toLowerCase(), new MR((byte) 0, new ItemStack(Items.NETHER_WART)));
        //manualRenders.put("Pumpkin".toLowerCase(), new MR((byte) 1, new ItemStack(Items.PUMPKIN_SEEDS)));
        //manualRenders.put("Melon".toLowerCase(), new MR((byte) 1, new ItemStack(Items.MELON_SEEDS)));
        //manualRenders.put("Sugarcane".toLowerCase(), new MR((byte) 2, new ItemStack(Items.REEDS)));
        //manualRenders.put("Cactus".toLowerCase(), new MR((byte) 2, new ItemStack(Blocks.CACTUS)));
        manualRenders.put("Red Mushroom".toLowerCase(), new MR((byte) 0, new ItemStack(Blocks.RED_MUSHROOM)));
        manualRenders.put("Brown Mushroom".toLowerCase(), new MR((byte) 0, new ItemStack(Blocks.BROWN_MUSHROOM)));

        for (IAgriPlant plant : AgriApi.getPlantRegistry().all()) {
            if (canRegister(plant)) {
                agricraftHandler.register(plant);
            }
        }
        Main.log.info("-------------------------------");
    }

    private static boolean testContains(String string, String contains) {
        return string.toLowerCase().contains(contains.toLowerCase());
    }

    private static ItemStack[] getPlantOutputs(IAgriPlant agriPlant, boolean showDebug) {
        ArrayList<ItemStack> outputs = new ArrayList<>();
        agriPlant.getPossibleProducts(outputs::add);

        if (showDebug && outputs.size() > 1) {
            Main.log.info("This plant has more than 1 output!");
        }
        return outputs.toArray(new ItemStack[0]);
    }

    private static ItemStack[] getPlantSoils(IAgriPlant agriPlant, boolean showDebug) {
        ArrayList<ItemStack> soils = new ArrayList<>();

        for (IAgriSoil soil : agriPlant.getGrowthRequirement().getSoils()) {
            if (showDebug) {
                Main.log.info("getPlantSoils Debug: " + soil.getName() + ", " + new ItemStack(Item.getByNameOrId(soil.getName())).getItem().getRegistryName());
            }

            Block soilBlock = Block.getBlockFromName(soil.getName()) != null ? Block.getBlockFromName(soil.getName()) : Blocks.COMMAND_BLOCK; //Try to get the stack from the name; Use unobtainable item that stands out if fails
            ItemStack soilStack = new ItemStack(soilBlock);
            ItemStack addMe = soilStack;
            switch (soil.getName().toLowerCase()) { //Special Exceptions from when the stack consists of certain items that don't register properly
                case "farmland": { //All farmland is wrong. If not a Special Exception, use dirt
                    NBTTagCompound tag = (agriPlant.getSeed().hasTagCompound() ? agriPlant.getSeed().getTagCompound() : new NBTTagCompound());
                    if (tag.hasKey("agri_seed") && tag.getString("agri_seed").contains("mysticalagradditions")) {
                        switch (tag.getString("agri_seed").split(":")[1]) {
                            case "tier_six_inferium_plant":
                                addMe = ConversionUtils.getItemStackFromStringClean("mysticalagradditions:storage");
                                break;
                            case "awakened_draconium_plant":
                                addMe = ConversionUtils.getItemStackFromStringClean("mysticalagradditions:special=4");
                                break;
                            case "nether_star_plant":
                                addMe = ConversionUtils.getItemStackFromStringClean("mysticalagradditions:special=0");
                                break;
                            case "dragon_egg_plant":
                                addMe = ConversionUtils.getItemStackFromStringClean("mysticalagradditions:special=1");
                                break;
                        }
                    } else { //Otherwise, assign dirt to replace the Farmland
                        addMe = new ItemStack(Blocks.DIRT);
                    }
                    break;
                }
                case "soul sand": { //Incorrectly registers an item. Manually setting fixes it.
                    addMe = new ItemStack(Blocks.SOUL_SAND);
                    break;
                }
            }
            soils.add(addMe);
        }
        return soils.toArray(new ItemStack[0]);
    }

    private static boolean canRegister(IAgriPlant agriPlant) {
        String agriValue = null;

        //Don't register weeds. They're dumb anyways, and why would you even want to? O.o
        if (testContains(agriPlant.getId(), "weed")) {
            return false;
        }

        try {
            agriValue = agriPlant.getSeed().getTagCompound().getString("agri_seed");
        } catch (Throwable e) {
            Main.log.error("AgriClocheCompat#canRegister: Seed does not have proper compound! Either a mod is incorrectly " +
                    "registering or Agricraft has changed! {" + agriPlant.getSeedName() + "}");
        }

        // If it's not in the proper format, reject it
        if (agriValue == null || agriValue.isEmpty() || agriValue.equals("")) {
            return false;
        }

        // If it's blacklisted, reject it
        if (Configurator.statAgricraftListType.equals("BLACK") && (filterList.contains(agriValue) || filterList.contains(agriValue.split(":")[0] + ":*"))) {
            Main.log.info("Agricraft Compat - Skipping blacklisted seed: " + agriPlant.getSeedName());
            Main.log.info("-------------------------------");
            return false;
        }

        // If it's NOT whitelisted, reject it
        if (Configurator.statAgricraftListType.equals("WHITE") && (!filterList.contains(agriValue) || !filterList.contains(agriValue.split(":")[0] + ":*"))) {
            Main.log.info("Agricraft Compat - Skipping non-whitelisted seed: " + agriPlant.getSeedName());
            Main.log.info("-------------------------------");
            return false;
        }

        // If it's somehow survived, keep it. It can be our pet :D
        return true;
    }
}