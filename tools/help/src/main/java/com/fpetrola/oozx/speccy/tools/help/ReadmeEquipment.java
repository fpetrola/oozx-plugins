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

package com.fpetrola.oozx.speccy.tools.help;

import dev.crystal.plugins.api.Offers;

import com.fpetrola.oozx.speccy.devices.Explains;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import javax.swing.JEditorPane;
import javax.swing.JInternalFrame;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.event.HyperlinkEvent;
import java.awt.Desktop;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** The wiki that travels in the application's jar, page by page: its sidebar on the left, the page asked for on the right. */
@Offers("Read the guide")
public class ReadmeEquipment implements Explains {
  private static final String STYLE = "body { font-family: Arial, sans-serif; margin: 20px; line-height: 1.6; color: #333; }"
      + "h1 { color: #1f77b4; border-bottom: 2px solid #1f77b4; padding-bottom: 10px; }"
      + "h2 { color: #ff7f0e; margin-top: 20px; }"
      + "h3 { color: #2ca02c; }"
      + "code { background-color: #f5f5f5; padding: 2px 6px; border-radius: 3px; font-family: 'Courier New'; }"
      + "pre { background-color: #f5f5f5; padding: 10px; border-radius: 5px; overflow-x: auto; }"
      + "pre code { background-color: transparent; padding: 0; }"
      + "a { color: #1f77b4; text-decoration: none; }"
      + "blockquote { border-left: 4px solid #ddd; padding-left: 15px; color: #666; margin-left: 0; }"
      + "table { border-collapse: collapse; width: 100%; }"
      + "th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }"
      + "th { background-color: #f5f5f5; }";

  public String name() {
    return "Guide";
  }

  public JInternalFrame open() {
    JEditorPane page = showing("Getting-Started"), pages = showing("_Sidebar");
    for (JEditorPane linked : List.of(page, pages)) linked.addHyperlinkListener(link -> {
      if (link.getEventType() == HyperlinkEvent.EventType.ACTIVATED) follow(link.getDescription(), page);
    });
    JInternalFrame frame = new JInternalFrame("Guide - OOZX", true, true, true, true);
    JSplitPane both = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(pages), new JScrollPane(page));
    both.setDividerLocation(230);
    frame.add(both);
    frame.setBounds(50, 50, 1000, 650);
    return frame;
  }

  private static JEditorPane showing(String name) {
    JEditorPane page = new JEditorPane("text/html", html(name));
    page.setEditable(false);
    page.setCaretPosition(0);
    return page;
  }

  /** A link to another page of the wiki shows it here; one that leaves the wiki goes to the browser. */
  private static void follow(String link, JEditorPane page) {
    try {
      if (link.contains(":")) Desktop.getDesktop().browse(URI.create(link));
      else if (!link.startsWith("#")) {
        page.setText(html(link));
        page.setCaretPosition(0);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static String html(String name) {
    List<org.commonmark.Extension> tables = List.of(TablesExtension.create());
    return "<html><head><style>" + STYLE + "</style></head><body>" + HtmlRenderer.builder().extensions(tables).build()
        .render(Parser.builder().extensions(tables).build().parse(page(name))) + "</body></html>";
  }

  private static String page(String name) {
    try (InputStream in = Explains.class.getClassLoader().getResourceAsStream("wiki/" + name + ".md")) {
      return in == null ? "This build carries no guide: [read it on the web](https://github.com/fpetrola/oozx/wiki/" + name + ")."
          : new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
