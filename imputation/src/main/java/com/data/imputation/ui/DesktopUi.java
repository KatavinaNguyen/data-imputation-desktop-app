package com.data.imputation.ui;

import com.data.imputation.service.TimeSeriesInterpolationService;
import org.springframework.stereotype.Component;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

@Component
public class DesktopUi {

    private final TimeSeriesInterpolationService interpolationService;
    private JTextField suffixField;
    private JLabel exampleLabel;

    // Neutral dark grey theme
    private static final Color BG_MAIN      = new Color(12, 12, 14);   // window background
    private static final Color BG_PANEL     = new Color(22, 22, 24);   // top panel
    private static final Color BG_INPUT     = new Color(32, 32, 36);   // text field / surfaces

    private static final Color FG_PRIMARY   = new Color(232, 232, 235); // main text
    private static final Color FG_MUTED     = new Color(140, 140, 146); // secondary text

    // buttons / accents: soft black/charcoal
    private static final Color ACCENT_BTN   = new Color(32, 32, 36);   // button bg
    private static final Color ACCENT_BTN_H = new Color(48, 48, 52);   // hover bg

    private static final Color BORDER_SOFT  = new Color(54, 54, 60);   // subtle outlines
    private static final Color DROP_BORDER  = new Color(70, 70, 78);   // dashed box border

    private static final String BASE_FONT_FAMILY = "SansSerif";

    public DesktopUi(TimeSeriesInterpolationService interpolationService) {
        this.interpolationService = interpolationService;
    }

    public void show() {
        SwingUtilities.invokeLater(() -> {
            installBaseLookAndFeel();

            Font baseFont = new Font(BASE_FONT_FAMILY, Font.PLAIN, 12);

            JFrame frame = new JFrame("Missing Data Imputation");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(560, 380);
            frame.setLocationRelativeTo(null);
            frame.getContentPane().setBackground(BG_MAIN);
            frame.setLayout(new BorderLayout(8, 8));
            frame.setFont(baseFont);

            // ---------- TOP: "Add File Tag" + textbox + Timestamp on one line ----------
            JPanel topPanel = new JPanel();
            topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
            topPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 8, 12));
            topPanel.setBackground(BG_PANEL);

            // row: [Add File Tag] [ text field ] [ Timestamp ]
            JPanel rowPanel = new JPanel(new GridBagLayout());
            rowPanel.setOpaque(false);

            GridBagConstraints topGbc = new GridBagConstraints();
            topGbc.gridy = 0;
            topGbc.insets = new Insets(0, 0, 0, 8);
            topGbc.anchor = GridBagConstraints.WEST;

            // label (left, no stretch)
            JLabel titleLabel = new JLabel("Add File Tag");
            titleLabel.setForeground(FG_PRIMARY);
            titleLabel.setFont(baseFont.deriveFont(Font.BOLD, 13f));
            topGbc.gridx = 0;
            topGbc.weightx = 0;
            topGbc.fill = GridBagConstraints.NONE;
            rowPanel.add(titleLabel, topGbc);

            // text field (middle, takes remaining width)
            suffixField = new JTextField();
            suffixField.setToolTipText("Suffix to append to filename (e.g. 2025-01-01, final, imp)");
            suffixField.setBackground(BG_INPUT);
            suffixField.setForeground(FG_PRIMARY);
            suffixField.setCaretColor(FG_PRIMARY);
            suffixField.setFont(baseFont.deriveFont(13f));
            suffixField.setBorder(new RoundedBorder(20, BORDER_SOFT));
            suffixField.setOpaque(true);

            topGbc.gridx = 1;
            topGbc.weightx = 1.0;
            topGbc.fill = GridBagConstraints.HORIZONTAL;
            rowPanel.add(suffixField, topGbc);

            // timestamp button (right, no stretch)
            JButton timestampButton = new JButton("Timestamp");
            timestampButton.setToolTipText("Fill with today's date (yyyy-MM-dd)");
            timestampButton.setFont(baseFont.deriveFont(Font.BOLD, 12f));
            stylePrimaryButton(timestampButton);
            timestampButton.addActionListener(e -> {
                String today = LocalDate.now().toString();
                suffixField.setText(today);
            });

            topGbc.gridx = 2;
            topGbc.weightx = 0;
            topGbc.fill = GridBagConstraints.NONE;
            rowPanel.add(timestampButton, topGbc);

            // preview label underneath
            exampleLabel = new JLabel();
            exampleLabel.setFont(baseFont.deriveFont(11f));
            exampleLabel.setForeground(FG_MUTED);

            // live update wiring
            suffixField.getDocument().addDocumentListener(new DocumentListener() {
                @Override public void insertUpdate(DocumentEvent e) { updateExampleLabel(); }
                @Override public void removeUpdate(DocumentEvent e) { updateExampleLabel(); }
                @Override public void changedUpdate(DocumentEvent e) { updateExampleLabel(); }
            });
            updateExampleLabel();

            // add to top panel
            topPanel.add(rowPanel);
            topPanel.add(Box.createVerticalStrut(6));
            topPanel.add(exampleLabel);


            // Divider line (subtle)
            JSeparator separator = new JSeparator();
            separator.setForeground(BORDER_SOFT);
            separator.setBackground(BG_MAIN);

            // ---------- BOTTOM: drag-and-drop area (same layout, blue style) ----------
            DropAreaPanel dropArea = new DropAreaPanel();
            dropArea.setLayout(new GridBagLayout());
            dropArea.setBackground(BG_MAIN);

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.weightx = 1.0;
            gbc.anchor = GridBagConstraints.CENTER;
            gbc.fill   = GridBagConstraints.NONE;
            // moderate spacing between rows
            gbc.insets = new Insets(2, 4, 2, 4);

            JLabel iconLabel = new JLabel();
            loadCloudIcon(iconLabel);

            JLabel dragLabel = new JLabel("Drag & drop to upload");
            dragLabel.setFont(baseFont.deriveFont(Font.BOLD, 15f));
            dragLabel.setForeground(FG_PRIMARY);

            JLabel browseLabel = new JLabel(
                    "<html><span style='color:#A0A0A6;'><u>or browse</u></span></html>"
            );
            browseLabel.setFont(baseFont.deriveFont(11f));
            browseLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            // top spacer to give room above icon
            gbc.gridy = 0;
            gbc.weighty = 1.0;
            dropArea.add(Box.createVerticalStrut(4), gbc);

            // icon
            gbc.gridy = 1;
            gbc.weighty = 0;
            dropArea.add(iconLabel, gbc);

            // "Drag & drop to upload"
            gbc.gridy = 2;
            dropArea.add(dragLabel, gbc);

            // "or browse"
            gbc.gridy = 3;
            dropArea.add(browseLabel, gbc);

            // bottom spacer to give room below "or browse"
            gbc.gridy = 4;
            gbc.weighty = 1.0;
            dropArea.add(Box.createVerticalStrut(4), gbc);

            // Drag & drop support
            new DropTarget(dropArea, new FileDropTargetListener());

            // Browse click -> open file chooser
            browseLabel.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    JFileChooser chooser = new JFileChooser();
                    chooser.setDialogTitle("Choose CSV file");
                    int result = chooser.showOpenDialog(frame);
                    if (result == JFileChooser.APPROVE_OPTION) {
                        File selected = chooser.getSelectedFile();
                        if (selected != null) {
                            handleFile(selected.toPath());
                        }
                    }
                }
            });

            JPanel dropWrapper = new JPanel(new BorderLayout());
            dropWrapper.setBorder(BorderFactory.createEmptyBorder(8, 12, 12, 12));
            dropWrapper.setBackground(BG_MAIN);
            dropWrapper.add(dropArea, BorderLayout.CENTER);

            frame.getContentPane().add(topPanel, BorderLayout.NORTH);
            frame.getContentPane().add(separator, BorderLayout.CENTER);
            frame.getContentPane().add(dropWrapper, BorderLayout.SOUTH);

            frame.setVisible(true);
        });
    }

    // ---------------- helper styling ----------------

    private void installBaseLookAndFeel() {
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception ignored) { }

        UIManager.put("control", BG_MAIN);
        UIManager.put("info", BG_PANEL);
        UIManager.put("nimbusBase", new Color(30, 30, 34));
        UIManager.put("nimbusBlueGrey", new Color(50, 50, 58));
        UIManager.put("nimbusLightBackground", BG_PANEL);
        UIManager.put("text", FG_PRIMARY);
    }

    private void stylePrimaryButton(JButton button) {
        button.setBackground(ACCENT_BTN);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setContentAreaFilled(false);
        button.setOpaque(true);
        button.setBorder(new RoundedBorder(18, ACCENT_BTN));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        Color normal = ACCENT_BTN;
        Color hover  = ACCENT_BTN_H;

        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setBackground(hover);
                button.setBorder(new RoundedBorder(18, hover));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(normal);
                button.setBorder(new RoundedBorder(18, normal));
            }

            @Override
            public void mousePressed(MouseEvent e) {
                button.setBackground(hover.darker());
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (button.getBounds().contains(e.getPoint())) {
                    button.setBackground(hover);
                } else {
                    button.setBackground(normal);
                }
            }
        });
    }

    private void updateExampleLabel() {
        if (exampleLabel == null) return;

        String suffix = (suffixField != null) ? suffixField.getText().trim() : "";
        String htmlSuffix = escapeHtml(suffix);

        String text;
        if (suffix.isEmpty()) {
            text = "<html><i>filename</i>.csv</html>";
        } else {
            text = "<html><i>filename</i>_" + htmlSuffix + ".csv</html>";
        }
        exampleLabel.setText(text);
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private void loadCloudIcon(JLabel iconLabel) {
        try {
            URL iconUrl = getClass().getResource("/ui/upload-icon.png");
            System.out.println("Icon URL = " + iconUrl);
            if (iconUrl != null) {
                ImageIcon rawIcon = new ImageIcon(iconUrl);
                java.awt.Image img = rawIcon.getImage();
                int w = rawIcon.getIconWidth();
                int h = rawIcon.getIconHeight();

                if (w > 60) {
                    int newW = 60;
                    int newH = (int) ((double) h * newW / w);
                    java.awt.Image scaled =
                            img.getScaledInstance(newW, newH, java.awt.Image.SCALE_SMOOTH);
                    iconLabel.setIcon(new ImageIcon(scaled));
                } else {
                    iconLabel.setIcon(rawIcon);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void handleFile(Path inputPath) {
        try {
            String suffix = (suffixField != null) ? suffixField.getText() : "";
            Path outputPath = interpolationService.processFile(inputPath, suffix);

            JOptionPane.showMessageDialog(
                    null,
                    "Completed\nOutput file:\n" + outputPath,
                    "Done",
                    JOptionPane.INFORMATION_MESSAGE
            );
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(
                    null,
                    "Error: " + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private class FileDropTargetListener extends DropTargetAdapter {
        @Override
        public void drop(DropTargetDropEvent dtde) {
            try {
                dtde.acceptDrop(DnDConstants.ACTION_COPY);
                @SuppressWarnings("unchecked")
                List<File> droppedFiles = (List<File>) dtde.getTransferable()
                        .getTransferData(DataFlavor.javaFileListFlavor);

                if (droppedFiles.isEmpty()) return;
                File file = droppedFiles.get(0);
                handleFile(file.toPath());
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(
                        null,
                        "Error: " + e.getMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        }
    }

    // Rounded rectangle with dashed magenta border
    private static class DropAreaPanel extends JPanel {
        @Override
        public Dimension getPreferredSize() {
            return new Dimension(500, 250);
        }

        @Override
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int inset = 8;
            int x = inset;
            int y = inset;
            int w = getWidth() - 2 * inset;
            int h = getHeight() - 2 * inset;
            int arc = 24;

            float[] dash = {6f, 6f};
            g2.setColor(DROP_BORDER);
            g2.setStroke(new BasicStroke(
                    2f,
                    BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND,
                    0f,
                    dash,
                    0f
            ));

            g2.draw(new RoundRectangle2D.Float(x, y, w, h, arc, arc));
            g2.dispose();
        }
    }

    // Rounded border for modern pill look (used by textbox and button)
    private static class RoundedBorder implements Border {
        private final int radius;
        private final Color color;

        RoundedBorder(int radius, Color color) {
            this.radius = radius;
            this.color = color;
        }

        @Override
        public Insets getBorderInsets(java.awt.Component c) {
            return new Insets(6, 14, 6, 14);
        }

        @Override
        public boolean isBorderOpaque() {
            return false;
        }

        @Override
        public void paintBorder(java.awt.Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.drawRoundRect(x, y, width - 1, height - 1, radius, radius);
            g2.dispose();
        }
    }
}
