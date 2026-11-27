package com.woogajjangka.coolthemekit;

import org.junit.Test;

import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JTree;
import javax.swing.plaf.TreeUI;
import javax.swing.plaf.basic.BasicTreeUI;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreePath;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/** The open folder is painted for the expanded rows of a tree, the closed one elsewhere. */
public class CoolFolderIconTest {
    /** Records which icon was painted, in order. */
    private record Recording(String name, List<String> painted) implements Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            painted.add(name);
        }

        @Override
        public int getIconWidth() {
            return 16;
        }

        @Override
        public int getIconHeight() {
            return 16;
        }
    }

    private static List<String> paintRows(TreeUI ui, boolean expandFirst) {
        List<String> painted = new ArrayList<>();
        Icon icon = new CoolFolderIcon(new Recording("closed", painted), new Recording("open", painted));
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
        DefaultMutableTreeNode a = new DefaultMutableTreeNode("a");
        DefaultMutableTreeNode b = new DefaultMutableTreeNode("b");
        a.add(new DefaultMutableTreeNode("a1"));
        b.add(new DefaultMutableTreeNode("b1"));
        root.add(a);
        root.add(b);
        JTree tree = new JTree(root);
        tree.setUI(ui);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(new DefaultTreeCellRenderer() {
            @Override
            public Component getTreeCellRendererComponent(JTree t, Object value, boolean sel, boolean expanded,
                                                          boolean leaf, int row, boolean focus) {
                JLabel label = (JLabel) super.getTreeCellRendererComponent(t, value, sel, expanded, leaf, row, focus);
                label.setIcon(((DefaultMutableTreeNode) value).isLeaf() ? null : icon);
                return label;
            }
        });
        if (expandFirst) tree.expandPath(new TreePath(new Object[]{root, a}));
        tree.setSize(200, 200);
        BufferedImage image = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB);
        Graphics g = image.createGraphics();
        tree.paint(g);
        g.dispose();
        return painted;
    }

    @Test
    public void expandedRowsPaintTheOpenFolder() {
        assertEquals(List.of("closed", "closed"), paintRows(new BasicTreeUI(), false));
        assertEquals(List.of("open", "closed"), paintRows(new BasicTreeUI(), true));
    }

    @Test
    public void outsideATreeItIsClosed() {
        List<String> painted = new ArrayList<>();
        Icon icon = new CoolFolderIcon(new Recording("closed", painted), new Recording("open", painted));
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        icon.paintIcon(new JLabel(), image.getGraphics(), 0, 0);
        assertEquals(List.of("closed"), painted);
    }
}
