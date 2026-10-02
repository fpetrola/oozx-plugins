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

  @Test
  @org.junit.jupiter.api.Timeout(value = 10, unit = java.util.concurrent.TimeUnit.MINUTES)
  void the_recording_of_a_played_machine_replays_to_the_same_screen() throws Exception {
    played = RzxSession.open(recording()).quiet();
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

    RzxSession replayed = RzxSession.open(copy.toFile()).quiet();
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

  /** A session following the recorder catches up with the played machine and then waits for it, frame by frame. */
  @Test
  @org.junit.jupiter.api.Timeout(value = 10, unit = java.util.concurrent.TimeUnit.MINUTES)
  void a_session_following_the_recorder_catches_up_and_waits_for_it() throws Exception {
    played = RzxSession.open(recording()).quiet();
    for (int frame = 0; frame < SKIPPED; frame++) played.playFrame();
    RzxRecorder recorder = new RzxRecorder(played.getSpeccy(), answer -> {
      played.whenRead((port, value) -> answer.accept(value));
      return () -> { };
    });
    for (int frame = 0; frame < 200; frame++) played.playFrame();
    RzxSession twin = RzxSession.follow(recorder).quiet();
    java.util.List<byte[]> seen = new java.util.ArrayList<>();
    int caughtUp = 0, matching = 0, compared = 0;
    for (int frame = 0; frame < RECORDED; frame++) {
      played.playFrame();
      seen.add(screenOf(played.getSpeccy()));
      while (twin.playFrame()) {
        // The twin's frames count from the recorder's start, 200 frames before this loop's.
        int at = twin.getPlayback().getFrameIndex() - 200;
        if (at >= 0 && at < seen.size()) {
          compared++;
          if (java.util.Arrays.equals(screenOf(twin.getSpeccy()), seen.get(at))) matching++;
        }
      }
      caughtUp++;
    }
    recorder.stop();
    System.out.printf("twin: played %d frames, compared %d, matching %d%n", twin.getPlayback().getFrameIndex(), compared, matching);
    assertEquals(RECORDED, caughtUp, "times the twin had nothing left to play");
    assertEquals(RECORDED - 1, compared, "twin frames held against the played machine's");
    assertEquals(compared, matching, "twin frames whose screen is the played machine's");
    assertEquals(RECORDED + 200 - 2, twin.getPlayback().getFrameIndex(), "frames the twin played: all but the recorder's first and the one it cannot touch");
    played.release();
    twin.release();
  }

  private static byte[] screenOf(Speccy speccy) {
    byte[] screen = new byte[6912];
    for (int offset = 0; offset < screen.length; offset++) screen[offset] = (byte) speccy.memory.peek(0x4000 + offset);
    return screen;
  }
}
