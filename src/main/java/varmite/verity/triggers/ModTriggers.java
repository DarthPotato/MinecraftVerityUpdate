package varmite.verity.triggers;

import net.minecraft.advancements.CriteriaTriggers;

public class ModTriggers {
   public static final OpenBoxTrigger UNBOX_VERITY_TRIGGER = CriteriaTriggers.register(new OpenBoxTrigger());
   public static final TalkTrigger TALK_TRIGGER = CriteriaTriggers.register(new TalkTrigger());
   public static final VillageTrigger VILLAGE_TRIGGER = CriteriaTriggers.register(new VillageTrigger());
   public static final KarmaChangeTrigger KARMA_CHANGE_TRIGGER = CriteriaTriggers.register(new KarmaChangeTrigger());
   public static final GoodKarmaTrigger GOOD_KARMA_TRIGGER = CriteriaTriggers.register(new GoodKarmaTrigger());
   public static final BadKarmaTrigger BAD_KARMA_TRIGGER = CriteriaTriggers.register(new BadKarmaTrigger());
   public static final FavoriteSongTrigger FAVORITE_SONG_TRIGGER = CriteriaTriggers.register(new FavoriteSongTrigger());
   public static final PlaySoundTrigger PLAY_SOUND_TRIGGER = CriteriaTriggers.register(new PlaySoundTrigger());
   public static final BadDayTrigger BAD_DAY_TRIGGER = CriteriaTriggers.register(new BadDayTrigger());
   public static final ThreeDaysTrigger THREE_DAYS_TRIGGER = CriteriaTriggers.register(new ThreeDaysTrigger());
}
