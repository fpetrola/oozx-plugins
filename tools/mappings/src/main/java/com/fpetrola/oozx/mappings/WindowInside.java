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

package com.fpetrola.oozx.mappings;

import dev.crystal.plugins.api.RoleInterface;

import javax.swing.JInternalFrame;
import java.util.Map;

/**
 * What a window of the desk has inside that the desk does not see, by name, and the same back on the
 * window opened again. One for each kind of window, each only with the plugin that has it, and saying
 * nothing of a window it does not know.
 */
@RoleInterface
public interface WindowInside {

  void keep(JInternalFrame window, Map<String, String> kept);

  void bringBack(JInternalFrame window, Map<String, String> kept);
}
