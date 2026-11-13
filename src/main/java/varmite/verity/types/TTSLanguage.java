package varmite.verity.types;

import java.util.Map;

public enum TTSLanguage {
   English,
   German;

   private static final Map<TTSLanguage, String> LANG_CODES = Map.ofEntries(Map.entry(English, "en"), Map.entry(German, "de"));

   public String getLangCode() {
      return LANG_CODES.get(this);
   }
}
