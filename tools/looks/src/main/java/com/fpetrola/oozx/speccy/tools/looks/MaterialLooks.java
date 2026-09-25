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

import dev.crystal.plugins.api.Offers;

import javax.swing.UIManager;
import java.util.List;

@Offers("Change the look: Material")
public class MaterialLooks extends FromThisPlugin {

  public String family() {
    return "Material";
  }

  public List<String> names() {
    return List.of("MaterialLite", "MaterialOceanic", "JMarsDark");
  }

  public void wear(String name) throws Exception {
    ClassLoader ours = getClass().getClassLoader();
    withOurClasses(() -> UIManager.setLookAndFeel(new mdlaf.MaterialLookAndFeel(
        (mdlaf.themes.MaterialTheme) Class.forName("mdlaf.themes." + name + "Theme", true, ours)
            .getDeclaredConstructor().newInstance())));
  }
}
