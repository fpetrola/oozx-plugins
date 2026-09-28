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

package com.fpetrola.oozx.snapshots;

/**
 * Peripherals that are there on every machine and hold nothing a snapshot carries: saying that a
 * format left them out would only hide what it did leave out.
 */
final class Quiet {

  static final Class<?>[] PARTS = {
      com.fpetrola.oozx.speccy.modules.snapshot.Snapshots.class,
      com.fpetrola.oozx.speccy.devices.ula.UlaPeripheral.class,
      com.fpetrola.oozx.speccy.devices.memory.Spec128MemoryPeripheral.class,
      com.fpetrola.oozx.speccy.devices.memory.SpecPlus3MemoryPeripheral.class,
  };

  private Quiet() {
  }
}
