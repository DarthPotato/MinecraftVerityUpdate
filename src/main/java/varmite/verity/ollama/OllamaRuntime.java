package varmite.verity.ollama;

import java.io.File;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class OllamaRuntime {
   private static Process serverProcess;
   private static boolean shutdownHookAdded;

   private OllamaRuntime() {
   }

   public static OllamaRuntime.Os os() {
      String name = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
      if (name.contains("win")) {
         return OllamaRuntime.Os.WINDOWS;
      } else if (name.contains("mac") || name.contains("darwin")) {
         return OllamaRuntime.Os.MAC;
      } else {
         return !name.contains("linux") && !name.contains("nux") && !name.contains("bsd") ? OllamaRuntime.Os.OTHER : OllamaRuntime.Os.LINUX;
      }
   }

   public static String arch() {
      String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
      if (arch.contains("aarch64") || arch.contains("arm64")) {
         return "arm64";
      } else {
         return !arch.contains("amd64") && !arch.contains("x86_64") && !arch.equals("x64") ? arch : "amd64";
      }
   }

   public static Path managedDir() {
      String home = System.getProperty("user.home", ".");

      Path base = switch (os()) {
         case WINDOWS -> {
            String local = System.getenv("LOCALAPPDATA");
            yield local != null && !local.isBlank() ? Paths.get(local) : Paths.get(home, "AppData", "Local");
         }
         case MAC -> Paths.get(home, "Library", "Application Support");
         default -> {
            String xdg = System.getenv("XDG_DATA_HOME");
            yield xdg != null && !xdg.isBlank() ? Paths.get(xdg) : Paths.get(home, ".local", "share");
         }
      };
      return base.resolve("Verity").resolve("ollama");
   }

   static Path managedInstallDir() {
      return managedDir().resolve("current");
   }

   public static String executableName() {
      return os() == OllamaRuntime.Os.WINDOWS ? "ollama.exe" : "ollama";
   }

   static Optional<Path> executableIn(Path installDir) {
      for (Path candidate : List.of(installDir.resolve(executableName()), installDir.resolve("bin").resolve(executableName()))) {
         if (Files.isRegularFile(candidate)) {
            return Optional.of(candidate);
         }
      }

      return Optional.empty();
   }

   public static boolean hasManagedInstall() {
      return executableIn(managedInstallDir()).isPresent();
   }

   public static Optional<Path> findExecutable() {
      Optional<Path> managed = executableIn(managedInstallDir());
      if (managed.isPresent()) {
         return managed;
      }

      for (Path candidate : candidates()) {
         if (Files.isRegularFile(candidate) && (os() == OllamaRuntime.Os.WINDOWS || Files.isExecutable(candidate))) {
            return Optional.of(candidate);
         }
      }

      return Optional.empty();
   }

   private static List<Path> candidates() {
      List<Path> list = new ArrayList<>();
      String path = System.getenv("PATH");
      if (path != null) {
         for (String dir : path.split(File.pathSeparator)) {
            if (!dir.isBlank()) {
               try {
                  list.add(Paths.get(dir.trim().replace("\"", "")).resolve(executableName()));
               } catch (RuntimeException var7) {
               }
            }
         }
      }

      String home = System.getProperty("user.home", ".");
      switch (os()) {
         case WINDOWS:
            String local = System.getenv("LOCALAPPDATA");
            if (local != null) {
               list.add(Paths.get(local, "Programs", "Ollama", "ollama.exe"));
            }

            String programFiles = System.getenv("ProgramFiles");
            if (programFiles != null) {
               list.add(Paths.get(programFiles, "Ollama", "ollama.exe"));
            }
            break;
         case MAC:
            list.add(Paths.get("/Applications/Ollama.app/Contents/Resources/ollama"));
            list.add(Paths.get(home, "Applications", "Ollama.app", "Contents", "Resources", "ollama"));
            list.add(Paths.get("/opt/homebrew/bin/ollama"));
            list.add(Paths.get("/usr/local/bin/ollama"));
            break;
         default:
            list.add(Paths.get("/usr/local/bin/ollama"));
            list.add(Paths.get("/usr/bin/ollama"));
            list.add(Paths.get(home, ".local", "bin", "ollama"));
            list.add(Paths.get("/snap/bin/ollama"));
      }

      return list;
   }

   public static boolean isLocalEndpoint(String baseUrl) {
      try {
         String host = URI.create(OllamaApi.normalize(baseUrl)).getHost();
         return host == null || host.equals("127.0.0.1") || host.equalsIgnoreCase("localhost") || host.equals("0.0.0.0") || host.equals("::1");
      } catch (IllegalArgumentException e) {
         return false;
      }
   }

   public static boolean isRunning(String baseUrl) {
      return OllamaApi.version(baseUrl, Duration.ofSeconds(2L)).isPresent();
   }

   public static synchronized boolean ensureRunning(String baseUrl, Duration wait) {
      if (isRunning(baseUrl)) {
         return true;
      }

      if (!isLocalEndpoint(baseUrl)) {
         return false;
      }

      Optional<Path> exe = findExecutable();
      if (exe.isEmpty()) {
         return false;
      }

      try {
         start(exe.get(), baseUrl);
      } catch (IOException e) {
         return false;
      }

      long deadline = System.currentTimeMillis() + wait.toMillis();

      while (System.currentTimeMillis() < deadline) {
         if (isRunning(baseUrl)) {
            return true;
         }

         if (serverProcess != null && !serverProcess.isAlive()) {
            return isRunning(baseUrl);
         }

         try {
            Thread.sleep(400L);
         } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
         }
      }

      return isRunning(baseUrl);
   }

   private static void start(Path exe, String baseUrl) throws IOException {
      Files.createDirectories(managedDir());
      ProcessBuilder builder = new ProcessBuilder(exe.toAbsolutePath().toString(), "serve");
      builder.directory(exe.getParent().toFile());
      URI uri = URI.create(OllamaApi.normalize(baseUrl));
      int port = uri.getPort() == -1 ? 11434 : uri.getPort();
      if (port != 11434) {
         builder.environment().put("OLLAMA_HOST", "127.0.0.1:" + port);
      }

      builder.redirectErrorStream(true);
      builder.redirectOutput(Redirect.appendTo(managedDir().resolve("server.log").toFile()));
      serverProcess = builder.start();
      if (!shutdownHookAdded) {
         shutdownHookAdded = true;
         Runtime.getRuntime().addShutdownHook(new Thread(OllamaRuntime::stopStartedServer, "Verity-Ollama-Shutdown"));
      }
   }

   public static synchronized boolean startedByVerity() {
      return serverProcess != null && serverProcess.isAlive();
   }

   public static synchronized void stopStartedServer() {
      Process process = serverProcess;
      serverProcess = null;
      if (process != null && process.isAlive()) {
         process.descendants().forEach(ProcessHandle::destroy);
         process.destroy();
      }
   }

   public static synchronized void stopManagedServers() {
      stopStartedServer();
      Path dir = managedInstallDir().toAbsolutePath().normalize();
      List<ProcessHandle> running = ProcessHandle.allProcesses().filter(px -> runsFrom(px, dir)).toList();
      running.forEach(ProcessHandle::destroy);
      long deadline = System.currentTimeMillis() + 10000L;

      for (ProcessHandle p : running) {
         try {
            p.onExit().get(Math.max(1L, deadline - System.currentTimeMillis()), TimeUnit.MILLISECONDS);
         } catch (TimeoutException e) {
            p.destroyForcibly();
         } catch (ExecutionException var8) {
         } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
         }
      }
   }

   private static boolean runsFrom(ProcessHandle process, Path dir) {
      try {
         return process.info().command().map(c -> Paths.get(c).toAbsolutePath().normalize().startsWith(dir)).orElse(false);
      } catch (RuntimeException e) {
         return false;
      }
   }

   public enum Os {
      WINDOWS,
      MAC,
      LINUX,
      OTHER;
   }
}
