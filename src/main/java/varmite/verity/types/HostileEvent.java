package varmite.verity.types;

/** What happens when Verity turns hostile (day threshold reached, or provoked through the AI). */
public enum HostileEvent {
   /** Nothing happens, and Verity's personality never shifts with the day count. */
   OFF,
   /** A chat message stands in for the event, then Verity goes back to normal. */
   MESSAGE,
   /** A Wither spawns near the player. Verity goes back to normal once it is gone. */
   WITHER,
   /** Reserved for Verity's demon form, which is parked (see parked/demon). Behaves like WITHER for now. */
   DEMON
}
