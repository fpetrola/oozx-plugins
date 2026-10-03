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

/**
 * The game mapped again from where the recording of it starts, knowing what the game being
 * played has taught so far - which bytes are the room, which the player - on a machine of its
 * own that plays what the recorder keeps: it catches up with the game and then follows it a
 * frame behind, so the map it draws is the one a second pass over a recording gives, while the
 * player goes on playing.
 */
final class Twin {
  private static final int CAUGHT_UP = 50;
  private final RzxRecorder recorder;
  private final RzxSession session;
  private final InputsSeen inputs;
  private final Mapper mapper;
  final Rooms rooms = new Rooms();
  final RoomVariable roomVariable;
  final PlayerVariable playerVariable;
  /** What it was taught of the player: worth starting over once the game being played says otherwise. */
  final String taught;
  private volatile boolean alive = true;
  private Thread thread;

  Twin(RzxRecorder recorder, RoomVariable learntRoom, PlayerVariable learntPlayer, Mapper.Listener listener) {
    this.recorder = recorder;
    session = RzxSession.follow(recorder).quiet();
    Speccy speccy = session.getSpeccy();
    inputs = new InputsSeen(speccy);
    roomVariable = new RoomVariable(speccy.memory::peek, learntRoom.survivors());
    playerVariable = new PlayerVariable(speccy.memory::peek);
    playerVariable.learnedFrom(learntPlayer);
    taught = learntPlayer.taught();
    mapper = new Mapper(roomVariable, playerVariable, rooms, listener);
  }

  Speccy machine() {
    return session.getSpeccy();
  }

  int frame() {
    return session.getPlayback().getFrameIndex();
  }

  /** How far behind the game it is, in frames. */
  int behind() {
    return recorder.frames() - frame();
  }

  /** Close enough behind the game for its map to be the one worth showing. */
  boolean caughtUp() {
    return behind() <= CAUGHT_UP;
  }

  /** Plays and maps one frame; false when there is none to play yet. */
  boolean step() {
    if (!alive || !session.playFrame()) return false;
    mapper.frame(Screens.of(session.getSpeccy().memory::peek), inputs.frame());
    return true;
  }

  /** Keeps up with the game on a thread of its own, as fast as the frames come, and lets the machine go once stopped. */
  void follow() {
    thread = new Thread(() -> {
      while (alive) {
        if (!step()) {
          try {
            Thread.sleep(4);
          } catch (InterruptedException e) {
            break;
          }
        }
      }
      machine().end();
    }, "map-twin");
    thread.setDaemon(true);
    thread.start();
  }

  /** From any thread: a twin following the game lets the machine go itself, once it is out of the frame it is in. */
  void stop() {
    alive = false;
    if (thread == null) machine().end();
  }
}
