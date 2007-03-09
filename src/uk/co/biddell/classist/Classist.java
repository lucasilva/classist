//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU Library General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
//
package uk.co.biddell.classist;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.prefs.Preferences;
import java.util.regex.Pattern;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

/**
 * Classist
 * 
 * @author luke.biddell@gmail.com
 */
final class Classist extends JFrame implements ListSelectionListener, DocumentListener {

    private static final long serialVersionUID = -5829213504411524998L;
    private static final String PREFS_LAST_SEARCH_DIRECTORY = "LastSearchDirectory";
    private final HashMap<String, ArrayList<String>> classes = new HashMap<String, ArrayList<String>>();
    private final DefaultListModel classListModel = new DefaultListModel();
    private final JList classList = new JList(classListModel);
    private final DefaultListModel jarListModel = new DefaultListModel();
    private final JList jarList = new JList(jarListModel);
    private final JTextField searchField = new JTextField();
    private final JTextField pathField = new JTextField();
    private final JButton loadButton = new JButton(new LoadClassesAction());
    private final JCheckBox duplicatesCheck = new JCheckBox(new ShowDuplicatesAction());
    private final JLabel resultsLabel = new JLabel();
    private final Preferences prefs = Preferences.userNodeForPackage(Classist.class);
    private final ProgressMonitor pm = new ProgressMonitor(this, "Loading classes....");
    private final FileFilter filter = new FileFilter() {

        public final boolean accept(final File pathname) {
            if (pathname.isDirectory()) {
                return true;
            } else {
                final String name = pathname.getName().toLowerCase();
                if (name.endsWith(".jar") || /* name.endsWith(".zip") || */name.endsWith(".ear") || name.endsWith(".war") || name.endsWith(".sar") || name.endsWith(".rar")
                        || name.endsWith(".par")) {
                    return true;
                }
            }
            return false;
        }
    };

    Classist(final String initialDirectory) throws Exception {
        setTitle(getClass().getSimpleName());
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setLayout(new GridBagLayout());
        final GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = 0;
        final JLabel label1 = new JLabel("Path");
        label1.setFont(label1.getFont().deriveFont(Font.BOLD).deriveFont(16.0F));
        add(label1, gbc);
        gbc.weightx = 1.0;
        gbc.gridx = 0;
        gbc.gridy++;
        add(pathField, gbc);
        gbc.weightx = 0.0;
        gbc.gridx = 1;
        add(loadButton, gbc);
        gbc.weightx = 1.0;
        gbc.gridx = 0;
        gbc.gridy++;
        final JLabel label2 = new JLabel("Search");
        label2.setFont(label2.getFont().deriveFont(Font.BOLD).deriveFont(16.0F));
        add(label2, gbc);
        gbc.weightx = 1.0;
        gbc.gridx = 0;
        gbc.gridy++;
        add(searchField, gbc);
        gbc.weightx = 0.0;
        gbc.gridx = 1;
        add(duplicatesCheck, gbc);
        gbc.gridx = 0;
        gbc.gridy++;
        resultsLabel.setFont(resultsLabel.getFont().deriveFont(Font.BOLD).deriveFont(16.0F));
        add(resultsLabel, gbc);
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.gridy++;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        final JSplitPane jsp = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(classList), new JScrollPane(jarList));
        add(jsp, gbc);
        searchField.getDocument().addDocumentListener(this);
        classList.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
        classList.getSelectionModel().addListSelectionListener(this);
        jarList.setVisibleRowCount(5);
        pathField.getDocument().addDocumentListener(this);
        getRootPane().setDefaultButton(loadButton);
        setPreferredSize(new Dimension(640, 480));
        pack();
        if (initialDirectory != null && initialDirectory.length() > 0) {
            pathField.setText(initialDirectory);
            if (loadButton.isEnabled()) {
                EventQueue.invokeLater(new Runnable() {

                    public final void run() {
                        loadButton.doClick();
                    }
                });
            }
        } else {
            pathField.setText(prefs.get(PREFS_LAST_SEARCH_DIRECTORY, System.getProperty("user.home")));
        }
        enableControls();
        updateResults();
    }

    private final void walkFilesystem(final File directory) throws IOException, InterruptedException {
        // Check if we have been interrupted
        Thread.sleep(0);
        final File[] files = directory.listFiles(filter);
        for (final File f : files) {
            if (f.isDirectory()) {
                walkFilesystem(f);
            } else {
                final String fileName = f.getAbsolutePath();
                final JarFile jar = new JarFile(fileName);
                try {
                    final Enumeration<JarEntry> e = jar.entries();
                    while (e.hasMoreElements()) {
                        String className = e.nextElement().getName();
                        if (className.endsWith(".class")) {
                            className = className.replace('/', '.').replace(".class", "");
                            ArrayList<String> jarsContainingClass = classes.get(className);
                            if (jarsContainingClass == null) {
                                jarsContainingClass = new ArrayList<String>();
                                classes.put(className, jarsContainingClass);
                            }
                            jarsContainingClass.add(fileName);
                        }
                    }
                } finally {
                    jar.close();
                }
            }
        }
    }

    private final void displayClassMatches() {
        try {
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            classListModel.removeAllElements();
            jarListModel.removeAllElements();
            final String text = searchField.getText();
            final Pattern pat = Pattern.compile(text, Pattern.CASE_INSENSITIVE);
            for (final String className : classes.keySet()) {
                if (pat.matcher(className).find()) {
                    classListModel.addElement(className);
                }
            }
        } finally {
            setCursor(Cursor.getDefaultCursor());
            enableControls();
            updateResults();
        }
    }

    private final void displayDuplicateClasses() {
        try {
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            classListModel.removeAllElements();
            jarListModel.removeAllElements();
            for (final Entry<String, ArrayList<String>> e : classes.entrySet()) {
                if (e.getValue().size() > 1) {
                    classListModel.addElement(e.getKey());
                }
            }
        } finally {
            setCursor(Cursor.getDefaultCursor());
            enableControls();
            updateResults();
        }
    }

    private final void displayJarMatches(final String className) {
        jarListModel.removeAllElements();
        final ArrayList<String> jarsContainingClass = classes.get(className);
        for (final String jar : jarsContainingClass) {
            jarListModel.addElement(jar);
        }
    }

    public final void valueChanged(final ListSelectionEvent e) {
        if (!e.getValueIsAdjusting()) {
            final int index = classList.getSelectedIndex();
            if (index != -1) {
                displayJarMatches((String) classListModel.get(index));
            }
        }
    }

    public final void insertUpdate(final DocumentEvent e) {
        handleDocumentEvent(e);
    }

    public final void removeUpdate(final DocumentEvent e) {
        handleDocumentEvent(e);
    }

    public final void changedUpdate(final DocumentEvent e) {
        handleDocumentEvent(e);
    }

    private final void handleDocumentEvent(final DocumentEvent e) {
        if (e.getDocument() == searchField.getDocument()) {
            EventQueue.invokeLater(new Runnable() {

                public void run() {
                    displayClassMatches();
                }
            });
        } else if (e.getDocument() == pathField.getDocument()) {
            enableControls();
        }
    }

    private final void updateResults() {
        resultsLabel.setText("Results (" + classListModel.getSize() + ")");
    }

    private final void enableControls() {
        loadButton.setEnabled(pathField.getDocument().getLength() > 0 && new File(pathField.getText()).exists());
        if (classes.size() > 0) {
            duplicatesCheck.setEnabled(true);
            searchField.setEnabled(!duplicatesCheck.isSelected());
        } else {
            duplicatesCheck.setEnabled(false);
            searchField.setEnabled(false);
        }
    }

    private final class LoadClassesAction extends AbstractAction {

        private static final long serialVersionUID = -2957345645844025493L;

        public LoadClassesAction() {
            super("Load classes");
        }

        public final void actionPerformed(final ActionEvent e) {
            prefs.put(PREFS_LAST_SEARCH_DIRECTORY, pathField.getText());
            pm.monitor(new Thread() {

                @Override
                public final void run() {
                    try {
                        classes.clear();
                        walkFilesystem(new File(pathField.getText()));
                    } catch (final InterruptedException ie) {
                        classes.clear();
                    } catch (final Exception e) {
                        classes.clear();
                        throw new RuntimeException(e);
                    } finally {
                        EventQueue.invokeLater(new Runnable() {

                            public final void run() {
                                displayClassMatches();
                                pm.setVisible(false);
                            }
                        });
                    }
                }
            });
        }
    }

    private final class ShowDuplicatesAction extends AbstractAction {

        private static final long serialVersionUID = -526302916502802938L;

        public ShowDuplicatesAction() {
            super("Show duplicate classes");
        }

        public final void actionPerformed(final ActionEvent e) {
            if (duplicatesCheck.isSelected()) {
                displayDuplicateClasses();
            } else {
                displayClassMatches();
            }
        }
    }

    private final class ProgressMonitor extends JDialog {

        private static final long serialVersionUID = 6420009097662336539L;
        private final JProgressBar progressBar = new JProgressBar();
        private final JButton cancelButton = new JButton(new CancelAction());
        private final JLabel label = new JLabel();
        private Thread thread;

        private ProgressMonitor(final Frame parent, final String title) {
            super(parent);
            setModal(true);
            setTitle(title);
            setResizable(false);
            setUndecorated(true);
            getRootPane().setBorder(BorderFactory.createLineBorder(Color.BLACK));
            setLayout(new GridBagLayout());
            final GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(4, 4, 4, 4);
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.gridx = 0;
            gbc.gridy = 0;
            add(label, gbc);
            gbc.gridy++;
            add(progressBar, gbc);
            gbc.gridy++;
            gbc.fill = GridBagConstraints.NONE;
            gbc.anchor = GridBagConstraints.EAST;
            add(cancelButton, gbc);
            label.setText(title);
            progressBar.setIndeterminate(true);
            pack();
        }

        public final void monitor(final Thread t) {
            this.thread = t;
            setLocationRelativeTo(getParent());
            t.start();
            setVisible(true);
        }

        @Override
        public final void setVisible(final boolean visible) {
            if (visible) {
                getParent().setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            } else {
                getParent().setCursor(Cursor.getDefaultCursor());
            }
            super.setVisible(visible);
        }

        private final class CancelAction extends AbstractAction {

            private static final long serialVersionUID = 1L;

            public CancelAction() {
                super("Cancel");
            }

            public final void actionPerformed(final ActionEvent e) {
                thread.interrupt();
            }
        }
    }

    public static void main(final String[] args) throws Exception {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        final Classist c = new Classist(args.length > 0 ? args[0] : null);
        c.setVisible(true);
    }
}
