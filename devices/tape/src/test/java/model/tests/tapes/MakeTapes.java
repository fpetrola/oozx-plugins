/*
 * Copyright (c) 2023-2026 Fernando Damian Petrola
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package model.tests.tapes;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

/**
 * The tapes the net plays that nobody else made: a TAP as the ROM saves a program and a screen,
 * and TZXs with every kind of block, written byte by byte from the specification. Run once with
 * -Dtapes.make=true; what it writes is kept with the tests.
 */
@EnabledIfSystemProperty(named = "tapes.make", matches = "true")
class MakeTapes {

  static final Path HERE = Path.of("src/test/resources/tapes");

  /** What the ROM writes for one block: the flag, the bytes, and their parity. */
  static byte[] romBlock(int flag, byte[] data) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.write(flag);
    int parity = flag;
    for (byte b : data) {
      out.write(b);
      parity ^= b;
    }
    out.write(parity & 0xff);
    return out.toByteArray();
  }

  static byte[] header(int type, String name, int length, int start, int param) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.write(type);
    byte[] padded = String.format("%-10s", name).getBytes(StandardCharsets.ISO_8859_1);
    out.write(padded, 0, 10);
    le16(out, length);
    le16(out, start);
    le16(out, param);
    return romBlock(0x00, out.toByteArray());
  }

  static byte[] random(int length, long seed) {
    byte[] bytes = new byte[length];
    new Random(seed).nextBytes(bytes);
    return bytes;
  }

  static void le16(ByteArrayOutputStream out, int value) {
    out.write(value & 0xff);
    out.write(value >> 8 & 0xff);
  }

  static void le24(ByteArrayOutputStream out, int value) {
    le16(out, value);
    out.write(value >> 16 & 0xff);
  }

  static void le32(ByteArrayOutputStream out, int value) {
    le16(out, value);
    le16(out, value >> 16);
  }

  static void tapBlock(ByteArrayOutputStream out, byte[] block) {
    le16(out, block.length);
    out.writeBytes(block);
  }

  static void text(ByteArrayOutputStream out, String text) {
    byte[] bytes = text.getBytes(StandardCharsets.ISO_8859_1);
    out.write(bytes.length);
    out.writeBytes(bytes);
  }

  @Test
  void make() throws IOException {
    Files.createDirectories(HERE);
    byte[] program = random(120, 1);
    byte[] screen = random(6912, 2);

    ByteArrayOutputStream tap = new ByteArrayOutputStream();
    tapBlock(tap, header(0, "program", program.length, 10, program.length));
    tapBlock(tap, romBlock(0xff, program));
    tapBlock(tap, header(3, "screen", screen.length, 16384, 32768));
    tapBlock(tap, romBlock(0xff, screen));
    Files.write(HERE.resolve("program-and-screen.tap"), tap.toByteArray());

    ByteArrayOutputStream tzx = new ByteArrayOutputStream();
    tzx.writeBytes(new byte[]{'Z', 'X', 'T', 'a', 'p', 'e', '!', 0x1a, 1, 20});
    // 0x10: standard, as the ROM saves it
    byte[] head = header(3, "screen", 256, 16384, 32768);
    tzx.write(0x10); le16(tzx, 1000); le16(tzx, head.length); tzx.writeBytes(head);
    // 0x11: turbo, with its own timings and a last byte of 5 bits
    byte[] turbo = romBlock(0xff, random(256, 3));
    tzx.write(0x11); le16(tzx, 2000); le16(tzx, 600); le16(tzx, 650); le16(tzx, 500); le16(tzx, 1000);
    le16(tzx, 3000); tzx.write(5); le16(tzx, 500); le24(tzx, turbo.length); tzx.writeBytes(turbo);
    // 0x12: a tone; 0x13: pulses
    tzx.write(0x12); le16(tzx, 1500); le16(tzx, 400);
    tzx.write(0x13); tzx.write(3); le16(tzx, 700); le16(tzx, 800); le16(tzx, 900);
    // 0x14: pure data
    byte[] pure = random(64, 4);
    tzx.write(0x14); le16(tzx, 700); le16(tzx, 1400); tzx.write(8); le16(tzx, 300); le24(tzx, pure.length); tzx.writeBytes(pure);
    // 0x15: a direct recording, a sample every 79 T-states
    byte[] samples = random(40, 5);
    tzx.write(0x15); le16(tzx, 79); le16(tzx, 200); tzx.write(6); le24(tzx, samples.length); tzx.writeBytes(samples);
    // 0x20: a pause
    tzx.write(0x20); le16(tzx, 250);
    // 0x21, 0x22: a group around a tone
    tzx.write(0x21); text(tzx, "group");
    tzx.write(0x12); le16(tzx, 1000); le16(tzx, 50);
    tzx.write(0x22);
    // 0x24, 0x25: a tone three times
    tzx.write(0x24); le16(tzx, 3);
    tzx.write(0x12); le16(tzx, 1200); le16(tzx, 30);
    tzx.write(0x25);
    // 0x23: a jump over the next block
    tzx.write(0x23); le16(tzx, 2);
    tzx.write(0x12); le16(tzx, 3000); le16(tzx, 999);
    // 0x2B: the level set low; 0x2A: stop if a 48K
    tzx.write(0x2b); le32(tzx, 1); tzx.write(0);
    tzx.write(0x2a); le32(tzx, 0);
    // what only says something
    tzx.write(0x30); text(tzx, "a text");
    tzx.write(0x31); tzx.write(2); text(tzx, "a message");
    tzx.write(0x32); le16(tzx, 1 + 2 + 5); tzx.write(1); tzx.write(0); text(tzx, "title");
    tzx.write(0x33); tzx.write(1); tzx.write(0); tzx.write(1); tzx.write(0);
    tzx.write(0x35); tzx.writeBytes(String.format("%-16s", "custom").getBytes(StandardCharsets.ISO_8859_1)); le32(tzx, 3); tzx.writeBytes(new byte[]{1, 2, 3});
    // the rest of a standard pair, after the stop
    byte[] data = romBlock(0xff, random(256, 6));
    tzx.write(0x10); le16(tzx, 500); le16(tzx, data.length); tzx.writeBytes(data);
    Files.write(HERE.resolve("every-block.tzx"), tzx.toByteArray());

    ByteArrayOutputStream calls = new ByteArrayOutputStream();
    calls.writeBytes(new byte[]{'Z', 'X', 'T', 'a', 'p', 'e', '!', 0x1a, 1, 20});
    // 0x26: call the two tones after the stop, twice; 0x27 returns
    calls.write(0x26); le16(calls, 2); le16(calls, 3); le16(calls, 3);
    calls.write(0x20); le16(calls, 0);
    calls.write(0x12); le16(calls, 1100); le16(calls, 20);
    calls.write(0x12); le16(calls, 900); le16(calls, 20);
    calls.write(0x27);
    Files.write(HERE.resolve("call-and-stop.tzx"), calls.toByteArray());
  }
}
