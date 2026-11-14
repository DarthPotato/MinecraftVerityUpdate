package varmite.verity.environment.sounds;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;

public class ModSounds {
   public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(Registries.SOUND_EVENT, "verity");
   public static final DeferredHolder<SoundEvent, SoundEvent> BOX_OPEN = registerSoundEvent("box_open");
   public static final DeferredHolder<SoundEvent, SoundEvent> BOX_CLICK = registerSoundEvent("box_click");
   public static final DeferredHolder<SoundEvent, SoundEvent> BOX_VERITY_0 = registerSoundEvent("box_verity_0");
   public static final DeferredHolder<SoundEvent, SoundEvent> BOX_VERITY_1 = registerSoundEvent("box_verity_1");
   public static final DeferredHolder<SoundEvent, SoundEvent> BOX_VERITY_2 = registerSoundEvent("box_verity_2");
   public static final DeferredHolder<SoundEvent, SoundEvent> INTRODUCTION = registerSoundEvent("intro");
   public static final DeferredHolder<SoundEvent, SoundEvent> IMPACT_0 = registerSoundEvent("impact_0");
   public static final DeferredHolder<SoundEvent, SoundEvent> IMPACT_1 = registerSoundEvent("impact_1");
   public static final DeferredHolder<SoundEvent, SoundEvent> IMPACT_2 = registerSoundEvent("impact_2");
   public static final DeferredHolder<SoundEvent, SoundEvent> BONE_0 = registerSoundEvent("bone_snap");
   public static final DeferredHolder<SoundEvent, SoundEvent> BONE_1 = registerSoundEvent("bone_break");
   public static final DeferredHolder<SoundEvent, SoundEvent> CHASE = registerSoundEvent("chase");
   public static final DeferredHolder<SoundEvent, SoundEvent> JUMPSCARE = registerSoundEvent("jumpscare");
   public static final DeferredHolder<SoundEvent, SoundEvent> VERITY_DISC_SOUND = SOUND_EVENTS.register(
      "verity_disc", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("verity", "verity_disc"))
   );
   public static final DeferredHolder<SoundEvent, SoundEvent> VERITY_EDIT_DISC_SOUND = SOUND_EVENTS.register(
      "verity_edit_disc", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("verity", "verity_edit_disc"))
   );

   private static DeferredHolder<SoundEvent, SoundEvent> registerSoundEvent(String name) {
      return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("verity", name)));
   }

   public static void register(IEventBus eventBus) {
      SOUND_EVENTS.register(eventBus);
   }
}
