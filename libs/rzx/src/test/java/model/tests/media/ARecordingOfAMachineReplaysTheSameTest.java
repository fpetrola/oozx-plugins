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

package model.tests.media;

import com.fpetrola.oozx.Speccy;
import com.fpetrola.oozx.rzx.RzxRecorder;
import com.fpetrola.oozx.rzx.RzxSession;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What a recorder keeps of a machine being played, a session plays again to the same screen:
 * measured by playing a known recording, recording the machine it drives from some frame on, and
 * playing that recording on a second machine.
 */
class ARecordingOfAMachineReplaysTheSameTest {
  private static final int SKIPPED = 1000, RECORDED = 3000;
  private RzxSession played;

  private static File recording() {
    try {
      return model.harness.TestFiles.testFile("/rzx/jsw-full.rzx");
    } catch (Exception e) {
      throw new IllegalStateException("the jsw-full.rzx test resource is missing", e);
    }
  }

  /** A session without a picture to paint or sound to pace it: the frames go as fast as they come. */
  private static RzxSession quietly(File recording) {
    RzxSession session = RzxSession.open(recording);
    session.getSpeccy().picture.active = false;
    session.getSpeccy().sound.output.enabled = false;
    session.getSpeccy().sound.setCard(new com.fpetrola.oozx.speccy.modules.sound.SilentSoundDevice());
    return session;
  }

  @Test
  @org.junit.jupiter.api.Timeout(value = 10, unit = java.util.concurrent.TimeUnit.MINUTES)
  void the_recording_of_a_played_machine_replays_to_the_same_screen() throws Exception {
    played = quietly(recording());
    for (int frame = 0; frame < SKIPPED; frame++) played.playFrame();
    RzxRecorder recorder = new RzxRecorder(played.getSpeccy(), answer -> {
      played.whenRead((port, value) -> answer.accept(value));
      return () -> { };
    });
    java.util.List<byte[]> played = new java.util.ArrayList<>();
    for (int frame = 0; frame < RECORDED; frame++) {
      this.played.playFrame();
      played.add(screenOf(this.played.getSpeccy()));
    }
    recorder.stop();
    Path copy = Files.createTempFile("recorded", ".rzx");
    copy.toFile().deleteOnExit();
    recorder.writeTo(copy);
    int kept = recorder.frames();

    RzxSession replayed = quietly(copy.toFile());
    int[] matching = new int[3], firstDiffering = {-1, -1, -1};
    int frames = 0;
    while (replayed.playFrame()) {
      byte[] now = screenOf(replayed.getSpeccy());
      for (int offset = 0; offset < 3; offset++) {
        int at = frames + offset;
        if (at >= played.size()) continue;
        if (java.util.Arrays.equals(now, played.get(at))) matching[offset]++;
        else if (firstDiffering[offset] < 0) firstDiffering[offset] = frames;
      }
      frames++;
    }
    System.out.printf("recorded %d frames, the copy played %d; screens matching at offset 0: %d (first differing %d), offset 1: %d (first %d), offset 2: %d (first %d)%n",
        kept, frames, matching[0], firstDiffering[0], matching[1], firstDiffering[1], matching[2], firstDiffering[2]);
    assertEquals(RECORDED - 1, kept, "frames kept: all but the one the recorder started in");
    assertEquals(kept - 1, frames, "frames the copy plays");
    assertEquals(frames, Math.max(matching[0], Math.max(matching[1], matching[2])), "frames whose screen is the played machine's");
    this.played.release();
    replayed.release();
  }

  private static byte[] screenOf(Speccy speccy) {
    byte[] screen = new byte[6912];
    for (int offset = 0; offset < screen.length; offset++) screen[offset] = (byte) speccy.memory.peek(0x4000 + offset);
    return screen;
  }
}
