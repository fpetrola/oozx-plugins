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

/**
 * A game mapped as it is played, a frame at a time: what its memory says of the room and of the
 * player, turned into rooms and the ways between them. Leaving is noticed as the room's memory
 * starts changing, arriving when it has settled. The side left by comes from the leap the game
 * makes the player's position take to put them at the far door, which may come before the room
 * changes, with it, or only after, as some games change the room first: a way not told by the
 * arrival is still looked for while the player stands where they were put.
 */
public final class Mapper {

  public interface Listener {
    default void leaving(PlayerVariable.Side said) {
    }

    default void arrived(PlayerVariable.Side leftBy) {
    }

    default void wayToldLate(PlayerVariable.Side side) {
    }
  }

  private final RoomVariable roomVariable;
  private final PlayerVariable playerVariable;
  private final Rooms rooms;
  private final Listener listener;
  private PlayerVariable.Side leftBy;
  private boolean wayPending;

  public Mapper(RoomVariable roomVariable, PlayerVariable playerVariable, Rooms rooms, Listener listener) {
    this.roomVariable = roomVariable;
    this.playerVariable = playerVariable;
    this.rooms = rooms;
    this.listener = listener;
  }

  /** The screen as the frame left it and the keys the game saw held during it. */
  public void frame(byte[] screen, long keys) {
    boolean arrived = roomVariable.frame(screen);
    if (roomVariable.leaving()) {
      leftBy = playerVariable.sideOfLeaving();
      listener.leaving(leftBy);
    }
    if (arrived) {
      if (leftBy == null) leftBy = playerVariable.sideOfLeaving();
      rooms.arrive(roomVariable.survivors(), roomVariable.memoryOnArrival(), screen, leftBy);
      playerVariable.roomEntered(leftBy != null);
      wayPending = leftBy == null;
      listener.arrived(leftBy);
      leftBy = null;
    } else if (wayPending) {
      boolean still = playerVariable.stillSinceArriving();
      PlayerVariable.Side late = still ? playerVariable.sideOfLeaving() : null;
      if (late != null) {
        rooms.lastWayWas(late);
        listener.wayToldLate(late);
      }
      if (late != null || !still) {
        wayPending = false;
        playerVariable.waySettled();
      }
    }
    playerVariable.frame(keys);
    if (playerVariable.movedNow()) rooms.played();
  }
}
