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
import com.fpetrola.oozx.speccy.modules.z80.PcTraps;

/**
 * The map of a game being played: made as the game goes and, once the player is believed, made
 * again from the start on a twin machine that plays what was recorded since this began, knowing
 * what the game has taught - and once more whenever the row byte learnt since differs. The
 * twin's map is the one shown once it has caught up with the game.
 */
final class LiveMap {
  /** What a twin is told and what becomes of it: left to follow the game on a thread of its own unless said otherwise. */
  interface Twins {
    default Mapper.Listener listener() {
      return new Mapper.Listener() {
      };
    }

    default void started(Twin twin) {
      twin.follow();
    }
  }

  private final Speccy machine;
  private final Rooms rooms = new Rooms();
  private final RoomVariable roomVariable;
  private final PlayerVariable playerVariable;
  private final InputsSeen inputs;
  private final Mapper mapper;
  private final RzxRecorder recorder;
  private final Twins twins;
  private final PcTraps.Watch watching;
  private volatile Twin twin;
  private long frame = -1;

  LiveMap(Speccy machine, Twins twins) {
    this.machine = machine;
    this.twins = twins;
    inputs = new InputsSeen(machine);
    roomVariable = new RoomVariable(machine.memory::peek);
    playerVariable = new PlayerVariable(machine.memory::peek);
    recorder = new RzxRecorder(machine);
    mapper = new Mapper(roomVariable, playerVariable, rooms, new Mapper.Listener() {
    });
    watching = machine.cpu.beforeFetch().watch(0x0000, 0xffff, pc -> lookAtTheScreen());
  }

  /** Once a frame, the first time the processor fetches in a new one: the screen as the last left it and the keys the game saw. */
  private void lookAtTheScreen() {
    long now = machine.machine.current.frameCount();
    if (now == frame) return;
    frame = now;
    mapper.frame(Screens.of(machine.memory::peek), inputs.frame());
    mapAgainIfReady();
  }

  private void mapAgainIfReady() {
    if (!recorder.started() || !playerVariable.trusted()) return;
    Twin again = twin;
    if (again != null && again.rowByte == playerVariable.rowByte()) return;
    if (again != null) again.stop();
    twin = again = new Twin(recorder, roomVariable, playerVariable, twins.listener());
    twins.started(again);
  }

  /** The twin mapping the game again from the start, or null until the player is believed. */
  Twin twin() {
    return twin;
  }

  boolean playerKnown() {
    return playerVariable.known();
  }

  /** The rooms to draw: the map made again from the start once it has caught up with the game, the one made as the game went until then. */
  Rooms shown() {
    Twin again = twin;
    return again != null && again.caughtUp() ? again.rooms : rooms;
  }

  void stop() {
    watching.off();
    inputs.off();
    recorder.stop();
    Twin again = twin;
    twin = null;
    if (again != null) again.stop();
  }
}
