package varmite.verity.triggers;

import net.minecraft.advancements.CriteriaTriggers;

public class ModTriggers {
   public static final OpenBoxTrigger UNBOX_VERITY_TRIGGER = (OpenBoxTrigger)CriteriaTriggers.register(new OpenBoxTrigger());
   public static final TalkTrigger TALK_TRIGGER = (TalkTrigger)CriteriaTriggers.register(new TalkTrigger());
   public static final VillageTrigger VILLAGE_TRIGGER = (VillageTrigger)CriteriaTriggers.register(new VillageTrigger());
   public static final KarmaChangeTrigger KARMA_CHANGE_TRIGGER = (KarmaChangeTrigger)CriteriaTriggers.register(new KarmaChangeTrigger());
   public static final GoodKarmaTrigger GOOD_KARMA_TRIGGER = (GoodKarmaTrigger)CriteriaTriggers.register(new GoodKarmaTrigger());
   public static final BadKarmaTrigger BAD_KARMA_TRIGGER = (BadKarmaTrigger)CriteriaTriggers.register(new BadKarmaTrigger());
   public static final PlaySoundTrigger PLAY_SOUND_TRIGGER = (PlaySoundTrigger)CriteriaTriggers.register(new PlaySoundTrigger());
   public static final BadDayTrigger BAD_DAY_TRIGGER = (BadDayTrigger)CriteriaTriggers.register(new BadDayTrigger());
   public static final ThreeDaysTrigger THREE_DAYS_TRIGGER = (ThreeDaysTrigger)CriteriaTriggers.register(new ThreeDaysTrigger());
}
