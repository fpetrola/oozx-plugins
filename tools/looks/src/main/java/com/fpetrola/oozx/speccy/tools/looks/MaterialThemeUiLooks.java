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

package com.fpetrola.oozx.speccy.tools.looks;

import java.util.List;

public class MaterialThemeUiLooks extends FromThisPlugin {

  public String family() {
    return "Material theme UI";
  }

  public List<String> names() {
    return spacedAll("ArcDark", "AtomOneDark", "AtomOneLight", "Dracula", "GitHub", "GitHubDark",
        "LightOwl", "MaterialDarker", "MaterialDeepOcean", "MaterialLighter", "MaterialOceanic",
        "MaterialPalenight", "MonokaiPro", "Moonlight", "NightOwl", "SolarizedDark", "SolarizedLight");
  }

  public void wear(String name) throws Exception {
    byClass("com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMT%sIJTheme".formatted(unspaced(name)));
  }
}
