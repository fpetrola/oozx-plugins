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

package com.fpetrola.oozx.speccy.modules.tape.cassette;

/**
 * One step of a block's sound: what happens to the level, and how long until the next step - or
 * nothing, and then the next step happens at the same moment.
 */
public record Step(Level level, int tstates, boolean waits) {

  /** What a step does to the level on the EAR line. */
  public enum Level {
    /** Turns it over: an edge. */
    TOGGLE,
    /** Leaves it: a pulse that starts without an edge. */
    KEEP,
    HIGH,
    LOW,
    /** The level a silent line rests at, which a setting can turn over. */
    REST
  }

  public static Step of(Level level, int tstates) {
    return new Step(level, tstates, true);
  }

  /** A step that does not wait: the next one happens at the same moment. */
  public static Step now(Level level) {
    return new Step(level, 0, false);
  }
}
