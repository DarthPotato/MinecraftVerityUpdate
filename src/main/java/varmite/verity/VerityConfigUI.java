package varmite.verity;

import net.minecraft.client.Minecraft;
import varmite.verity.client.gui.OllamaSetupScreen;
import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.DoubleSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerFieldControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.StringControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import java.util.Locale;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import varmite.verity.client.VerityPreviewTexture;
import varmite.verity.types.HostileEvent;
import varmite.verity.types.STTProvider;
import varmite.verity.types.TTSLanguage;
import varmite.verity.types.TTSProvider;
import varmite.verity.types.VerityVoice;

@EventBusSubscriber(modid = "verity", value = Dist.CLIENT)
public class VerityConfigUI {
   @SubscribeEvent
   public static void onClientSetup(FMLClientSetupEvent event) {
      ModList.get()
         .getModContainerById("verity")
         .ifPresent(container -> container.registerExtensionPoint(IConfigScreenFactory.class, (modContainer, previousScreen) -> createYACLScreen(previousScreen)));
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
      return YetAnotherConfigLib.createBuilder()
         .title(Component.literal("Verity Configuration"))
         // Forge saved config edits automatically; NeoForge only writes the file when asked.
         .save(VerityConfig.SPEC::save)
         .category(
            ConfigCategory.createBuilder()
               .name(Component.literal("General"))
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
                     .binding(false, VerityConfig.TRUE_DARKNESS, VerityConfig.TRUE_DARKNESS::set)
                     .controller(TickBoxControllerBuilder::create)
                     .build()
               )
               .option(
                  Option.<Boolean>createBuilder()
                     .name(Component.literal("Verity Fog"))
                     .description(OptionDescription.of(Component.literal("Thicker, darker fog in the overworld.")))
                     .binding(false, VerityConfig.VERITY_FOG, VerityConfig.VERITY_FOG::set)
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
                     .description(OptionDescription.of(Component.literal("The amount of in-game days before\nVerity turns hostile.")))
                     .binding(5, VerityConfig.DAY_COUNT, VerityConfig.DAY_COUNT::set)
                     .controller(opt -> IntegerFieldControllerBuilder.create(opt).min(1).max(100))
                     .build()
               )
               .option(
                  Option.<HostileEvent>createBuilder()
                     .name(Component.literal("Hostile Event"))
                     .description(
                        OptionDescription.of(
                           Component.literal(
                              "What happens when Verity turns hostile. Runs once per world.\n"
                                 + "OFF: nothing, and no personality shift.\n"
                                 + "MESSAGE: a chat message stands in for it.\n"
                                 + "WITHER: a Wither spawns near you (it destroys blocks).\n"
                                 + "DEMON: Verity's demon form hunts you and breaks any block in its way."
                           )
                        )
                     )
                     .binding(HostileEvent.MESSAGE, VerityConfig.HOSTILE_EVENT, VerityConfig.HOSTILE_EVENT::set)
                     .controller(opt -> EnumControllerBuilder.create(opt).enumClass(HostileEvent.class))
                     .build()
               )
               .option(
                  Option.<Boolean>createBuilder()
                     .name(Component.literal("Can Crash"))
                     .description(OptionDescription.of(Component.literal("Kick a player from the server when Verity's demon kills them. Off by default.")))
                     .binding(false, VerityConfig.CAN_CRASH, VerityConfig.CAN_CRASH::set)
                     .controller(TickBoxControllerBuilder::create)
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
                  Option.<Boolean>createBuilder()
                     .name(Component.literal("Host Mode"))
                     .description(
                        OptionDescription.of(
                           Component.literal(
                              "When this game hosts the world (LAN or server), Verity's voice is made here once and streamed to\n"
                                 + "nearby players, so everyone hears the same voice. Their own voice settings are not used."
                           )
                        )
                     )
                     .binding(false, VerityConfig.HOST_MODE, VerityConfig.HOST_MODE::set)
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
                  Option.<String>createBuilder()
                     .name(Component.literal("fish.audio API Key"))
                     .description(OptionDescription.of(Component.literal("Your fish.audio API key (FISH_AUDIO provider)")))
                     .binding("", VerityConfig.FISH_API_KEY, VerityConfig.FISH_API_KEY::set)
                     .controller(StringControllerBuilder::create)
                     .build()
               )
               .option(
                  Option.<String>createBuilder()
                     .name(Component.literal("fish.audio Voice ID"))
                     .description(
                        OptionDescription.of(Component.literal("The voice model ID from fish.audio (FISH_AUDIO provider).\nEmpty uses Verity's voice."))
                     )
                     .binding("", VerityConfig.FISH_VOICE_ID, VerityConfig.FISH_VOICE_ID::set)
                     .controller(StringControllerBuilder::create)
                     .build()
               )
               .option(
                  Option.<String>createBuilder()
                     .name(Component.literal("fish.audio Model"))
                     .description(OptionDescription.of(Component.literal("s2.1-pro-free is the free tier.\nPaid: s2.1-pro, s2-pro, s1.")))
                     .binding("s2.1-pro-free", VerityConfig.FISH_MODEL, VerityConfig.FISH_MODEL::set)
                     .controller(StringControllerBuilder::create)
                     .build()
               )
               .option(
                  Option.<Double>createBuilder()
                     .name(Component.literal("fish.audio Speed"))
                     .description(OptionDescription.of(Component.literal("How fast Verity talks with fish.audio. 1.00 is normal.")))
                     .binding(1.0, VerityConfig.FISH_SPEED, VerityConfig.FISH_SPEED::set)
                     .controller(opt -> DoubleSliderControllerBuilder.create(opt).range(0.5, 2.0).step(0.05))
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
         .build()
         .generateScreen(previousScreen);
   }

   private static ConfigCategory createAiCategory(Screen previousScreen) {
      return ConfigCategory.createBuilder()
         .name(Component.literal("AI Settings"))
         .option(
            ButtonOption.createBuilder()
               .name(Component.literal("Local AI Setup (Ollama)"))
               .text(Component.literal("Open"))
               .description(
                  OptionDescription.of(
                     Component.literal(
                        "Run Verity's AI on this PC: free, private, no API key.\n\nInstalls Ollama and downloads a model with one click, and lists the Ollama models already on this computer. On a server, the AI runs on the server, so set Ollama up there instead."
                     )
                  )
               )
               .action((screen, option) -> {
                  if (screen.pendingChanges()) {
                     screen.finishOrSave();
                  }

                  Minecraft.getInstance().gui.setScreen(new OllamaSetupScreen(() -> createYACLScreen(previousScreen)));
               })
               .build()
         )
         .option(
            Option.<String>createBuilder()
               .name(Component.literal("Endpoint URL"))
               .description(
                  OptionDescription.of(
                     Component.literal("Base URL of any OpenAI-compatible chat API, including /v1.\nOllama: " + VerityConfig.DEFAULT_AI_ENDPOINT)
                  )
               )
               .binding(VerityConfig.DEFAULT_AI_ENDPOINT, VerityConfig.AI_ENDPOINT, VerityConfig.AI_ENDPOINT::set)
               .controller(StringControllerBuilder::create)
               .build()
         )
         .option(
            Option.<String>createBuilder()
               .name(Component.literal("API Key"))
               .description(OptionDescription.of(Component.literal("API key sent to the endpoint.\nOllama ignores it.")))
               .binding(VerityConfig.DEFAULT_API_KEY, VerityConfig.API_KEY, VerityConfig.API_KEY::set)
               .controller(StringControllerBuilder::create)
               .build()
         )
         .option(
            Option.<String>createBuilder()
               .name(Component.literal("Model"))
               .description(OptionDescription.of(Component.literal("Exact model ID to request from the endpoint.")))
               .binding(VerityConfig.DEFAULT_AI_MODEL, VerityConfig.AI_MODEL, VerityConfig.AI_MODEL::set)
               .controller(StringControllerBuilder::create)
               .build()
         )
         .option(
            Option.<Boolean>createBuilder()
               .name(Component.literal("Thinking Mode"))
               .description(OptionDescription.of(Component.literal("(On supported models)\nMakes the AI think longer before answering.\n\nLocal models: only applied to models that can think. qwen3 and qwen3.5 need it on to use their tools reliably; models without thinking are fast either way.")))
               .binding(true, VerityConfig.AI_THINK, VerityConfig.AI_THINK::set)
               .controller(TickBoxControllerBuilder::create)
               .build()
         )
         .build();
   }
}
