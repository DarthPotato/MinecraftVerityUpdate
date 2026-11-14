package varmite.verity.ollama;

import io.airlift.compress.zstd.ZstdInputStream;
import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class ArchiveExtractor {
   private static final int BLOCK = 512;

   private ArchiveExtractor() {
   }

   public static void extract(Path archive, Path destination, ArchiveExtractor.Progress progress, BooleanSupplier cancelled) throws IOException {
      String name = archive.getFileName().toString().toLowerCase(Locale.ROOT);
      long total = Files.size(archive);
      Files.createDirectories(destination);

      try (ArchiveExtractor.CountingInputStream counted = new ArchiveExtractor.CountingInputStream(Files.newInputStream(archive), total, progress, cancelled)) {
         InputStream in = new BufferedInputStream(counted, 65536);
         if (name.endsWith(".zip")) {
            extractZip(in, destination);
         } else if (!name.endsWith(".tar.zst") && !name.endsWith(".tzst")) {
            if (!name.endsWith(".tgz") && !name.endsWith(".tar.gz")) {
               throw new IOException("Unsupported archive type: " + archive.getFileName());
            }

            extractTar(new BufferedInputStream(new GZIPInputStream(in, 65536), 65536), destination);
         } else {
            extractTar(new BufferedInputStream(new ZstdInputStream(in), 65536), destination);
         }
      }
   }

   private static void extractZip(InputStream in, Path destination) throws IOException {
      ZipEntry entry;
      try (ZipInputStream zip = new ZipInputStream(in)) {
         while ((entry = zip.getNextEntry()) != null) {
            Path target = safeResolve(destination, entry.getName());
            if (entry.isDirectory()) {
               Files.createDirectories(target);
            } else {
               Files.createDirectories(target.getParent());
               Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING);
            }
         }
      }
   }

   private static void extractTar(InputStream in, Path destination) throws IOException {
      byte[] header = new byte[512];
      String longName = null;
      String longLink = null;
      String paxPath = null;
      String paxLink = null;
      Long paxSize = null;

      while (readFully(in, header)) {
         if (isZeroBlock(header)) {
            return;
         }

         String name = cString(header, 0, 100);
         int mode = (int)parseOctal(header, 100, 8);
         long size = parseNumber(header, 124, 12);
         char type = (char)header[156];
         String linkName = cString(header, 157, 100);
         String magic = cString(header, 257, 6);
         if (magic.startsWith("ustar")) {
            String prefix = cString(header, 345, 155);
            if (!prefix.isEmpty()) {
               name = prefix + "/" + name;
            }
         }

         if (paxSize != null) {
            size = paxSize;
         }

         switch (type) {
            case 'K':
               longLink = readString(in, size);
               break;
            case 'L':
               longName = readString(in, size);
               break;
            case 'g':
               skip(in, padded(size));
               break;
            case 'x':
               String pax = readString(in, size);

               for (String record : parsePax(pax)) {
                  int eq = record.indexOf(61);
                  if (eq >= 0) {
                     String key = record.substring(0, eq);
                     String value = record.substring(eq + 1);
                     switch (key) {
                        case "path":
                           paxPath = value;
                           break;
                        case "linkpath":
                           paxLink = value;
                           break;
                        case "size":
                           paxSize = Long.parseLong(value.trim());
                     }
                  }
               }
               break;
            default:
               if (paxPath != null) {
                  name = paxPath;
               } else if (longName != null) {
                  name = longName;
               }

               if (paxLink != null) {
                  linkName = paxLink;
               } else if (longLink != null) {
                  linkName = longLink;
               }

               paxLink = null;
               paxPath = null;
               longLink = null;
               longName = null;
               paxSize = null;
               if (!name.isEmpty() && !name.equals("./") && !name.equals(".")) {
                  Path target = safeResolve(destination, name);
                  switch (type) {
                     case '\u0000':
                     case '0':
                     case '7':
                        Files.createDirectories(target.getParent());
                        Files.deleteIfExists(target);

                        try (InputStream body = new ArchiveExtractor.BoundedInputStream(in, size)) {
                           Files.copy(body, target);
                        }

                        skip(in, padded(size) - size);
                        applyMode(target, mode);
                        break;
                     case '1':
                        Files.createDirectories(target.getParent());
                        Path source = safeResolve(destination, linkName);
                        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                        skip(in, padded(size));
                        break;
                     case '2':
                        Files.createDirectories(target.getParent());
                        safeResolve(target.getParent(), linkName, destination);
                        Files.deleteIfExists(target);
                        Files.createSymbolicLink(target, target.getFileSystem().getPath(linkName));
                        skip(in, padded(size));
                        break;
                     case '5':
                        Files.createDirectories(target);
                        skip(in, padded(size));
                        break;
                     default:
                        skip(in, padded(size));
                  }
               } else {
                  skip(in, padded(size));
               }
         }
      }
   }

   private static void applyMode(Path file, int mode) {
      if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
         Set<PosixFilePermission> perms = EnumSet.noneOf(PosixFilePermission.class);
         PosixFilePermission[] order = new PosixFilePermission[]{
            PosixFilePermission.OTHERS_EXECUTE,
            PosixFilePermission.OTHERS_WRITE,
            PosixFilePermission.OTHERS_READ,
            PosixFilePermission.GROUP_EXECUTE,
            PosixFilePermission.GROUP_WRITE,
            PosixFilePermission.GROUP_READ,
            PosixFilePermission.OWNER_EXECUTE,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_READ
         };

         for (int bit = 0; bit < order.length; bit++) {
            if ((mode & 1 << bit) != 0) {
               perms.add(order[bit]);
            }
         }

         perms.add(PosixFilePermission.OWNER_READ);
         perms.add(PosixFilePermission.OWNER_WRITE);

         try {
            Files.setPosixFilePermissions(file, perms);
         } catch (IOException | UnsupportedOperationException var5) {
         }
      }
   }

   private static Path safeResolve(Path destination, String entryName) throws IOException {
      return safeResolve(destination, entryName, destination);
   }

   private static Path safeResolve(Path base, String entryName, Path mustStayIn) throws IOException {
      String cleaned = entryName.replace('\\', '/');

      while (cleaned.startsWith("./")) {
         cleaned = cleaned.substring(2);
      }

      if (!cleaned.startsWith("/") && !cleaned.matches("^[A-Za-z]:.*")) {
         Path resolved = base.resolve(cleaned).normalize();
         if (!resolved.startsWith(mustStayIn.normalize())) {
            throw new IOException("Archive entry escapes the install folder: " + entryName);
         } else {
            return resolved;
         }
      } else {
         throw new IOException("Archive entry uses an absolute path: " + entryName);
      }
   }

   private static Iterable<String> parsePax(String pax) {
      List<String> records = new ArrayList<>();
      byte[] bytes = pax.getBytes(StandardCharsets.UTF_8);
      int pos = 0;

      while (pos < bytes.length) {
         int space = pos;

         while (space < bytes.length && bytes[space] != 32) {
            space++;
         }

         if (space >= bytes.length) {
            break;
         }

         int length;
         try {
            length = Integer.parseInt(new String(bytes, pos, space - pos, StandardCharsets.UTF_8).trim());
         } catch (NumberFormatException e) {
            break;
         }

         if (length <= 0 || pos + length > bytes.length) {
            break;
         }

         int start = space + 1;
         int end = pos + length - 1;
         records.add(new String(bytes, start, Math.max(0, end - start), StandardCharsets.UTF_8));
         pos += length;
      }

      return records;
   }

   private static boolean isZeroBlock(byte[] block) {
      for (byte b : block) {
         if (b != 0) {
            return false;
         }
      }

      return true;
   }

   private static String cString(byte[] buf, int offset, int length) {
      int end = offset;

      while (end < offset + length && buf[end] != 0) {
         end++;
      }

      return new String(buf, offset, end - offset, StandardCharsets.UTF_8);
   }

   private static long parseOctal(byte[] buf, int offset, int length) {
      long value = 0L;

      for (int i = offset; i < offset + length; i++) {
         byte b = buf[i];
         if (b != 0 && b != 32) {
            if (b < 48 || b > 55) {
               break;
            }

            value = (value << 3) + (b - 48);
         } else if (value != 0L) {
            break;
         }
      }

      return value;
   }

   private static long parseNumber(byte[] buf, int offset, int length) {
      if ((buf[offset] & 128) == 0) {
         return parseOctal(buf, offset, length);
      }

      long value = buf[offset] & 127;

      for (int i = offset + 1; i < offset + length; i++) {
         value = value << 8 | buf[i] & 0xFF;
      }

      return value;
   }

   private static long padded(long size) {
      return (size + 512L - 1L) / 512L * 512L;
   }

   private static String readString(InputStream in, long size) throws IOException {
      if (size > 1048576L) {
         throw new IOException("Unreasonably large tar header record: " + size);
      }

      byte[] data = new byte[(int)size];
      if (!readFully(in, data)) {
         throw new EOFException("Truncated tar header record");
      }

      skip(in, padded(size) - size);
      return cString(data, 0, data.length);
   }

   private static boolean readFully(InputStream in, byte[] buf) throws IOException {
      int read = 0;

      while (read < buf.length) {
         int n = in.read(buf, read, buf.length - read);
         if (n < 0) {
            if (read == 0) {
               return false;
            }

            throw new EOFException("Archive ended in the middle of a tar block");
         }

         read += n;
      }

      return true;
   }

   private static void skip(InputStream in, long count) throws IOException {
      byte[] scratch = new byte[8192];
      long left = count;

      while (left > 0L) {
         int n = in.read(scratch, 0, (int)Math.min(scratch.length, left));
         if (n < 0) {
            throw new EOFException("Archive ended early");
         }

         left -= n;
      }
   }

   private static final class BoundedInputStream extends FilterInputStream {
      private long remaining;

      BoundedInputStream(InputStream in, long size) {
         super(in);
         this.remaining = size;
      }

      @Override
      public int read() throws IOException {
         if (this.remaining <= 0L) {
            return -1;
         }

         int b = super.read();
         if (b >= 0) {
            this.remaining--;
         }

         return b;
      }

      @Override
      public int read(byte[] b, int off, int len) throws IOException {
         if (this.remaining <= 0L) {
            return -1;
         }

         int n = super.read(b, off, (int)Math.min(len, this.remaining));
         if (n < 0) {
            throw new EOFException("Archive ended inside a file");
         }

         this.remaining -= n;
         return n;
      }

      @Override
      public void close() {
      }
   }

   private static final class CountingInputStream extends FilterInputStream {
      private final long total;
      private final ArchiveExtractor.Progress progress;
      private final BooleanSupplier cancelled;
      private long count;
      private long lastReport;

      CountingInputStream(InputStream in, long total, ArchiveExtractor.Progress progress, BooleanSupplier cancelled) {
         super(in);
         this.total = total;
         this.progress = progress;
         this.cancelled = cancelled;
      }

      @Override
      public int read() throws IOException {
         int b = super.read();
         if (b >= 0) {
            this.advance(1L);
         }

         return b;
      }

      @Override
      public int read(byte[] b, int off, int len) throws IOException {
         int n = super.read(b, off, len);
         if (n > 0) {
            this.advance(n);
         }

         return n;
      }

      private void advance(long n) throws IOException {
         this.count += n;
         if (this.count - this.lastReport >= 1048576L || this.count == this.total) {
            this.lastReport = this.count;
            if (this.cancelled != null && this.cancelled.getAsBoolean()) {
               throw new InterruptedIOException("Cancelled");
            }

            if (this.progress != null) {
               this.progress.onProgress(this.count, this.total);
            }
         }
      }
   }

   public interface Progress {
      void onProgress(long consumed, long total);
   }
}
