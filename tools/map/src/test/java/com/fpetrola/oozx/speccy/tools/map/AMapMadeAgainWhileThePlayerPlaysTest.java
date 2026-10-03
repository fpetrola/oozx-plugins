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

package com.fpetrola.oozx.speccy.tools.map;

import com.fpetrola.oozx.Speccy;
import com.fpetrola.oozx.rzx.RzxRecorder;
import com.fpetrola.oozx.rzx.RzxSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

/**
 * What the window does while the game is played, done to a recording standing in for the player:
 * mapped as it goes while recorded, then mapped again from the start on a twin machine once the
 * player is believed, and once more when up and down become known. The twin's map is held to the
 * game's own screen number, as the second pass over the recording is.
 */
class AMapMadeAgainWhileThePlayerPlaysTest {

  @Test
  @Timeout(value = 25, unit = TimeUnit.MINUTES)
  void theTwinsMapIsTheSecondPassesMap() throws Exception {
    RzxSession live = Mapping.open(DynamiteDanIsMappedTest.RECORDING);
    Speccy speccy = live.getSpeccy();
    InputsSeen inputs = new InputsSeen(speccy);
    RoomVariable roomVariable = new RoomVariable(speccy.memory::peek);
    PlayerVariable playerVariable = new PlayerVariable(speccy.memory::peek);
    Mapper mapper = new Mapper(roomVariable, playerVariable, new Rooms(), new Mapper.Listener() {
    });
    RzxRecorder recorder = new RzxRecorder(speccy, answer -> {
      live.whenRead((port, value) -> answer.accept(value));
      return () -> { };
    });
    Twin[] twin = {null};
    DynamiteDanIsMappedTest.Grading[] grading = {null};
    int[] startedAt = new int[4];
    int starts = 0, frames = 0;
    try {
      for (; frames < 200_000 && live.playFrame(); frames++) {
        mapper.frame(Screens.of(speccy.memory::peek), inputs.frame());
        if (recorder.started() && playerVariable.trusted() && (twin[0] == null || twin[0].rowByte != playerVariable.rowByte())) {
          if (twin[0] != null) twin[0].stop();
          grading[0] = new DynamiteDanIsMappedTest.Grading();
          twin[0] = new Twin(recorder, roomVariable, playerVariable, new Mapper.Listener() {
            @Override
            public void arrived(PlayerVariable.Side leftBy) {
              grading[0].arrived(twin[0].machine(), twin[0].frame(), twin[0].rooms, twin[0].playerVariable, twin[0].roomVariable, leftBy);
            }

            @Override
            public void wayToldLate(PlayerVariable.Side side) {
              grading[0].wayToldLate(side);
            }
          });
          startedAt[Math.min(starts, 3)] = frames;
          starts++;
        }
        if (twin[0] != null) while (twin[0].step()) ;
      }
    } finally {
      inputs.off();
      live.release();
    }
    System.out.printf("TWIN: started %d times, at frames %s; followed to frame %d of %d, %d behind%n", starts, java.util.Arrays.toString(startedAt), twin[0].frame(), frames, twin[0].behind());
    grading[0].report(twin[0].rooms);
    twin[0].stop();
  }
}
