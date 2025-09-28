package io.github.s4gh.projecteditorsyncactions;

import org.openide.cookies.EditorCookie;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.event.ContainerAdapter;
import java.awt.event.ContainerEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Objects;
import java.util.Set;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import org.openide.awt.Actions;
import org.openide.util.ContextAwareAction;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import javax.swing.Action;
import java.awt.event.ActionEvent;

import org.netbeans.api.project.ui.OpenProjects;
import org.openide.explorer.view.BeanTreeView;
import org.openide.modules.ModuleInstall;
import org.openide.util.ImageUtilities;
import org.openide.util.WeakListeners;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

public final class Installer extends ModuleInstall {

    // TopComponent IDs for the Projects tab variants
    private static final String ID_PROJECTS_LOGICAL  = "projectTabLogical_tc"; // Projects (Logical)
    private static final String ID_PROJECTS_PHYSICAL = "projectTab_tc";        // Files (Physical)

    // Client properties on BeanTreeView
    private static final String CP_OVERLAYS_INSTALLED   = "s4gh.overlays.installed";
    private static final String CP_ORIGINAL_HEADER_VIEW = "s4gh.overlays.originalHeaderView";

    // Client property on TopComponent (temporary listener for lazy content)
    private static final String CP_TC_CONTAINER_LIS     = "s4gh.overlays.tcContainerListener";

    private PropertyChangeListener registryListener;

    // ------------------------------ lifecycle ------------------------------

    @Override
    public void restored() {
        // Ensure we start on EDT after winsys is up
        WindowManager.getDefault().invokeWhenUIReady(() -> {
            TopComponent.Registry reg = TopComponent.getRegistry();

            registryListener = this::onRegistryChange;
            reg.addPropertyChangeListener(WeakListeners.propertyChange(registryListener, reg));

            // If Projects windows are already open (restored session), install now (EDT)
            Set<TopComponent> opened = reg.getOpened();
            if (opened != null) {
                for (TopComponent tc : opened) {
                    if (isProjectsView(tc)) {
                        ensureButtonsInstalled(tc); // already on EDT
                    }
                }
            }

            // First-run: when the first project opens, attempt install on EDT
            PropertyChangeListener projectsPCL = evt -> {
                if (OpenProjects.PROPERTY_OPEN_PROJECTS.equals(evt.getPropertyName())) {
                    runOnEDT(() -> {
                        TopComponent logical  = WindowManager.getDefault().findTopComponent(ID_PROJECTS_LOGICAL);
                        TopComponent physical = WindowManager.getDefault().findTopComponent(ID_PROJECTS_PHYSICAL);
                        if (logical  != null) ensureButtonsInstalled(logical);
                        if (physical != null) ensureButtonsInstalled(physical);
                    });
                }
            };
            OpenProjects.getDefault().addPropertyChangeListener(
                WeakListeners.propertyChange(projectsPCL, OpenProjects.getDefault()));
        });
    }

    @Override
    public void uninstalled() {
        // Registry listener removal can be done on any thread; method itself will dispatch if needed
        TopComponent.Registry reg = TopComponent.getRegistry();
        if (registryListener != null) {
            reg.removePropertyChangeListener(registryListener);
            registryListener = null;
        }
    }
    
    public static FileObject getActiveEditorFile() {
        // Get all opened windows
        Set<TopComponent> opened = WindowManager.getDefault().getRegistry().getOpened();

        // Find the editor window
        for (TopComponent tc : opened) {
            // Check if this component is an editor window and is focused
            if (tc.isShowing() && isEditorWindow(tc)) {
                // Get the DataObject from the editor
                DataObject dataObj = tc.getLookup().lookup(DataObject.class);
                if (dataObj != null) {
                    return dataObj.getPrimaryFile();
                }
            }
        }
        return null;
    }

        private static boolean isEditorWindow(TopComponent tc) {
        // Check if this is an editor window by looking for EditorCookie
        EditorCookie ec = tc.getLookup().lookup(EditorCookie.class);
        if (ec != null) {
            // Additional check - verify the component's class name
            // Editor windows typically have "EditorTopComponent" in their class name
            String className = tc.getClass().getName().toLowerCase();
            return className.contains("editor") || className.contains("multiview");
        }
        return false;
    }
    
    // ------------------------------ window registry events ------------------------------

    private void onRegistryChange(PropertyChangeEvent evt) {
        String prop = evt.getPropertyName();

        if (TopComponent.Registry.PROP_TC_OPENED.equals(prop)) {
            TopComponent tc = (TopComponent) evt.getNewValue();
            if (isProjectsView(tc)) {
                runOnEDT(() -> ensureButtonsInstalled(tc));
            }
        } else if (TopComponent.Registry.PROP_TC_CLOSED.equals(prop)) {
            TopComponent tc = (TopComponent) evt.getNewValue();
            if (isProjectsView(tc)) {
                runOnEDT(() -> removeButtons(tc));
            }
        } else if (TopComponent.Registry.PROP_ACTIVATED.equals(prop)) {
            TopComponent tc = TopComponent.getRegistry().getActivated();
            if (tc != null && isProjectsView(tc)) {
                runOnEDT(() -> ensureButtonsInstalled(tc));
            }
        }
    }

    private boolean isProjectsView(TopComponent tc) {
        // Must be called on EDT; this method is only called from runOnEDT callers
        String id = WindowManager.getDefault().findTopComponentID(tc);
        return ID_PROJECTS_LOGICAL.equals(id) || ID_PROJECTS_PHYSICAL.equals(id);
    }
    
    private boolean isProjectsLogicalView(TopComponent tc) {
        // Must be called on EDT; this method is only called from runOnEDT callers
        String id = WindowManager.getDefault().findTopComponentID(tc);
        return ID_PROJECTS_LOGICAL.equals(id);
    }

    // ------------------------------ installation & cleanup (EDT-only) ------------------------------

    private void ensureButtonsInstalled(TopComponent projectsTC) {
        assert SwingUtilities.isEventDispatchThread();

        // If we already have a BeanTreeView, install header now
        BeanTreeView btv = findChild(projectsTC, BeanTreeView.class);
        if (btv != null) {
            installHeaderIfNeeded(btv, projectsTC);
            return;
        }

        // Otherwise, attach a temporary ContainerListener to detect when BTV appears
        if (projectsTC.getClientProperty(CP_TC_CONTAINER_LIS) == null) {
            ContainerAdapter ca = new ContainerAdapter() {
                @Override public void componentAdded(ContainerEvent e) {
                    // This callback is on EDT
                    BeanTreeView found = findChild(projectsTC, BeanTreeView.class);
                    if (found != null) {
                        installHeaderIfNeeded(found, projectsTC);
                        // Remove this listener once installed
                        ContainerAdapter stored = (ContainerAdapter) projectsTC.getClientProperty(CP_TC_CONTAINER_LIS);
                        if (stored != null) projectsTC.removeContainerListener(stored);
                        projectsTC.putClientProperty(CP_TC_CONTAINER_LIS, null);
                    }
                }
            };
            projectsTC.addContainerListener(ca);
            projectsTC.putClientProperty(CP_TC_CONTAINER_LIS, ca);
        }
    }

    private void installHeaderIfNeeded(BeanTreeView btv, TopComponent projectsTC) {
        assert SwingUtilities.isEventDispatchThread();

        if (Boolean.TRUE.equals(btv.getClientProperty(CP_OVERLAYS_INSTALLED))) {
            return; // already installed for this BTV instance
        }

        // Create transparent icon-only buttons
        Icon collapseTreeIcon  = safeLoadIcon("icons/collapseTree.svg", 16);
        Icon syncWithCodeEditorIcon = safeLoadIcon("icons/syncWithCodeEditor.svg", 16);
        
        Action collapseAction = Actions.forID(
                "Project",
                "org.netbeans.modules.project.ui.collapseAllNodes"
        );

        if (collapseAction instanceof ContextAwareAction) {
            collapseAction = ((ContextAwareAction) collapseAction)
                    .createContextAwareInstance(
                            Lookups.fixed(WindowManager.getDefault().findTopComponentID(projectsTC))
                    );
        };
        
        final Action collapseActionToExec = collapseAction;

        Runnable collapseTreeAction = () -> {
            runOnEDT(() -> {
                
                collapseActionToExec.actionPerformed(
                        new ActionEvent(
                                this,
                                ActionEvent.ACTION_PERFORMED,
                                "collapseAll"
                        )
                );
                
//                JTree tree = resolveTreeFrom(btv);
//                if (tree != null) {
//                    for (int row = tree.getRowCount() - 1; row >= 0; row--) {
//                        tree.collapseRow(row);
//                    }
//                }
            });
        };

        Runnable selectInTreeAction = () -> {
            runOnEDT(() -> {
                
                ContextAwareAction template = null;
                if (isProjectsLogicalView(projectsTC)) {
                    template = (ContextAwareAction) Actions.forID(
                            "Window/SelectDocumentNode",
                            "org.netbeans.modules.project.ui.SelectInProjects"
                    );
                } else {
                    template = (ContextAwareAction) Actions.forID(
                            "Window/SelectDocumentNode",
                            "org.netbeans.modules.project.ui.SelectInFiles"
                    );
                }

                FileObject fo = getActiveEditorFile();
                if (fo == null) {
                    return;
                }
                
                Lookup ctx = Lookups.fixed(fo);

                Action selectInProjects = template.createContextAwareInstance(ctx);
                selectInProjects.actionPerformed(
                        new ActionEvent(this, ActionEvent.ACTION_PERFORMED, null)
                );
            });
        };

        JButton collapseBtn = makeOverlayButton(collapseTreeIcon, "collapseTreeButton", collapseTreeAction, "Collapse All");
        JButton selectBtn   = makeOverlayButton(syncWithCodeEditorIcon, "selectInTreeButton", selectInTreeAction, "Sync with Code Editor");

        JPanel header = new JPanel();
        header.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 4));
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.X_AXIS));
        header.add(Box.createHorizontalGlue());   // right-align
        header.add(collapseBtn);
        header.add(Box.createHorizontalStrut(4));
        header.add(selectBtn);

        // Save previous header (if any) and install ours
        JViewport headerVP = btv.getColumnHeader();
        Component previousHeader = (headerVP != null) ? headerVP.getView() : null;
        btv.putClientProperty(CP_ORIGINAL_HEADER_VIEW, previousHeader);
        btv.setColumnHeaderView(header);

        btv.putClientProperty(CP_OVERLAYS_INSTALLED, Boolean.TRUE);
    }

    private void removeButtons(TopComponent projectsTC) {
        assert SwingUtilities.isEventDispatchThread();

        // Remove temporary container listener if still present
        Object ca = projectsTC.getClientProperty(CP_TC_CONTAINER_LIS);
        if (ca instanceof ContainerAdapter) {
            projectsTC.removeContainerListener((ContainerAdapter) ca);
        }
        projectsTC.putClientProperty(CP_TC_CONTAINER_LIS, null);

        // Restore header on any BeanTreeView inside
        BeanTreeView btv = findChild(projectsTC, BeanTreeView.class);
        if (btv != null) {
            Object oldHeader = btv.getClientProperty(CP_ORIGINAL_HEADER_VIEW);
            if (oldHeader instanceof Component) {
                btv.setColumnHeaderView((Component) oldHeader);
            } else {
                btv.setColumnHeaderView(null);
            }
            btv.putClientProperty(CP_ORIGINAL_HEADER_VIEW, null);
            btv.putClientProperty(CP_OVERLAYS_INSTALLED, null);
        }
    }

    // ------------------------------ utilities ------------------------------

    /** Run on EDT (invokeLater if we are on a background thread). */
    private static void runOnEDT(Runnable r) {
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
        }
        else {
            SwingUtilities.invokeLater(r);
        }
    }

    /** Depth-first search for a child of the given type within a container (EDT only). */
    private static <T extends Component> T findChild(Container parent, Class<T> type) {
        // Caller ensures EDT; keep fast and simple
        for (Component c : parent.getComponents()) {
            if (type.isInstance(c)) {
                return type.cast(c);
            }
            if (c instanceof Container) {
                T found = findChild((Container) c, type);
                if (found != null) return found;
            }
        }
        return null;
    }

    /** Try to get the JTree used inside a BeanTreeView (EDT only). */
    private static JTree resolveTreeFrom(BeanTreeView btv) {
        if (btv == null) return null;
        JViewport vp = btv.getViewport();
        if (vp == null) {
            return null;
        }
        Component v = vp.getView();
        if (v instanceof JTree) {
            return (JTree) v;
        }
        if (v instanceof JComponent) {
            return findChild((JComponent) v, JTree.class);
        }
        return null;
    }

    private static JButton makeOverlayButton(Icon icon, String name, Runnable action, String toolTipText) {
        JButton b = new JButton(icon);
        b.setName(Objects.requireNonNullElse(name, "overlayButton"));
        b.setOpaque(false);
        b.setContentAreaFilled(false);
        b.setBorderPainted(true);
        b.setFocusPainted(true);
        b.setFocusable(true);
        b.setMargin(new java.awt.Insets(0, 0, 0, 0));
        b.setBorder(javax.swing.BorderFactory.createEmptyBorder(4, 0, 4, 0));
        b.setToolTipText(toolTipText); // add tooltip text if desired
        b.addActionListener(e -> action.run());
        
        b.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
//                b.setBackground(Color.decode("#E0E0E0"));
                b.setContentAreaFilled(true);
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent evt) {
                b.setCursor(Cursor.getDefaultCursor());
                b.setContentAreaFilled(false);
            }
        });

        
        return b;
    }

    private static Icon safeLoadIcon(String path, int size) {
        Icon ic = ImageUtilities.loadImageIcon(path, true);
        if (ic != null) return ic;

        int s = Math.max(12, size);
        BufferedImage bi = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = bi.createGraphics();
        g2.setColor(new java.awt.Color(0, 0, 0, 0)); g2.fillRect(0, 0, s, s);
        g2.setColor(new java.awt.Color(0, 0, 0, 140)); g2.drawRect(1, 1, s - 3, s - 3);
        g2.dispose();
        return new ImageIcon(bi);
    }
}