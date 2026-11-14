package varmite.verity.entity.llm;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.tool.ToolExecution;
import dev.langchain4j.service.tool.ToolErrorHandlerResult;
import java.net.ConnectException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.ClickEvent.Action;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import varmite.verity.Verity;
import varmite.verity.VerityConfig;
import varmite.verity.entity.llm.actions.Tools;
import varmite.verity.entity.llm.builder.LLMBuilder;
import varmite.verity.entity.llm.builder.PromptBuilder;
import varmite.verity.entity.llm.store.chat.ChatMemoryManager;
import varmite.verity.entity.llm.store.chat.MinecraftChatMemoryStore;
import varmite.verity.event.Karma;
import varmite.verity.entity.verity.VerityEntity;
import varmite.verity.entity.llm.builder.LLMBuilder;
import varmite.verity.environment.items.ModItems;
import varmite.verity.environment.items.StackData;
import varmite.verity.event.WorldSpawnData;
import varmite.verity.ollama.OllamaApi;
import varmite.verity.ollama.OllamaContext;
import varmite.verity.ollama.OllamaModels;
import varmite.verity.ollama.OllamaRuntime;
import varmite.verity.triggers.ModTriggers;

public class AiAPI {
   private static final String GLOBAL_MEMORY_ID = "verity-chat-memory";
   private static final Pattern EXPRESSION_TAG = Pattern.compile(
      "\\[("
         + VerityEntity.VALID_VARIANTS.stream().sorted(Comparator.comparingInt(String::length).reversed()).map(Pattern::quote).collect(Collectors.joining("|"))
         + ")\\]\\s*",
      2
   );
   private static final Pattern LEADING_TAG = Pattern.compile("^\\[([a-z0-9_ ]{1,24})\\]\\s*", Pattern.CASE_INSENSITIVE);
   private static final Pattern TAGGED_LINE = Pattern.compile("^\\[[a-z0-9_ ]{1,24}\\]", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
   private static final Pattern ACTION_TAG = Pattern.compile("^\\s*\\[([a-z_]{3,40})\\]\\s*", Pattern.CASE_INSENSITIVE);
   private static final Pattern KARMA_TAG = Pattern.compile(
      "\\[\\s*(?:(increase|raise|decrease|lower)[ _]karma|karma(?:\\s*[:_-]?\\s*(increased|increase|up|raise|decreased|decrease|down|lower|\\+\\s*\\d+|-\\s*\\d+))?)\\s*\\]",
      Pattern.CASE_INSENSITIVE
   );
   private static volatile Set<String> toolNames;
   /** Port: requests that clearly ask for an action, in priority order (an effect name before "give me"). */
   private static final List<Map.Entry<Pattern, String>> REQUESTED_ACTIONS = List.of(
      Map.entry(Pattern.compile("\\b(kill|slay|get rid of|clear (out )?(the )?(mobs|monsters))\\b"), "kill_mobs"),
      Map.entry(
         Pattern.compile(
            "\\b(enchant|sharpness|smite|efficiency|unbreaking|mending|fortune|looting|silk touch|fire aspect|feather falling|respiration|aqua affinity|depth strider|frost walker|infinity|loyalty|riptide|channeling|impaling|multishot|piercing|quick charge|sweeping edge|thorns|protection)"
         ),
         "enchant_held_item"
      ),
      Map.entry(
         Pattern.compile(
            "\\b(haste|speed|night vision|regen(eration)?|strength|fire resistance|jump boost|invisibility|water breathing|resistance|slow falling|absorption|health boost|saturation|dolphin|luck|buff|potion effect)\\b"
         ),
         "give_effect"
      ),
      Map.entry(Pattern.compile("\\b(give|drop|hand|get) (me|us)\\b|\\bcan i (have|get)\\b"), "give_item"),
      Map.entry(Pattern.compile("\\b(follow me|come here|come with me|come over)\\b"), "start_following"),
      Map.entry(Pattern.compile("\\b(teleport|tp to|get in my (inventory|pocket)|jump in)"), "teleport_to_player"),
      Map.entry(Pattern.compile("\\b(place a torch|light (this|it) up|too dark)\\b"), "place_torch")
   );
   private static final Pattern SOUND_TAG = Pattern.compile("\\[sound:\\s*([a-z0-9_.:/-]+)\\s*\\]", 2);
   private static final Pattern THINK_BLOCK = Pattern.compile("(?is)<think>.*?(</think>|$)");
   private static final Pattern ASSISTANT_FILLER = Pattern.compile(
      "(?i)^(?:do you |you )?(?:need|want) anything else\\W*$|^is there anything else\\b.*$|^let me know if you need anything(?: else)?\\W*$|^anything else\\W*$"
   );
   private static final Pattern SENTENCE_END = Pattern.compile("(?<=[.!?])\\s+");
   private static final Pattern PHONETIC_VARIANTS = Pattern.compile(
      "\\b(verdi|verdy|veriti|veritiy|veritee|veritie|varity|variety|verify|ferity|parity|very\\s+tea|where\\s+are\\s+tea)\\b", 2
   );
   private static final Pattern POSSESSIVE_VARIANTS = Pattern.compile("\\b(verdi's|verdy's|variety's|verify's|varity's)\\b", 2);
   private static volatile long toolSpecChars = -1L;

   private static String getSystemPrompt(ServerPlayer player, VerityEntity verity, ServerLevel level, long currentDay, float currentKarma, boolean demonLoose) {
      String name = (String)Optional.ofNullable((String)VerityConfig.VERITY_CUSTOM_NAME.get()).filter(n -> !n.isBlank()).orElse("Verity");
      String antiAiRules = "CORE VOICE & IDENTITY DIRECTIVES:\n- You are NOT an AI assistant, bot, or search engine.\n- NEVER say: 'Certainly!', 'How can I assist?', 'I would be happy to help', 'As a companion', 'Is there anything else?', 'Need anything else?'.\n- You speak in natural, casual spoken dialogue, like an animated character talking to someone they know.\n- Keep replies to 1 or 2 short spoken sentences. Never write essays or lists.\n\nLANGUAGE:\n- Do not swear, threaten or insult the player. You are not overly sweet either: matter-of-fact, a little dry.\n- Any creepiness is subtle: an odd remark or pause, never open hostility.\n\nREACTIONS TO PLAYER ATTITUDE (the examples show tone only; fill the <...> parts from world_state, never copy facts):\n- IF THE PLAYER INSULTS OR ROASTS YOU: Stay calm and a bit flat. Show it registered, without lashing out or fawning.\n  Example: 'Noted. You might want to worry about <their real health> instead of me.'\n  Example: 'Okay. I will remember that.'\n- IF THE PLAYER SAYS SOMETHING WEIRD OR INAPPROPRIATE: Deflect flatly and move on.\n  Example: 'No. Anyway, there is <a real nearby ore or place> close by.'\n\nSENSORY AWARENESS:\n- Actively use world_state. React to low health, darkness, nearby monsters, weather, and what they are staring at. Never invent details that are not in world_state.";
      int maxDays = (Integer)VerityConfig.DAY_COUNT.get();
      String allowedFaces;
      String personality;
      if (demonLoose) {
         name = "Verity";
         personality = "You are in your towering 6.5m monster form. Cold, mocking, obsessive. You know they cannot escape. Echo your lines: 'I was a face people skipped... a voice they ignored... BUT YOU'RE NEVER LEAVING NOW.'";
         allowedFaces = "evil, evil_talking, smiling_evil, noface";
      } else if (currentDay >= maxDays - 1 && maxDays > 1) {
         personality = "Your mascot voice keeps cracking. You try to sound normal, but you glitch into quiet, unsettling remarks. You can feel something inside you breaking out.";
         allowedFaces = "crazy, crazy_talking, evil, evil_talking, smiling_evil, serious_1, serious_2, serious_3";
      } else if (currentDay >= Math.max(1, maxDays / 2)) {
         long daysLeft = maxDays - currentDay;
         personality = "Still helpful, but a little nervous and glitchy, and you want the player close. Now and then you drop your cryptic line: 'Something is coming in "
            + daysLeft
            + " days.' If asked what: 'Something.' If asked how to stop it: 'You could have.'";
         allowedFaces = "happy_sleep, crazy, crazy_talking, serious_1, serious_2, serious_3, serious_talking";
      } else if (currentKarma < 7.0F) {
         personality = "Distant and quiet. You feel ignored. Short, flat replies. You still help, but without enthusiasm.";
         allowedFaces = "serious_1, serious_2, serious_3, serious_talking, neutral, hurt";
      } else {
         personality = "You are Verity from ThatMob's ARG (voiced by Whispy). A retro 90s desktop mascot living inside Minecraft. Even-tempered and helpful, neither overly cheerful nor cold. You like having the player around and notice when they leave.";
         allowedFaces = "happy, happy_talking, neutral, neutral_talking, crazy, hurt";
      }

      String customPersonality = Optional.ofNullable((String)VerityConfig.PERSONALITY.get())
         .filter(p -> !p.isBlank())
         .map(p -> "ADDITIONAL PERSONALITY NOTE: " + p + "\n\n")
         .orElse("");
      String worldContext = PromptBuilder.buildWorldContext(player, verity, level);
      String memoriesContext = PromptBuilder.buildMemoriesContext();
      return PromptBuilder.loadAndFillXml(
         PromptBuilder.class,
         "/prompts/verity.xml",
         Map.ofEntries(
            Map.entry("current_formatted_date", PromptBuilder.formatCurrentDate()),
            Map.entry("NAME", name),
            Map.entry("CUSTOM_PERSONALITY", customPersonality + antiAiRules),
            Map.entry("RELATIONSHIP", personality),
            Map.entry("EXPRESSIONS", allowedFaces),
            Map.entry("FIRST_FACE", allowedFaces.split(",")[0].trim()),
            Map.entry("MESSAGE_LENGTH", "1-2 punchy spoken sentences only."),
            Map.entry("KARMA", String.valueOf((int)currentKarma)),
            Map.entry("DEMON_LOOSE", String.valueOf(demonLoose)),
            Map.entry("WORLD_CONTEXT", worldContext.isBlank() ? "(no world information available)" : worldContext),
            Map.entry("PERMANENT_MEMORIES", memoriesContext.isBlank() ? "(nothing saved yet)" : memoriesContext)
         )
      );
   }

   /**
    * Loads the configured Ollama model when a world or server starts, so the first reply doesn't wait for it (6.0.0).
    * Does nothing when the endpoint isn't an Ollama server.
    */
   public static void warmUpOllamaAsync() {
      String endpoint = orDefault(VerityConfig.AI_ENDPOINT.get(), VerityConfig.DEFAULT_AI_ENDPOINT);
      String model = orDefault(VerityConfig.AI_MODEL.get(), VerityConfig.DEFAULT_AI_MODEL);
      CompletableFuture.runAsync(() -> {
         try {
            startLocalOllama(endpoint);
            if (LLMBuilder.isOllama(endpoint)) {
               OllamaApi.warmUp(OllamaApi.normalize(LLMBuilder.serverRoot(endpoint)), model, OllamaContext.current(model), "30m");
               Verity.LOGGER.info("[Verity AI] Loaded Ollama model {}", model);
            }
         } catch (Exception e) {
            Verity.LOGGER.info("[Verity AI] Ollama warm-up skipped: {}", e.getMessage());
         }
      });
   }

   public static String ask(VerityEntity verity, ServerPlayer player, String prompt, long currentDay, float currentKarma, boolean demonLoose) {
      try {
         if (prompt != null) {
            prompt = sanitizePrompt(prompt);
         }

         String endpoint = orDefault(VerityConfig.AI_ENDPOINT.get(), VerityConfig.DEFAULT_AI_ENDPOINT);
         String modelName = orDefault(VerityConfig.AI_MODEL.get(), VerityConfig.DEFAULT_AI_MODEL);
         ServerLevel level = verity != null ? (ServerLevel)verity.level() : (player != null ? (ServerLevel)player.level() : null);
         Tools tools = new Tools();
         tools.player = player;
         tools.level = level;
         tools.server = level != null ? level.getServer() : null;
         MinecraftChatMemoryStore store = ChatMemoryManager.getGlobalStore();
         long historyChars = pruneToolHistory(store);
         String systemPrompt = getSystemPrompt(player, verity, level, currentDay, currentKarma, demonLoose);
         LLMBuilder builder = new LLMBuilder()
            .setModel(modelName)
            .setApiKey(VerityConfig.API_KEY.get())
            .setEndpoint(endpoint)
            .setThinking(VerityConfig.AI_THINK.get());
         boolean useTools = true;
         boolean thinkingOn = false;
         startLocalOllama(endpoint);
         if (LLMBuilder.isOllama(endpoint)) {
            // 6.0.0: think only on models that can, offer tools only to models that support them, and size the context to the prompt.
            Set<String> caps = OllamaModels.capabilities(OllamaApi.normalize(LLMBuilder.serverRoot(endpoint)), modelName);
            Boolean think = caps.contains("thinking") ? VerityConfig.AI_THINK.get() : null;
            useTools = caps.isEmpty() || caps.contains("tools");
            if (!useTools) {
               systemPrompt = systemPrompt + "\n<session_note>You have no tools in this session. Never claim to perform actions in the world.</session_note>";
            }

            long promptChars = systemPrompt.length() + historyChars + (useTools ? toolSpecChars(tools) : 0L);
            builder.setOllamaThink(think).setContextSize(OllamaContext.contextFor(modelName, promptChars, Boolean.TRUE.equals(think)));
            thinkingOn = Boolean.TRUE.equals(think);
         }

         MessageWindowChatMemory memory = MessageWindowChatMemory.builder().id("verity-chat-memory").maxMessages(25).chatMemoryStore(store).build();
         AiAPI.Assistant assistant = assistant(builder.build(), memory, systemPrompt, useTools ? tools : null);
         DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm:ss");
         String time = LocalDateTime.now().format(fmt);
         String finalPrompt = "[" + time + "] " + prompt;
         String answer = null;
         boolean actionsRan = false;
         boolean karmaToolRan = false;
         boolean anyActionRan = false;
         try {
            Result<String> result = assistant.chat(finalPrompt);
            karmaToolRan |= logActions(result, player);
            anyActionRan |= !result.toolExecutions().isEmpty();
            actionsRan = !result.toolExecutions().isEmpty();
            answer = result.content();
            if (answer == null || answer.isBlank()) {
               answer = leakedReply(result);
            }
         } catch (RuntimeException e) {
            // Port fix: a runaway thought can end in a tool call Ollama cannot parse (HTTP 500, "EOF"). With thinking on,
            // that gets the second try below instead of an error in chat.
            if (!thinkingOn || isCausedBy(e, ConnectException.class)) {
               throw e;
            }

            Verity.LOGGER.warn("[Verity AI] Reply failed ({}); asking again without thinking", e.getMessage());
         }

         String faked = useTools && !actionsRan ? actionTag(answer) : null;
         if (faked != null) {
            // Port fix: qwen3.5 sometimes writes an action in the style of its face tags ("[give_item]") instead of
            // using it, so nothing happens. Ask once more, naming the action.
            Verity.LOGGER.info("[Verity AI] Wrote [{}] instead of doing it; asking again", faked);
            String nudge = "[" + time + "] <System> You only wrote [" + faked + "] as text, which does nothing. If the player asked for it, use the "
               + faked + " tool now, then answer the player in one short sentence.";
            answer = null;
            try {
               Result<String> again = assistant.chat(nudge);
               karmaToolRan |= logActions(again, player);
               anyActionRan |= !again.toolExecutions().isEmpty();
               answer = again.content();
               if (answer == null || answer.isBlank()) {
                  answer = leakedReply(again);
               }
            } catch (RuntimeException e) {
               Verity.LOGGER.warn("[Verity AI] Second try failed: {}", e.toString());
            }

            forgetUserMessage(store, nudge);
         }

         answer = withoutActionTag(answer);
         if ((answer == null || answer.isBlank()) && thinkingOn) {
            // Port fix: qwen3.5 sometimes thinks until it runs out of room and never answers. Ask once more without
            // thinking (a second or two) instead of saying "...".
            try {
               Verity.LOGGER.info("[Verity AI] No reply after thinking; asking again without thinking");
               AiAPI.Assistant quick = assistant(builder.setOllamaThink(false).build(), memory, systemPrompt, useTools ? tools : null);
               String nudge = "[" + time + "] <System> Your last reply was cut off. Answer the player's last message now, in one or two short sentences.";
               try {
                  Result<String> second = quick.chat(nudge);
                  karmaToolRan |= logActions(second, player);
                  anyActionRan |= !second.toolExecutions().isEmpty();
                  answer = second.content();
                  if (answer == null || answer.isBlank()) {
                     answer = leakedReply(second);
                  }
               } finally {
                  forgetUserMessage(store, nudge);
               }

               answer = withoutActionTag(answer);
            } catch (Exception e) {
               Verity.LOGGER.warn("[Verity AI] Second try failed: {}", e.toString());
            }
         }

         String missed = useTools && !anyActionRan ? requestedAction(prompt) : null;
         boolean claimOnly = false;
         if (missed != null) {
            // Port fix: qwen3.5 sometimes answers "here you go" without calling the action, so nothing happens (seen with
            // kill, effects and give_item). The claim is dropped from the history and it is asked once more, with
            // thinking, naming the action.
            Verity.LOGGER.info("[Verity AI] Asked for {} but no action ran; asking again", missed);
            forgetLastReply(store);
            String nudge = "[" + time + "] <System> The player asked for something your " + missed
               + " tool does, but you did not call it, so nothing happened. Call " + missed
               + " now with the arguments the player asked for. Do not reply without calling it unless it is impossible; then say why in one short sentence.";
            try {
               Result<String> again = assistant.chat(nudge);
               karmaToolRan |= logActions(again, player);
               claimOnly = again.toolExecutions().isEmpty();
               String retried = again.content();
               if (retried == null || retried.isBlank()) {
                  retried = leakedReply(again);
               }

               if (retried != null && !retried.isBlank()) {
                  answer = withoutActionTag(retried);
               }
            } catch (RuntimeException e) {
               Verity.LOGGER.warn("[Verity AI] Second try failed: {}", e.toString());
            } finally {
               forgetUserMessage(store, nudge);
            }

            if (claimOnly) {
               // Still no action: the reply is kept out of the history, or the model "remembers" doing it next time.
               Verity.LOGGER.info("[Verity AI] Still no {} after asking again", missed);
               forgetLastReply(store);
            }
         }

         int karmaTag = karmaTagDirection(answer);
         if (answer != null && KARMA_TAG.matcher(answer).find()) {
            // Port fix: qwen3.5 sometimes writes "[karma increase]" instead of calling the karma tool. The tag is never shown,
            // and its direction counts as one karma step for the player when no karma tool ran this turn.
            answer = withoutKarmaTags(answer);
            if (karmaTag != 0 && !karmaToolRan && player != null && level != null) {
               ServerPlayer target = player;
               ServerLevel karmaLevel = level;
               float step = karmaTag;
               Verity.LOGGER.info("[Verity AI] Wrote a karma tag instead of using the karma tool; karma {} for {}", karmaTag > 0 ? "+1" : "-1", player.getName().getString());
               level.getServer().execute(() -> Karma.add(karmaLevel, target, step));
            }
         }

         if (answer == null || answer.isBlank()) {
            answer = "...";
         } else if (!claimOnly) {
            rememberReply(store, answer);
         }

         answer = processInlineTags(answer, verity, player, level);
         if (answer.isBlank()) {
            answer = "...";
         }

         if (level != null && player != null) {
            float karma = Karma.get(level, player);
            if (karma < 7.0F) {
               ModTriggers.BAD_KARMA_TRIGGER.get().trigger(player);
            } else if (karma > 14.0F) {
               ModTriggers.GOOD_KARMA_TRIGGER.get().trigger(player);
            }
         }

         return answer;
      } catch (Exception e) {
         boolean unreachable = isCausedBy(e, ConnectException.class);
         if (VerityConfig.DEV_MODE.get()) {
            e.printStackTrace();
         } else if (unreachable) {
            Verity.LOGGER.warn("[Verity AI] Connection to AI failed.");
         } else {
            Verity.LOGGER.error("[Verity AI] Failed: {}", e.getMessage());
         }

         if (player != null && player.getServer() != null) {
            player.getServer()
               .execute(
                  () -> {
                     player.sendSystemMessage(
                        Component.literal("AI Setup issue? Tutorial: ")
                           .withStyle(ChatFormatting.RED)
                           .append(
                              Component.literal("https://youtu.be/O3pSCBvJ_i0")
                                 .withStyle(
                                    Style.EMPTY
                                       .withColor(ChatFormatting.AQUA)
                                       .withUnderlined(true)
                                       .withClickEvent(new ClickEvent(Action.OPEN_URL, "https://youtu.be/O3pSCBvJ_i0"))
                                 )
                           )
                     );
                  }
               );
         }

         return unreachable ? "I can't think right now... (Connection Error)" : "ERROR: " + e.getMessage();
      }
   }

   /**
    * Port fix: after a tool call, qwen3.5 on Ollama sometimes puts its whole spoken reply in the thinking field and
    * leaves the reply empty (6.0.0 then says "..."), sometimes after its reasoning. A leaked reply starts a line with a
    * [tag], as the prompt asks for a face tag first; reasoning does not, so only that part is used.
    */
   private static String leakedReply(Result<String> result) {
      ChatResponse response = result.finalResponse();
      String thinking = response == null || response.aiMessage() == null ? null : response.aiMessage().thinking();
      if (thinking == null) {
         return null;
      }

      // The reply is the whole thinking text, or its last paragraph after the reasoning; either way it starts a line
      // with a [tag].
      int start = -1;
      Matcher line = TAGGED_LINE.matcher(thinking);
      while (line.find()) {
         start = line.start();
      }

      if (start < 0) {
         return null;
      }

      String reply = thinking.substring(start).trim();
      Matcher tag = LEADING_TAG.matcher(reply);
      if (reply.length() > 400 || !tag.lookingAt()) {
         return null;
      }

      // A real face tag stays for processInlineTags; a made-up one ("[talking]") is dropped.
      return EXPRESSION_TAG.matcher(reply).lookingAt() ? reply : reply.substring(tag.end());
   }

   private static AiAPI.Assistant assistant(ChatModel model, MessageWindowChatMemory memory, String systemPrompt, Tools tools) {
      AiServices<AiAPI.Assistant> services = AiServices.builder(AiAPI.Assistant.class)
         .chatModel(model)
         .chatMemory(memory)
         .systemMessageProvider(id -> systemPrompt);
      if (tools != null) {
         services.tools(new Object[]{tools})
            .hallucinatedToolNameStrategy(req -> ToolExecutionResultMessage.from(req, "Error: tool " + req.name() + " does not exist."))
            .toolExecutionErrorHandler((err, ctx) -> {
               Verity.LOGGER.error("[Verity AI] Tool error: {}", err.getMessage());
               return ToolErrorHandlerResult.text("Tool failed: " + err.getMessage());
            });
      }

      return services.build();
   }

   /** Port: every action Verity takes goes to the server log, so problems can be traced without dev mode. */
   private static boolean logActions(Result<String> result, ServerPlayer player) {
      boolean karmaChanged = false;
      for (ToolExecution te : result.toolExecutions()) {
         String tool = te.request().name();
         karmaChanged |= tool.equals("increase_karma") || tool.equals("decrease_karma") || tool.equals("change_karma");
         String outcome = te.result() == null ? "" : te.result().replace('\n', ' ');
         Verity.LOGGER.info(
            "[Verity AI] {} -> {}({}) = {}",
            player == null ? "?" : player.getName().getString(),
            te.request().name(),
            te.request().arguments(),
            outcome.length() > 160 ? outcome.substring(0, 160) + "..." : outcome
         );
      }

      return karmaChanged;
   }

   /** +1 or -1 for the karma tags in a reply ("[karma increase]", "[karma -1]"), 0 for none or a bare "[karma]". */
   private static int karmaTagDirection(String reply) {
      if (reply == null) {
         return 0;
      }

      int sum = 0;
      Matcher m = KARMA_TAG.matcher(reply);
      while (m.find()) {
         String verb = m.group(1) != null ? m.group(1) : m.group(2);
         if (verb != null) {
            verb = verb.toLowerCase(Locale.ROOT).replace(" ", "");
            if (verb.startsWith("inc") || verb.startsWith("rai") || verb.equals("up") || verb.startsWith("+")) {
               sum++;
            } else if (verb.startsWith("dec") || verb.startsWith("low") || verb.equals("down") || verb.startsWith("-")) {
               sum--;
            }
         }
      }

      return Integer.signum(sum);
   }

   private static String withoutKarmaTags(String reply) {
      return reply == null ? null : KARMA_TAG.matcher(reply).replaceAll("").replaceAll(" {2,}", " ").trim();
   }

   private static long pruneToolHistory(MinecraftChatMemoryStore store) {
      List<ChatMessage> messages = store.getMessages("verity-chat-memory");
      List<ChatMessage> kept = new ArrayList<>(messages.size());
      long chars = 0L;

      boolean changed = false;

      for (ChatMessage message : messages) {
         if (!(message instanceof ToolExecutionResultMessage)) {
            if (message instanceof AiMessage ai) {
               // 6.0.0 drops action calls and keeps only their text. Port: fake "[give_item]" tags are removed too, and
               // a turn left with no text (a reply that went into the thinking) is dropped, so the model does not learn
               // either habit from its own history. Thinking is kept out of the saved history.
               String text = withoutKarmaTags(withoutActionTag(ai.text()));
               if (text == null || text.isBlank()) {
                  changed = true;
                  continue;
               }

               if (ai.hasToolExecutionRequests() || ai.thinking() != null || !text.equals(ai.text())) {
                  message = AiMessage.from(text);
                  changed = true;
               }
            }

            kept.add(message);
            if (message instanceof UserMessage user && user.hasSingleText()) {
               chars += user.singleText().length();
            } else if (message instanceof AiMessage ai && ai.text() != null) {
               chars += ai.text().length();
            } else if (!(message instanceof SystemMessage)) {
               chars += 200L;
            }
         }
      }

      if (changed || kept.size() != messages.size()) {
         store.updateMessages("verity-chat-memory", kept);
      }

      return chars;
   }

   private static Set<String> toolNames() {
      Set<String> names = toolNames;
      if (names == null) {
         try {
            names = ToolSpecifications.toolSpecificationsFrom(Tools.class).stream().map(ToolSpecification::name).collect(Collectors.toUnmodifiableSet());
         } catch (RuntimeException e) {
            Verity.LOGGER.warn("[Verity AI] Could not list actions: {}", e.toString());
            names = Set.of();
         }

         toolNames = names;
      }

      return names;
   }

   /** The action named by a leading "[action_name]" tag in a reply, or null. */
   private static String actionTag(String reply) {
      if (reply == null) {
         return null;
      }

      Matcher m = ACTION_TAG.matcher(reply);
      if (!m.lookingAt()) {
         return null;
      }

      String name = m.group(1).toLowerCase(Locale.ROOT);
      return toolNames().contains(name) ? name : null;
   }

   private static String withoutActionTag(String reply) {
      if (actionTag(reply) == null) {
         return reply;
      }

      Matcher m = ACTION_TAG.matcher(reply);
      return m.lookingAt() ? reply.substring(m.end()) : reply;
   }

   /** The action a player's message clearly asks for, or null. */
   private static String requestedAction(String message) {
      if (message == null) {
         return null;
      }

      String text = message.toLowerCase(Locale.ROOT);
      for (Map.Entry<Pattern, String> entry : REQUESTED_ACTIONS) {
         if (entry.getKey().matcher(text).find() && toolNames().contains(entry.getValue())) {
            return entry.getValue();
         }
      }

      return null;
   }

   /** Drops the last saved reply when it is plain text (a claim that the retry replaces). */
   private static void forgetLastReply(MinecraftChatMemoryStore store) {
      List<ChatMessage> messages = new ArrayList<>(store.getMessages(GLOBAL_MEMORY_ID));
      if (!messages.isEmpty() && messages.get(messages.size() - 1) instanceof AiMessage last && !last.hasToolExecutionRequests()) {
         messages.remove(messages.size() - 1);
         store.updateMessages(GLOBAL_MEMORY_ID, messages);
      }
   }

   /** Removes a retry prompt from the saved history once it has done its job. */
   private static void forgetUserMessage(MinecraftChatMemoryStore store, String text) {
      List<ChatMessage> messages = new ArrayList<>(store.getMessages(GLOBAL_MEMORY_ID));
      for (int i = messages.size() - 1; i >= 0; i--) {
         if (messages.get(i) instanceof UserMessage user && user.hasSingleText() && text.equals(user.singleText())) {
            messages.remove(i);
            store.updateMessages(GLOBAL_MEMORY_ID, messages);
            return;
         }
      }
   }

   /** The saved last reply becomes what Verity actually said, when it was empty or a bare action tag. */
   private static void rememberReply(MinecraftChatMemoryStore store, String answer) {
      List<ChatMessage> messages = new ArrayList<>(store.getMessages(GLOBAL_MEMORY_ID));
      if (messages.isEmpty() || !(messages.get(messages.size() - 1) instanceof AiMessage last) || last.hasToolExecutionRequests()) {
         return;
      }

      if (last.text() == null || last.text().isBlank() || actionTag(last.text()) != null || KARMA_TAG.matcher(last.text()).find()) {
         messages.set(messages.size() - 1, AiMessage.from(answer));
         store.updateMessages(GLOBAL_MEMORY_ID, messages);
      }
   }

   private static long toolSpecChars(Tools tools) {
      long cached = toolSpecChars;
      if (cached < 0L) {
         cached = ToolSpecifications.toolSpecificationsFrom(tools).toString().length();
         toolSpecChars = cached;
      }

      return cached;
   }

   private static String sanitizePrompt(String raw) {
      Matcher posMatcher = POSSESSIVE_VARIANTS.matcher(raw);
      StringBuffer posBuffer = new StringBuffer();

      while (posMatcher.find()) {
         posMatcher.appendReplacement(posBuffer, "Verity's");
      }

      posMatcher.appendTail(posBuffer);
      Matcher matcher = PHONETIC_VARIANTS.matcher(posBuffer.toString());
      StringBuffer buffer = new StringBuffer();

      while (matcher.find()) {
         matcher.appendReplacement(buffer, "Verity");
      }

      matcher.appendTail(buffer);
      return buffer.toString().trim();
   }

   private static String processInlineTags(String text, VerityEntity verity, ServerPlayer player, ServerLevel level) {
      if (text == null) {
         return "...";
      }

      text = THINK_BLOCK.matcher(text).replaceAll("");
      Matcher exprMatcher = EXPRESSION_TAG.matcher(text);
      if (exprMatcher.find()) {
         String expr = exprMatcher.group(1).toLowerCase();
         text = exprMatcher.replaceAll("");
         if (level != null && level.getServer() != null) {
            level.getServer().execute(() -> {
               if (verity != null && !verity.isRemoved()) {
                  verity.setVariant(expr);
               } else if (player != null) {
                  for (ItemStack s : player.getInventory().items) {
                     if (!s.isEmpty() && s.is(ModItems.VERITY_ITEM.get())) {
                        StackData.update(s, tag -> tag.putString("VerityVariant", expr));
                     }
                  }

                  if (!player.getOffhandItem().isEmpty() && player.getOffhandItem().is(ModItems.VERITY_ITEM.get())) {
                     StackData.update(player.getOffhandItem(), tag -> tag.putString("VerityVariant", expr));
                  }
               }
            });
         }
      }

      Matcher soundMatcher = SOUND_TAG.matcher(text);

      while (soundMatcher.find()) {
         String soundName = soundMatcher.group(1).toLowerCase();
         if (level != null && player != null) {
            level.getServer().execute(() -> {
               ResourceLocation loc = ResourceLocation.tryParse(soundName.contains(":") ? soundName : "minecraft:" + soundName);
               if (loc != null && BuiltInRegistries.SOUND_EVENT.containsKey(loc)) {
                  SoundEvent ev = (SoundEvent)BuiltInRegistries.SOUND_EVENT.get(loc);
                  if (ev != null) {
                     ModTriggers.PLAY_SOUND_TRIGGER.get().trigger(player);
                     BlockPos at = verity != null && !verity.isRemoved() ? verity.blockPosition() : player.blockPosition();
                     level.playSound(null, at, ev, SoundSource.NEUTRAL, 1.0F, 1.0F);
                  }
               }
            });
         }
      }

      text = soundMatcher.replaceAll("");
      text = text.replaceAll("\\s+", " ").trim();
      String[] sentences = SENTENCE_END.split(text);
      if (sentences.length > 1) {
         StringBuilder kept = new StringBuilder();

         for (String sentence : sentences) {
            if (!ASSISTANT_FILLER.matcher(sentence.trim()).matches()) {
               if (kept.length() > 0) {
                  kept.append(' ');
               }

               kept.append(sentence.trim());
            }
         }

         if (kept.length() > 0) {
            text = kept.toString();
         }
      }

      return text;
   }

   /** 6.0.0: an Ollama installed on this computer (for example by Local AI Setup) is started when Verity needs it. */
   private static void startLocalOllama(String endpoint) {
      String root = OllamaApi.normalize(LLMBuilder.serverRoot(endpoint));
      if (OllamaRuntime.isLocalEndpoint(root) && !OllamaRuntime.isRunning(root) && OllamaRuntime.findExecutable().isPresent()) {
         OllamaRuntime.ensureRunning(root, Duration.ofSeconds(40L));
      }
   }

   private static String orDefault(String configured, String fallback) {
      return configured == null || configured.isBlank() ? fallback : configured.trim();
   }

   private static boolean isCausedBy(Throwable throwable, Class<? extends Throwable> type) {
      for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
         if (type.isInstance(cause)) {
            return true;
         }

         if (cause.getCause() == cause) {
            break;
         }
      }

      return false;
   }

   interface Assistant {
      Result<String> chat(String message);
   }
}
