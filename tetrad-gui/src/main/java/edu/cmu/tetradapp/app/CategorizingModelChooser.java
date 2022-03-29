///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.app;

import edu.cmu.tetrad.session.SessionNode;

import javax.swing.*;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.prefs.Preferences;

/**
 * Lets you choose models from a categorized list.
 *
 * @author Tyler Gibson
 */
public class CategorizingModelChooser extends JPanel implements ModelChooser {


    /**
     * The title of the chooser.
     */
    private String title;


    /**
     * The node name
     */
    private String nodeName;

    /**
     * The id for the node.
     */
    private String nodeId;


    /**
     * THe JTree used to display the options.
     */
    private JTree tree;

    /**
     * The session node for the getModel node.
     */
    private SessionNode sessionNode;

    //========================== public methods =========================//

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        if (title == null) {
            throw new NullPointerException("The title must not be null");
        }
        this.title = title;
    }

    public Class getSelectedModel() {
        TreePath path = tree.getSelectionPath();

        if (path == null) {
            throw new NullPointerException("I had a problem figuring out the models for this box given the parents. Maybe\n" +
                    "the parents are wrong, or maybe this isn't the box you were intending to use.");
        }

        Object selected = path.getLastPathComponent();
        if (selected instanceof ModelWrapper) {
            return ((ModelWrapper) selected).model;
        }
        return null;
    }

    public void setModelConfigs(List<SessionNodeModelConfig> configs) {
        ChooserTreeModel model = new ChooserTreeModel(configs);
        tree = new JTree(model);
        tree.setCellRenderer(new ChooserRenderer());
        tree.setRootVisible(false);
        tree.setEditable(false);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandRow(i);
        }
        tree.addTreeSelectionListener(e -> {
            TreePath path = e.getPath();
            Object selected = path.getLastPathComponent();
            if (selected instanceof ModelWrapper) {
                String name = ((ModelWrapper) selected).name;
                Preferences.userRoot().put(nodeId, name);
            }
        });

        // select a default value, if one exists
        String storedModelType = this.getModelTypeFromSessionNode(sessionNode, model);

        if (storedModelType == null) {
            storedModelType = Preferences.userRoot().get(nodeId, "");
        }

        System.out.println("Stored model type = " + storedModelType);


        if (storedModelType.length() != 0) {
            for (Entry<String, List<ModelWrapper>> entry : model.map.entrySet()) {
                for (ModelWrapper wrapper : entry.getValue()) {
                    if (storedModelType.equals(wrapper.name)) {
                        Object[] path = {ChooserTreeModel.ROOT, entry.getKey(), wrapper};
                        tree.setSelectionPath(new TreePath(path));
                        break;
                    }
                }
            }
        }
    }

    private String getModelTypeFromSessionNode(SessionNode sessionNode, ChooserTreeModel model) {

        // Assumes the tree will always be of depth 2.
        Class clazz = sessionNode.getLastModelClass();
        Object root = model.getRoot();

        for (int i = 0; i < model.getChildCount(root); i++) {
            Object child = model.getChild(root, i);

            for (int j = 0; j < model.getChildCount(child); j++) {
                ModelWrapper modelWrapper = (ModelWrapper) model.getChild(child, j);
                assert modelWrapper != null;
                if (modelWrapper.model == clazz) {
                    return modelWrapper.name;
                }
            }
        }

        return null;
    }

    public void setNodeId(String id) {
        if (id == null) {
            throw new NullPointerException("The given id must not be null");
        }
        nodeId = id;
    }

    public void setSessionNode(SessionNode sessionNode) {
        this.sessionNode = sessionNode;
        nodeName = sessionNode.getDisplayName();
    }

    public void setup() {
        setLayout(new BorderLayout());

        JButton info = new JButton("Help");

        info.addActionListener(e -> {
            Class model = this.getSelectedModel();
            if (model == null) {
                JOptionPane.showMessageDialog(this, "No node selected. Select" +
                        " a node to get help for it.");
            } else {
                SessionUtils.showPermissibleParentsDialog(model,
                        this, false, false);
            }
        });

        Box vBox = Box.createVerticalBox();
        vBox.add(Box.createVerticalStrut(5));

        Box box = Box.createHorizontalBox();
        box.add(new JLabel(" Name of node: " + nodeName));
        box.add(Box.createHorizontalGlue());
        box.add(info);
        box.add(Box.createHorizontalStrut(5));

        vBox.add(box);
        vBox.add(Box.createVerticalStrut(5));

        add(vBox, BorderLayout.NORTH);
        add(new JScrollPane(tree), BorderLayout.CENTER);
    }

    //================================= Inner classes ============================//

    /**
     * Wraps a model class and the name.
     */
    private static class ModelWrapper {

        private final String name;
        private final Class model;

        public ModelWrapper(String name, Class model) {
            this.name = name;
            this.model = model;
        }

    }

    private static class ChooserRenderer extends DefaultTreeCellRenderer {


        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded,
                                                      boolean leaf, int row, boolean hasFocus) {

            if (value == null) {
                setText("");
            } else if (value instanceof ModelWrapper) {
                setText(((ModelWrapper) value).name);
            } else {
                setText((String) value);
            }

            if (leaf) {
                this.setIcon(null);
            } else if (expanded) {
                this.setIcon(this.getOpenIcon());
            } else {
                this.setIcon(this.getClosedIcon());
            }
            if (selected) {
                setForeground(getTextSelectionColor());
            } else {
                setForeground(getTextNonSelectionColor());
            }

            this.selected = selected;
            return this;
        }

    }


    /**
     * Model for the chooser's tree.
     */
    private static class ChooserTreeModel implements TreeModel {

        private static final String ROOT = "Root";

        private final Map<String, List<ModelWrapper>> map = new HashMap<>();

        private final List<String> categories = new LinkedList<>();

        public ChooserTreeModel(List<SessionNodeModelConfig> configs) {
            for (SessionNodeModelConfig config : configs) {
                String category = config.getCategory();
                if (category == null) {
                    throw new NullPointerException("No Category name associated with model: " + config.getModel());
                }

                if ("Unlisted".equals(category)) {
                    continue;
                }

                if (!categories.contains(category)) {
                    categories.add(category);
                }

                List<ModelWrapper> models = map.computeIfAbsent(category, k -> new LinkedList<>());
                models.add(new ModelWrapper(config.getName(), config.getModel()));
            }
        }

        public Object getRoot() {
            return ROOT;
        }

        public Object getChild(Object parent, int index) {
            if (ROOT.equals(parent)) {
                return categories.get(index);
            } else if (parent instanceof String) {
                List<ModelWrapper> models = map.get(parent);
                return models.get(index);
            }
            return null;
        }

        public int getChildCount(Object parent) {
            if (ROOT.equals(parent)) {
                return categories.size();
            } else if (parent instanceof ModelWrapper) {
                return 0;
            }
            List<ModelWrapper> models = map.get(parent);
            return models.size();
        }

        public boolean isLeaf(Object node) {
            return node instanceof ModelWrapper;
        }

        public void valueForPathChanged(TreePath path, Object newValue) {

        }

        public int getIndexOfChild(Object parent, Object child) {
            if (ROOT.equals(parent)) {
                return categories.indexOf(child);
            }
            List<ModelWrapper> models = map.get(parent);
            return models.indexOf(child);
        }

        public void addTreeModelListener(TreeModelListener l) {

        }

        public void removeTreeModelListener(TreeModelListener l) {

        }
    }


}




