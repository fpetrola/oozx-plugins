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

package com.fpetrola.oozx.speccy.devices.uspeech;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The chip against Fuse's own. Fuse's sp0256.c, built into src/test/fuse/harness.c, was run once
 * over each of these ROMs and the script beside them, printing every sample and every answer to
 * "busy?"; make-goldens.sh does it again. The ROMs are two K of noise with more zeros in it than
 * noise has, so that the microsequencer halts now and then and takes the next allophone: between
 * them every instruction it has runs dozens of times. This prints the same and compares digests,
 * and leaves what it printed in target/ when they differ, to be put next to what Fuse prints.
 */
class Sp0256AsFuseHasItTest {

  private static final int FRAME = 69888, PROCESSOR = 3500000;

  static Stream<Arguments> roms() throws IOException {
    return text("expected.txt").lines().map(line -> line.split(" "))
        .map(rom -> Arguments.of(rom[0], rom[1], Integer.parseInt(rom[2])));
  }

  @ParameterizedTest
  @MethodSource("roms")
  void saysWhatFuseSays(String rom, String digest, int lines) throws Exception {
    StringBuilder printed = new StringBuilder();
    long[] frameStart = {0};
    Sp0256 chip = new Sp0256(bytes(rom), (tstates, level) ->
        printed.append("S ").append(frameStart[0] + tstates).append(' ').append(level).append('\n'));
    chip.clock(3050000, PROCESSOR);
    int frame = 0;
    for (String line : text("script.txt").lines().toList()) {
      String[] step = line.split(" ");
      for (int until = Integer.parseInt(step[0]); frame < until; frame++) {
        chip.endFrame(FRAME);
        frameStart[0] += FRAME;
      }
      long at = Long.parseLong(step[1]);
      int argument = Integer.parseInt(step[3]);
      switch (step[2]) {
        case "P" -> chip.play(at, argument);
        case "C" -> chip.changeClock(at, argument, PROCESSOR);
        case "B" -> {
          boolean busy = chip.busy(at);
          printed.append("B ").append(frameStart[0] + at).append(' ').append(busy ? 1 : 0).append('\n');
        }
      }
    }
    String said = printed.toString();
    String saidDigest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(said.getBytes(StandardCharsets.US_ASCII)));
    if (!saidDigest.equals(digest)) {
      Files.writeString(Path.of("target", "sp0256-" + rom + ".txt"), said);
    }
    assertEquals(lines, said.lines().count(), rom + ": not as many samples and answers as Fuse");
    assertEquals(digest, saidDigest, rom + ": not the same samples as Fuse");
  }

  private static byte[] bytes(String resource) throws IOException {
    try (InputStream in = Sp0256AsFuseHasItTest.class.getResourceAsStream("/sp0256/" + resource)) {
      return in.readAllBytes();
    }
  }

  private static String text(String resource) throws IOException {
    return new String(bytes(resource), StandardCharsets.US_ASCII);
  }
}
