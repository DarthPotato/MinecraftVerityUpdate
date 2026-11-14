package varmite.verity.environment.sounds;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModSounds {
   public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, "verity");
   public static final RegistryObject<SoundEvent> BOX_OPEN = registerSoundEvent("box_open");
   public static final RegistryObject<SoundEvent> BOX_CLICK = registerSoundEvent("box_click");
   public static final RegistryObject<SoundEvent> BOX_VERITY_0 = registerSoundEvent("box_verity_0");
   public static final RegistryObject<SoundEvent> BOX_VERITY_1 = registerSoundEvent("box_verity_1");
   public static final RegistryObject<SoundEvent> BOX_VERITY_2 = registerSoundEvent("box_verity_2");
   public static final RegistryObject<SoundEvent> INTRODUCTION = registerSoundEvent("intro");
   public static final RegistryObject<SoundEvent> IMPACT_0 = registerSoundEvent("impact_0");
   public static final RegistryObject<SoundEvent> IMPACT_1 = registerSoundEvent("impact_1");
   public static final RegistryObject<SoundEvent> IMPACT_2 = registerSoundEvent("impact_2");
   public static final RegistryObject<SoundEvent> INTRO_VIDEO_AUDIO = registerSoundEvent("intro_video_audio");
   public static final RegistryObject<SoundEvent> CHASE = registerSoundEvent("chase");
   public static final RegistryObject<SoundEvent> BONE_0 = registerSoundEvent("bone_snap");
   public static final RegistryObject<SoundEvent> BONE_1 = registerSoundEvent("bone_break");
   public static final RegistryObject<SoundEvent> JUMPSCARE = registerSoundEvent("jumpscare");

   private static RegistryObject<SoundEvent> registerSoundEvent(String name) {
      return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("verity", name)));
   }

   public static void register(IEventBus eventBus) {
      SOUND_EVENTS.register(eventBus);
   }
}
