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

import com.fpetrola.oozx.rzx.RzxSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

/**
 * What the window does while the game is played, done to a recording standing in for the player:
 * the window's own live map, its twin stepped here so the map is graded against the game's own
 * screen number, as the second pass over the recording is.
 */
class AMapMadeAgainWhileThePlayerPlaysTest {
  @Test
  @Timeout(value = 25, unit = TimeUnit.MINUTES)
  void theTwinsMapIsTheSecondPassesMap() throws Exception {
    RzxSession live = Mapping.open(DynamiteDanIsMappedTest.RECORDING);
    DynamiteDanIsMappedTest.Grading[] grading = {null};
    LiveMap[] map = {null};
    int[] startedAt = new int[4], starts = {0}, frames = {0};
    map[0] = new LiveMap(live.getSpeccy(), new LiveMap.Twins() {
      @Override
      public Mapper.Listener listener() {
        grading[0] = new DynamiteDanIsMappedTest.Grading();
        return new Mapper.Listener() {
          @Override
          public void arrived(PlayerVariable.Side leftBy) {
            Twin twin = map[0].twin();
            grading[0].arrived(twin.machine(), twin.frame(), twin.rooms, twin.playerVariable, twin.roomVariable, leftBy);
          }

          @Override
          public void wayToldLate(PlayerVariable.Side side) {
            grading[0].wayToldLate(side);
          }
        };
      }

      @Override
      public void started(Twin twin) {
        startedAt[Math.min(starts[0], 3)] = frames[0];
        starts[0]++;
      }
    });
    try {
      for (; frames[0] < 200_000 && live.playFrame(); frames[0]++) {
        Twin twin = map[0].twin();
        if (twin != null) while (twin.step()) ;
      }
    } finally {
      live.release();
    }
    Twin twin = map[0].twin();
    System.out.printf("TWIN: started %d times, at frames %s; followed to frame %d of %d, %d behind%n", starts[0], java.util.Arrays.toString(startedAt), twin.frame(), frames[0], twin.behind());
    grading[0].report(twin.rooms);
    map[0].stop();
  }
}
