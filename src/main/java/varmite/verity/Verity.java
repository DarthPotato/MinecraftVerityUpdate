package varmite.verity;

import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig.Type;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.slf4j.Logger;
import varmite.verity.client.gui.PlayerKarmaProvider;
import varmite.verity.entity.ModEntities;
import varmite.verity.entity.llm.store.chat.ChatMemoryManager;
import varmite.verity.entity.llm.store.memory.VerityMemoryManager;
import varmite.verity.environment.block.ModBlocks;
import varmite.verity.environment.items.ModCreativeModeTabs;
import varmite.verity.environment.items.ModItems;
import varmite.verity.environment.sounds.ModSounds;
import varmite.verity.event.ModBusCommonSetup;
import varmite.verity.network.ModNetwork;
import varmite.verity.triggers.ModTriggers;

@Mod("verity")
public class Verity {
   public static final String MOD_ID = "verity";
   public static final Logger LOGGER = LogUtils.getLogger();

   public Verity(IEventBus modEventBus, ModContainer container) {
      // "common" configs are called LOCAL since 26.1; the file keeps its old name.
      container.registerConfig(Type.LOCAL, VerityConfig.SPEC, "verity-common.toml");
      ModNetwork.register(modEventBus);
      modEventBus.addListener(this::commonSetup);
      NeoForge.EVENT_BUS.register(this);
      ModCreativeModeTabs.register(modEventBus);
      ModEntities.register(modEventBus);
      ModItems.register(modEventBus);
      ModSounds.register(modEventBus);
      ModBlocks.register(modEventBus);
      ModTriggers.register(modEventBus);
      PlayerKarmaProvider.register(modEventBus);
      modEventBus.addListener(ModBusCommonSetup::registerAttributes);
   }

   private void commonSetup(FMLCommonSetupEvent event) {
      quietRetryLogging();
   }

   private static void quietRetryLogging() {
      if (!VerityConfig.DEV_MODE.get()) {
         try {
            ClassLoader owner = Class.forName("dev.langchain4j.internal.RetryUtils").getClassLoader();
            LoggerContext context = (LoggerContext)LogManager.getContext(owner, false);
            context.getLogger("dev.langchain4j.internal.RetryUtils").setLevel(Level.ERROR);
            context.updateLoggers();
         } catch (Throwable t) {
            LOGGER.debug("Couldn't quiet LangChain4j retry logging", t);
         }
      }
   }

   @SubscribeEvent
   public void onServerStopping(ServerStoppingEvent event) {
      try {
         VerityMemoryManager.get().save();
         ChatMemoryManager.getGlobalStorage().save();
      } catch (Exception e) {
         LOGGER.error("[Verity] Failed to save memory on shutdown", e);
      }
   }

   @SubscribeEvent
   public void onServerStarting(ServerStartingEvent event) {
      MinecraftServer Server = event.getServer();
      VerityMemoryManager.init(Server);
      ChatMemoryManager.init(event.getServer());
      System.out.println("Verity JE has loaded !");
   }
}
