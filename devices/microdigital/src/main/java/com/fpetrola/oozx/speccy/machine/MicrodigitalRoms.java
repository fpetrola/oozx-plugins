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

package com.fpetrola.oozx.speccy.machine;

import com.fpetrola.oozx.config.RomsOfItsOwn;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** What these machines are made with, which travels with the machines themselves. */
public class MicrodigitalRoms implements RomsOfItsOwn {

  @Override
  public Map<String, List<String>> files() {
    return Map.ofEntries(
        Map.entry("Tk90x", List.of("tk90x.rom")),
        Map.entry("Tk95", List.of("tk95.rom")));
  }

  @Override
  public Map<String, Map<String, List<String>>> sets() {
    Map<String, Map<String, List<String>>> sets = new LinkedHashMap<>();
    Map<String, List<String>> tk90x = new LinkedHashMap<>();
    tk90x.put("Portuguese", List.of("tk90x.rom"));
    tk90x.put("Spanish", List.of("tk90x-spanish.rom"));
    sets.put("Tk90x", tk90x);
    Map<String, List<String>> tk95 = new LinkedHashMap<>();
    tk95.put("Portuguese", List.of("tk95.rom"));
    tk95.put("Spanish", List.of("tk95-spanish.rom"));
    sets.put("Tk95", tk95);
    return sets;
  }
}
