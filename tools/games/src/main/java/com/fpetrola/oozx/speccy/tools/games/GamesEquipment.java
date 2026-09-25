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

package com.fpetrola.oozx.speccy.tools.games;

import dev.crystal.plugins.api.Offers;

import com.fpetrola.oozx.speccy.devices.DeskEquipment;

import javax.swing.JInternalFrame;

/** The game browser, offered like any other equipment: found, not named by the application. */
@Offers("Browse the game catalogue")
public class GamesEquipment implements DeskEquipment {
  public String name() {
    return "Game Browser";
  }

  public JInternalFrame open() {
    return new GamesFrame();
  }

  /** What layouts saved before this was a plugin call it. */
  public String keeps() {
    return "GAME_BROWSER";
  }

  /** Su boton en la barra del escritorio. */
  @Override
  public javax.swing.Icon icon() {
    return com.fpetrola.oozx.speccy.windows.Widgets.loadIcon("1F579.svg");
  }
}
