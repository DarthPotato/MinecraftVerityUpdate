package varmite.verity.triggers;

import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModTriggers {
   public static final DeferredRegister<CriterionTrigger<?>> TRIGGERS = DeferredRegister.create(Registries.TRIGGER_TYPE, "verity");
   public static final DeferredHolder<CriterionTrigger<?>, OpenBoxTrigger> UNBOX_VERITY_TRIGGER = TRIGGERS.register(OpenBoxTrigger.ID, OpenBoxTrigger::new);
   public static final DeferredHolder<CriterionTrigger<?>, TalkTrigger> TALK_TRIGGER = TRIGGERS.register(TalkTrigger.ID, TalkTrigger::new);
   public static final DeferredHolder<CriterionTrigger<?>, VillageTrigger> VILLAGE_TRIGGER = TRIGGERS.register(VillageTrigger.ID, VillageTrigger::new);
   public static final DeferredHolder<CriterionTrigger<?>, KarmaChangeTrigger> KARMA_CHANGE_TRIGGER = TRIGGERS.register(KarmaChangeTrigger.ID, KarmaChangeTrigger::new);
   public static final DeferredHolder<CriterionTrigger<?>, GoodKarmaTrigger> GOOD_KARMA_TRIGGER = TRIGGERS.register(GoodKarmaTrigger.ID, GoodKarmaTrigger::new);
   public static final DeferredHolder<CriterionTrigger<?>, BadKarmaTrigger> BAD_KARMA_TRIGGER = TRIGGERS.register(BadKarmaTrigger.ID, BadKarmaTrigger::new);
   public static final DeferredHolder<CriterionTrigger<?>, FavoriteSongTrigger> FAVORITE_SONG_TRIGGER = TRIGGERS.register(FavoriteSongTrigger.ID, FavoriteSongTrigger::new);
   public static final DeferredHolder<CriterionTrigger<?>, PlaySoundTrigger> PLAY_SOUND_TRIGGER = TRIGGERS.register(PlaySoundTrigger.ID, PlaySoundTrigger::new);
   public static final DeferredHolder<CriterionTrigger<?>, BadDayTrigger> BAD_DAY_TRIGGER = TRIGGERS.register(BadDayTrigger.ID, BadDayTrigger::new);
   public static final DeferredHolder<CriterionTrigger<?>, ThreeDaysTrigger> THREE_DAYS_TRIGGER = TRIGGERS.register(ThreeDaysTrigger.ID, ThreeDaysTrigger::new);

   public static void register(IEventBus eventBus) {
      TRIGGERS.register(eventBus);
   }
}
