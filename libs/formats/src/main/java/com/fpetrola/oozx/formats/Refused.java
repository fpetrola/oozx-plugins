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

package com.fpetrola.oozx.formats;

/**
 * Why a file could not be read or a machine written, thrown from wherever that was found - deep
 * inside a table, a rule, a page - and turned into the emulator's SnapshotException once, by the
 * format. Unchecked for that reason.
 */
public class Refused extends RuntimeException {

  public Refused(String reason) {
    super(reason);
  }

  public Refused(String reason, Throwable cause) {
    super(reason, cause);
  }
}
