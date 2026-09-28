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

import com.fpetrola.oozx.speccy.modules.scheduler.Task;
import com.fpetrola.oozx.speccy.modules.tape.Tape;
import com.fpetrola.oozx.speccy.modules.tape.TapeSettingsType;
import com.fpetrola.oozx.speccy.modules.timer.Speed;
import com.fpetrola.oozx.speccy.modules.timer.Timer;
import com.fpetrola.oozx.speccy.modules.z80.SpectrumZ80Clock;

import java.io.File;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What a tape sounds like: every level the tape puts on the EAR line and for how long, played
 * through from the first block to the last with the clock taken from edge to edge, and nothing
 * else running. A stop is pressed on again, as a person would, until there is nothing left.
 */
final class Signal {

  static final int MOST_EDGES = 4_000_000;
  static final int FIRST = 60;

  static String of(File file) throws Exception {
    SpectrumZ80Clock clock = new SpectrumZ80Clock();
    Remembering scheduler = new Remembering(clock);
    Timer timer = new Timer(scheduler, null, null, new Speed(), clock, () -> null);
    Tape tape = new Tape(new TapeSettingsType(), clock, scheduler, timer);
    if (!tape.insert(file)) {
      return "inserted = false\n";
    }
    List<String> lines = new ArrayList<>();
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    int stops = 0;
    long last = 0;
    int edges = 0;
    String crashed = null;
    try {
      if (!tape.play(false)) {
        return "inserted = true\nplays = false\n";
      }
    } catch (RuntimeException fell) {
      return "inserted = true\ncrashed = " + fell.getClass().getSimpleName() + " when it started\n";
    }
    while (edges < MOST_EDGES && crashed == null) {
      Optional<Map.Entry<Task, Long>> edge = scheduler.next("Edge");
      if (edge.isEmpty() || !tape.isTapePlaying()) {
        boolean again;
        try {
          again = tape.play(false);
        } catch (RuntimeException fell) {
          crashed = fell.getClass().getSimpleName() + " when played again";
          break;
        }
        if (!again) break;
        stops++;
        say(lines, digest, "play at block " + tape.getSelectedBlock());
        continue;
      }
      long at = edge.get().getValue();
      clock.setTStates((int) at);
      scheduler.ran(edge.get().getKey());
      try {
        edge.get().getKey().run(at);
      } catch (RuntimeException fell) {
        crashed = fell.getClass().getSimpleName() + " at edge " + edges;
      }
      say(lines, digest, (at - last) + " " + tape.getEarBit());
      last = at;
      edges++;
    }
    StringBuilder text = new StringBuilder("inserted = true\n")
        .append("edges = ").append(edges).append('\n')
        .append("stops = ").append(stops).append('\n')
        .append("length = ").append(last).append('\n')
        .append(crashed == null ? "" : "crashed = " + crashed + "\n")
        .append("digest = ").append(HexFormat.of().formatHex(digest.digest(), 0, 12)).append('\n');
    lines.stream().limit(FIRST).forEach(line -> text.append(line).append('\n'));
    return text.toString();
  }

  private static void say(List<String> lines, MessageDigest digest, String line) {
    if (lines.size() < FIRST) lines.add(line);
    digest.update((line + "\n").getBytes());
  }
}
