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

import javax.swing.*;
import javax.swing.AbstractAction;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

/**
 * Dialog for viewing downloaded files from the internet
 */
public class DownloadViewerDialog extends JDialog {
    private static final int DEFAULT_WIDTH = 800;
    private static final int DEFAULT_HEIGHT = 600;

    public DownloadViewerDialog(Frame owner, String downloadUrl, String fileName) {
        super(owner, "Download Viewer - " + fileName, false);
        
        setSize(DEFAULT_WIDTH, DEFAULT_HEIGHT);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        setModal(true);
        // Setup Escape key to close
        setupEscapeToClose();
        
        // Create content panel
        JPanel contentPanel = new JPanel(new BorderLayout(10, 10));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Show loading message
        JLabel loadingLabel = new JLabel("Loading " + fileName + "...");
        loadingLabel.setHorizontalAlignment(JLabel.CENTER);
        contentPanel.add(loadingLabel, BorderLayout.CENTER);
        
        add(contentPanel);
        
        // Load file asynchronously
        SwingWorker<Component, Void> worker = new SwingWorker<Component, Void>() {
            @Override
            protected Component doInBackground() throws Exception {
                return createViewerComponent(downloadUrl, fileName);
            }
            
            @Override
            protected void done() {
                try {
                    Component viewer = get();
                    contentPanel.removeAll();
                    contentPanel.add(viewer, BorderLayout.CENTER);
                    contentPanel.revalidate();
                    contentPanel.repaint();
                } catch (Exception e) {
                    contentPanel.removeAll();
                    JTextArea errorArea = new JTextArea();
                    errorArea.setText("Error loading file:\n" + e.getMessage());
                    errorArea.setEditable(false);
                    contentPanel.add(new JScrollPane(errorArea), BorderLayout.CENTER);
                    contentPanel.revalidate();
                    contentPanel.repaint();
                }
            }
        };
        worker.execute();
    }
    
    /**
     * Create appropriate viewer component based on file type
     */
    private Component createViewerComponent(String downloadUrl, String fileName) throws IOException {
        String lowerFileName = fileName.toLowerCase();
        
        // Determine file type and create appropriate viewer
        if (isImageFile(lowerFileName)) {
            return createImageViewer(downloadUrl);
        } else if (isTextFile(lowerFileName)) {
            return createTextViewer(downloadUrl);
        } else {
            return createScreenViewer(downloadUrl);
        }
    }

    /** Whether this window shows that file; what it does not, the person's own programs open: a PDF, a sound. */
    static boolean shows(String fileName) {
        String lower = fileName.toLowerCase();
        return lower.endsWith(".scr") || lower.matches(".*\\.(jpg|jpeg|png|gif|bmp|txt|md|html|htm|xml|json|csv|log)");
    }

    private Component createScreenViewer(String downloadUrl) throws IOException {
        try (InputStream in = new URL(downloadUrl).openStream()) {
            return new JScrollPane(new JLabel(new ImageIcon(screen(in.readAllBytes()).getScaledInstance(768, 576, Image.SCALE_REPLICATE))));
        }
    }

    /** A Spectrum screen as it is kept: 6144 bytes of pixels in the display's own order, then 768 of colours, a cell each. */
    static java.awt.image.BufferedImage screen(byte[] screen) throws IOException {
        if (screen.length < 6912) throw new IOException("not a Spectrum screen: " + screen.length + " bytes");
        java.awt.image.BufferedImage picture = new java.awt.image.BufferedImage(256, 192, java.awt.image.BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 192; y++) {
            int line = ((y & 0xC0) << 5) | ((y & 0x07) << 8) | ((y & 0x38) << 2);
            for (int x = 0; x < 256; x++) {
                int colours = screen[6144 + (y / 8) * 32 + x / 8] & 0xFF, level = (colours & 0x40) != 0 ? 0xFF : 0xD8;
                boolean ink = (screen[line + x / 8] & (0x80 >> (x & 7))) != 0;
                int colour = ink ? colours & 7 : (colours >> 3) & 7;
                picture.setRGB(x, y, ((colour & 2) != 0 ? level << 16 : 0) | ((colour & 4) != 0 ? level << 8 : 0) | ((colour & 1) != 0 ? level : 0));
            }
        }
        return picture;
    }

    private Component createImageViewer(String downloadUrl) throws IOException {
        URL url = new URL(downloadUrl);
        ImageIcon icon = new ImageIcon(url);
        
        if (icon.getIconWidth() > 0 && icon.getIconHeight() > 0) {
            // Scale if too large
            int maxWidth = 750;
            int maxHeight = 550;
            int width = icon.getIconWidth();
            int height = icon.getIconHeight();
            
            if (width > maxWidth || height > maxHeight) {
                double scaleX = (double) maxWidth / width;
                double scaleY = (double) maxHeight / height;
                double scale = Math.min(scaleX, scaleY);
                width = (int) (width * scale);
                height = (int) (height * scale);
                
                Image scaledImage = icon.getImage().getScaledInstance(width, height, Image.SCALE_SMOOTH);
                icon = new ImageIcon(scaledImage);
            }
        }
        
        JLabel imageLabel = new JLabel(icon);
        imageLabel.setHorizontalAlignment(JLabel.CENTER);
        imageLabel.setVerticalAlignment(JLabel.CENTER);
        
        JScrollPane scrollPane = new JScrollPane(imageLabel);
        return scrollPane;
    }
    
    private Component createTextViewer(String downloadUrl) throws IOException {
        URL url = new URL(downloadUrl);
        URLConnection conn = url.openConnection();
        InputStream inputStream = conn.getInputStream();
        
        StringBuilder content = new StringBuilder();
        byte[] buffer = new byte[1024];
        int bytesRead;
        
        try {
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                content.append(new String(buffer, 0, bytesRead));
            }
        } finally {
            inputStream.close();
        }
        
        JTextArea textArea = new JTextArea();
        textArea.setText(content.toString());
        textArea.setEditable(false);
        textArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        
        return new JScrollPane(textArea);
    }
    
    private boolean isImageFile(String fileName) {
        return fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") || 
               fileName.endsWith(".png") || fileName.endsWith(".gif") ||
               fileName.endsWith(".bmp") || fileName.endsWith(".webp");
    }
    
    private boolean isTextFile(String fileName) {
        return fileName.endsWith(".txt") || fileName.endsWith(".md") ||
               fileName.endsWith(".html") || fileName.endsWith(".htm") ||
               fileName.endsWith(".xml") || fileName.endsWith(".json") ||
               fileName.endsWith(".csv") || fileName.endsWith(".log");
    }
    
    private void setupEscapeToClose() {
        KeyStroke escapeKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(escapeKeyStroke, "closeDialog");
        getRootPane().getActionMap().put("closeDialog", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });
    }
}
