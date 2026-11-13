package varmite.verity;

import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionEventListener;
import dev.isxander.yacl3.api.StateManager;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.ConfigCategory.Builder;
import dev.isxander.yacl3.api.OptionEventListener.Event;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerFieldControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.StringControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import dev.isxander.yacl3.gui.YACLScreen;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import varmite.verity.client.VerityPreviewTexture;
import varmite.verity.event.ModBusClientSetup;
import varmite.verity.types.AiModelPreset;
import varmite.verity.types.AiProvider;
import varmite.verity.types.STTProvider;
import varmite.verity.types.TTSLanguage;
import varmite.verity.types.TTSProvider;
import varmite.verity.types.VerityVoice;

@EventBusSubscriber(modid = "verity", bus = Bus.MOD, value = Dist.CLIENT)
public class VerityConfigUI {
   @SubscribeEvent
   public static void onClientSetup(FMLClientSetupEvent event) {
      IEventBus forgeBus = MinecraftForge.EVENT_BUS;
      forgeBus.addListener(ModBusClientSetup::registerRenderers);
      forgeBus.addListener(ModBusClientSetup::onModifyBakingResult);
      MinecraftForge.registerConfigScreen((minecraft, previousScreen) -> createYACLScreen(previousScreen));
   }

   private static VerityVoice readVoice() {
      String configured = VerityConfig.VOICE.get();
      if (configured != null && !configured.isBlank()) {
         try {
            return VerityVoice.valueOf(configured.toUpperCase(Locale.ROOT));
         } catch (IllegalArgumentException e) {
            return VerityVoice.DANIEL;
         }
      } else {
         return VerityVoice.DANIEL;
      }
   }

   public static Screen createYACLScreen(Screen previousScreen) {
      return createYACLScreen(previousScreen, false);
   }

   private static <T> OptionEventListener<T> refreshAiSettings(Screen previousScreen) {
      return (option, event) -> {
         if (event == Event.STATE_CHANGE) {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.execute(() -> {
               if (minecraft.screen instanceof YACLScreen screen && screen.pendingChanges()) {
                  screen.finishOrSave();
               }

               minecraft.setScreen(createYACLScreen(previousScreen, true));
            });
         }
      };
   }

   private static ConfigCategory createAiCategory(Screen previousScreen) {
      AiProvider provider = VerityConfig.AI_PROVIDER.get();
      boolean customModel = provider.requiresCustomModel() || VerityConfig.USE_CUSTOM_AI_MODEL.get();
      Builder category = ConfigCategory.createBuilder()
         .name(Component.literal("AI Settings"))
         .option(
            Option.<AiProvider>createBuilder()
               .name(Component.literal("AI Provider"))
               .description(OptionDescription.of(Component.literal("The provider used to power the AI.")))
               .stateManager(StateManager.createInstant(AiProvider.OPENAI, VerityConfig.AI_PROVIDER, VerityConfig.AI_PROVIDER::set))
               .controller(opt -> EnumControllerBuilder.create(opt).enumClass(AiProvider.class))
               .addListener(refreshAiSettings(previousScreen))
               .build()
         )
         .option(
            Option.<String>createBuilder()
               .name(Component.literal("API Key"))
               .description(OptionDescription.of(Component.literal("API Key of the provider.\nOnly needed if the provider requests one.")))
               .binding("", VerityConfig.API_KEY, VerityConfig.API_KEY::set)
               .controller(StringControllerBuilder::create)
               .build()
         );
      if (!provider.requiresCustomModel()) {
         category.option(
            Option.<Boolean>createBuilder()
               .name(Component.literal("Use Custom Model"))
               .description(OptionDescription.of(Component.literal("Enter a model ID instead of using the Smart or Fast preset.")))
               .stateManager(StateManager.createInstant(false, VerityConfig.USE_CUSTOM_AI_MODEL, VerityConfig.USE_CUSTOM_AI_MODEL::set))
               .controller(TickBoxControllerBuilder::create)
               .addListener(refreshAiSettings(previousScreen))
               .build()
         );
      }

      if (customModel) {
         category.option(
            Option.<String>createBuilder()
               .name(Component.literal(provider.requiresCustomModel() ? "Ollama Model" : "Custom AI Model"))
               .description(OptionDescription.of(Component.literal("Exact model ID to send to " + provider + ".")))
               .binding("", VerityConfig.AI_MODEL, VerityConfig.AI_MODEL::set)
               .controller(StringControllerBuilder::create)
               .build()
         );
      } else {
         category.option(
            Option.<AiModelPreset>createBuilder()
               .name(Component.literal("AI Model Preset"))
               .description(
                  preset -> OptionDescription.of(
                     Component.literal(
                        (preset == AiModelPreset.SMART ? "Higher-quality responses at a low price." : "Faster responses at the lowest practical price.")
                           + "\nModel: "
                           + preset.getModel(provider)
                     )
                  )
               )
               .binding(AiModelPreset.SMART, VerityConfig.AI_MODEL_PRESET, VerityConfig.AI_MODEL_PRESET::set)
               .controller(
                  opt -> EnumControllerBuilder.create(opt)
                     .enumClass(AiModelPreset.class)
                     .valueFormatter(preset -> Component.literal(preset == AiModelPreset.SMART ? "Smart" : "Fast"))
               )
               .build()
         );
      }

      return category.option(
            Option.<Boolean>createBuilder()
               .name(Component.literal("Thinking Mode"))
               .description(OptionDescription.of(Component.literal("(On supported models)\nMakes the AI think longer before answering")))
               .binding(true, VerityConfig.AI_THINK, VerityConfig.AI_THINK::set)
               .controller(TickBoxControllerBuilder::create)
               .build()
         )
         .build();
   }

   private static Screen createYACLScreen(Screen previousScreen, boolean focusAiSettings) {
      return YetAnotherConfigLib.createBuilder()
         .title(Component.literal("Verity Configuration"))
         .category(
            ConfigCategory.createBuilder()
               .name(Component.literal("General"))
               .option(
                  Option.<Boolean>createBuilder()
                     .name(Component.literal("Can Crash"))
                     .description(OptionDescription.of(Component.literal("Allow Verity to kick you from the server.")))
                     .binding(true, VerityConfig.CAN_CRASH, VerityConfig.CAN_CRASH::set)
                     .controller(TickBoxControllerBuilder::create)
                     .build()
               )
               .option(
                  Option.<Boolean>createBuilder()
                     .name(Component.literal("Play Video"))
                     .description(OptionDescription.of(Component.literal("Play the Verity Edit on the\nstartup of the client.")))
                     .binding(true, VerityConfig.PLAY_VIDEO, VerityConfig.PLAY_VIDEO::set)
                     .controller(TickBoxControllerBuilder::create)
                     .build()
               )
               .option(
                  Option.<Boolean>createBuilder()
                     .name(Component.literal("Require Verity"))
                     .description(OptionDescription.of(Component.literal("Require 'Verity' in every\nsentence to speak to him.")))
                     .binding(false, VerityConfig.REQUIRE_VERITY, VerityConfig.REQUIRE_VERITY::set)
                     .controller(TickBoxControllerBuilder::create)
                     .build()
               )
               .option(
                  Option.<Boolean>createBuilder()
                     .name(Component.literal("True Darkness"))
                     .description(OptionDescription.of(Component.literal("Toggle the extreme darkness.")))
                     .binding(true, VerityConfig.TRUE_DARKNESS, VerityConfig.TRUE_DARKNESS::set)
                     .controller(TickBoxControllerBuilder::create)
                     .build()
               )
               .option(
                  Option.<Boolean>createBuilder()
                     .name(Component.literal("Kill Entities"))
                     .description(OptionDescription.of(Component.literal("Toggles if Verity can kill entities.")))
                     .binding(true, VerityConfig.KILL_ENTITIES, VerityConfig.KILL_ENTITIES::set)
                     .controller(TickBoxControllerBuilder::create)
                     .build()
               )
               .option(
                  Option.<Boolean>createBuilder()
                     .name(Component.literal("Kill Wildlife"))
                     .description(OptionDescription.of(Component.literal("Toggles if Verity should kill randomly wildlife.\nPigs, sheep, etc")))
                     .binding(true, VerityConfig.KILL_WILDLIFE, VerityConfig.KILL_WILDLIFE::set)
                     .controller(TickBoxControllerBuilder::create)
                     .build()
               )
               .option(
                  Option.<Boolean>createBuilder()
                     .name(Component.literal("Kill Villagers"))
                     .description(OptionDescription.of(Component.literal("Toggles if Verity should kill villagers.")))
                     .binding(true, VerityConfig.KILL_VILLAGERS, VerityConfig.KILL_VILLAGERS::set)
                     .controller(TickBoxControllerBuilder::create)
                     .build()
               )
               .option(
                  Option.<Boolean>createBuilder()
                     .name(Component.literal("Immersive Mode"))
                     .description(OptionDescription.of(Component.literal("Disables mod specific UIs to resemble the original series.")))
                     .binding(false, VerityConfig.IMMERSIVE_MODE, VerityConfig.IMMERSIVE_MODE::set)
                     .controller(TickBoxControllerBuilder::create)
                     .build()
               )
               .option(
                  Option.<Integer>createBuilder()
                     .name(Component.literal("Hostile Day Threshold"))
                     .description(OptionDescription.of(Component.literal("The amount of in-game days before\nVerity turns into a demon.")))
                     .binding(5, VerityConfig.DAY_COUNT, VerityConfig.DAY_COUNT::set)
                     .controller(opt -> IntegerFieldControllerBuilder.create(opt).min(1).max(100))
                     .build()
               )
               .build()
         )
         .category(createAiCategory(previousScreen))
         .category(
            ConfigCategory.createBuilder()
               .name(Component.literal("Voice Settings"))
               .option(
                  Option.<Boolean>createBuilder()
                     .name(Component.literal("Use Text To Speech (TTS)"))
                     .description(OptionDescription.of(Component.literal("Whenever Verity speaks using Text To Speech")))
                     .binding(true, VerityConfig.USE_TTS, VerityConfig.USE_TTS::set)
                     .controller(TickBoxControllerBuilder::create)
                     .build()
               )
               .option(
                  Option.<TTSProvider>createBuilder()
                     .name(Component.literal("Text To Speech Provider"))
                     .description(OptionDescription.of(Component.literal("Provider to use for Text To Speech")))
                     .binding(TTSProvider.LOCAL, VerityConfig.TTS_PROVIDER, VerityConfig.TTS_PROVIDER::set)
                     .controller(opt -> EnumControllerBuilder.create(opt).enumClass(TTSProvider.class))
                     .build()
               )
               .option(
                  Option.<VerityVoice>createBuilder()
                     .name(Component.literal("Voice"))
                     .description(OptionDescription.of(Component.literal("Choose the voice Verity has (GROQ, Local)")))
                     .binding(VerityVoice.DANIEL, VerityConfigUI::readVoice, newValue -> VerityConfig.VOICE.set(newValue.name()))
                     .controller(opt -> EnumControllerBuilder.create(opt).enumClass(VerityVoice.class))
                     .build()
               )
               .option(
                  Option.<String>createBuilder()
                     .name(Component.literal("TTS API Key"))
                     .description(OptionDescription.of(Component.literal("Your TTS API Key (Cartesia)")))
                     .binding("", VerityConfig.TTS_API_KEY, VerityConfig.TTS_API_KEY::set)
                     .controller(StringControllerBuilder::create)
                     .build()
               )
               .option(
                  Option.<TTSLanguage>createBuilder()
                     .name(Component.literal("TTS Language"))
                     .description(OptionDescription.of(Component.literal("The language Verity speaks (Cartesia)")))
                     .binding(TTSLanguage.English, () -> TTSLanguage.valueOf(VerityConfig.TTS_LANGUAGE.get().name()), VerityConfig.TTS_LANGUAGE::set)
                     .controller(opt -> EnumControllerBuilder.create(opt).enumClass(TTSLanguage.class))
                     .build()
               )
               .option(
                  Option.<String>createBuilder()
                     .name(Component.literal("Advanced TTS Voice"))
                     .description(OptionDescription.of(Component.literal("The voice the selected TTS Provider uses (Cartesia Voice ID, Kokoro)")))
                     .binding("am_fenrir", VerityConfig.ENDPOINT_VOICE, VerityConfig.ENDPOINT_VOICE::set)
                     .controller(StringControllerBuilder::create)
                     .build()
               )
               .build()
         )
         .category(
            ConfigCategory.createBuilder()
               .name(Component.literal("Speech Recognition"))
               .option(
                  Option.<STTProvider>createBuilder()
                     .name(Component.literal("Recognition Provider"))
                     .description(OptionDescription.of(Component.literal("Provider to use for Text To Speech")))
                     .binding(STTProvider.NATIVE, VerityConfig.STT_PROVIDER, VerityConfig.STT_PROVIDER::set)
                     .controller(opt -> EnumControllerBuilder.create(opt).enumClass(STTProvider.class))
                     .build()
               )
               .option(
                  Option.<String>createBuilder()
                     .name(Component.literal("Groq API Key"))
                     .description(OptionDescription.of(Component.literal("API Key for the groq Speech Recognition")))
                     .binding("", VerityConfig.GROQ_KEY, VerityConfig.GROQ_KEY::set)
                     .controller(StringControllerBuilder::create)
                     .build()
               )
               .build()
         )
         .category(
            ConfigCategory.createBuilder()
               .name(Component.literal("Personalisation"))
               .option(
                  Option.<Integer>createBuilder()
                     .name(Component.literal("Custom Color [HUE]"))
                     .description(
                        OptionDescription.createBuilder()
                           .text(Component.literal("0 = Original Colors. 1-360 = Hue Tint."))
                           .image(VerityPreviewTexture.id(), 100, 100)
                           .build()
                     )
                     .binding(0, VerityConfig.COLOR, VerityConfig.COLOR::set)
                     .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(0, 360).step(1).formatValue(value -> {
                        VerityPreviewTexture.applyHue(value);
                        if (value == 0) {
                           return Component.literal("0 (Default)");
                        }

                        int rgb = Mth.hsvToRgb(value.intValue() / 360.0F, 1.0F, 1.0F);
                        return Component.literal("██ " + value).withStyle(Style.EMPTY.withColor(rgb));
                     }))
                     .build()
               )
               .option(
                  Option.<String>createBuilder()
                     .name(Component.literal("Custom Name"))
                     .description(OptionDescription.of(Component.literal("Rename Verity to anything you want!")))
                     .binding("Verity", VerityConfig.VERITY_CUSTOM_NAME, VerityConfig.VERITY_CUSTOM_NAME::set)
                     .controller(StringControllerBuilder::create)
                     .build()
               )
               .option(
                  Option.<String>createBuilder()
                     .name(Component.literal("Personality"))
                     .description(OptionDescription.of(Component.literal("Can conflict when changed multiple\ntimes in one world")))
                     .binding("", VerityConfig.PERSONALITY, VerityConfig.PERSONALITY::set)
                     .controller(StringControllerBuilder::create)
                     .build()
               )
               .build()
         )
         .category(
            ConfigCategory.createBuilder()
               .name(Component.literal("Advanced"))
               .option(
                  Option.<Boolean>createBuilder()
                     .name(Component.literal("Developer Mode"))
                     .description(OptionDescription.of(Component.literal("Turns on developer mode (more logs)")))
                     .binding(false, VerityConfig.DEV_MODE, VerityConfig.DEV_MODE::set)
                     .controller(TickBoxControllerBuilder::create)
                     .build()
               )
               .option(
                  Option.<String>createBuilder()
                     .name(Component.literal("AI URL"))
                     .description(OptionDescription.of(Component.literal("Custom URL for dedicated AI servers")))
                     .binding("", VerityConfig.AI_ENDPOINT, VerityConfig.AI_ENDPOINT::set)
                     .controller(StringControllerBuilder::create)
                     .build()
               )
               .option(
                  Option.<String>createBuilder()
                     .name(Component.literal("Text To Speech URL"))
                     .description(OptionDescription.of(Component.literal("Custom URL for dedicated TTS servers.")))
                     .binding("", VerityConfig.TTS_ENDPOINT, VerityConfig.TTS_ENDPOINT::set)
                     .controller(StringControllerBuilder::create)
                     .build()
               )
               .option(
                  Option.<String>createBuilder()
                     .name(Component.literal("Speech Recognition URL"))
                     .description(OptionDescription.of(Component.literal("Custom URL for dedicated SR servers.")))
                     .binding("", VerityConfig.STT_ENDPOINT, VerityConfig.STT_ENDPOINT::set)
                     .controller(StringControllerBuilder::create)
                     .build()
               )
               .option(
                  Option.<String>createBuilder()
                     .name(Component.literal("Kokoro Model"))
                     .description(OptionDescription.of(Component.literal("Model used by Kokoro.")))
                     .binding("", VerityConfig.KOKORO_MODEL, VerityConfig.KOKORO_MODEL::set)
                     .controller(StringControllerBuilder::create)
                     .build()
               )
               .build()
         )
         .screenInit(screen -> {
            if (focusAiSettings) {
               screen.tabNavigationBar.selectTab(1, false);
            }
         })
         .build()
         .generateScreen(previousScreen);
   }
}
