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

import com.fpetrola.oozx.speccy.devices.Look;

import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import java.util.List;

/**
 * Lo comun a todas las familias: ponerse un look desde este plugin.
 * <p>
 * Las clases del look viven en el classloader de este jar, no en el de la aplicacion. Swing busca
 * las piezas con que pinta cada componente por nombre, y si no se le dice donde, las busca en el
 * de la aplicacion y no las encuentra: el look queda puesto y no pinta nada. Por eso se pone con
 * el classloader de aca en el hilo, y se le deja dicho a Swing donde buscar.
 */
abstract class FromThisPlugin implements Look {

  /** Pone lo que haga falta con el classloader de este plugin puesto en el hilo. */
  protected void withOurClasses(Wearing wearing) throws Exception {
    Thread current = Thread.currentThread();
    ClassLoader was = current.getContextClassLoader();
    ClassLoader ours = getClass().getClassLoader();
    current.setContextClassLoader(ours);
    try {
      wearing.wear();
      UIManager.getLookAndFeelDefaults().put("ClassLoader", ours);
    } finally {
      current.setContextClassLoader(was);
    }
  }

  /** Un look que se pone por el nombre de su clase, creado desde aca. */
  protected void byClass(String className) throws Exception {
    withOurClasses(() -> UIManager.setLookAndFeel((LookAndFeel) Class.forName(className, true,
        getClass().getClassLoader()).getDeclaredConstructor().newInstance()));
  }

  /** "GraphiteAqua" se lee "Graphite Aqua" en un menu; la clase sigue llamandose como se llama. */
  static String spaced(String name) {
    return name.replaceAll("(?<=[a-z0-9])(?=[A-Z])", " ");
  }

  static String unspaced(String name) {
    return name.replace(" ", "");
  }

  interface Wearing {
    void wear() throws Exception;
  }

  static List<String> spacedAll(String... names) {
    return java.util.Arrays.stream(names).map(FromThisPlugin::spaced).toList();
  }
}
