package br.com.facilitasig.ui;

import br.com.facilitasig.AppInfo;
import br.com.facilitasig.company.Company;
import br.com.facilitasig.company.CompanyRepository;
import br.com.facilitasig.db.Database;
import br.com.facilitasig.fiscal.Direction;
import br.com.facilitasig.fiscal.DocStatus;
import br.com.facilitasig.fiscal.FiscalDocRepository;
import br.com.facilitasig.fiscal.FiscalService;
import br.com.facilitasig.core.ApuracaoSession;
import br.com.facilitasig.core.Feature;
import br.com.facilitasig.core.MenuGroup;
import br.com.facilitasig.core.Step;
import br.com.facilitasig.core.WorkArea;
import br.com.facilitasig.core.WorkAreas;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JDesktopPane;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.awt.Insets;
import java.beans.PropertyVetoException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Janela principal: menus no topo, barra de ferramentas, roteiro de etapas à
 * esquerda e área de janelas internas (MDI) no centro.
 */
public class MainFrame extends JFrame {

    private final List<WorkArea> areas = WorkAreas.all();
    private final JDesktopPane desktop = new JDesktopPane();
    private final StatusBar statusBar = new StatusBar("Versão " + AppInfo.VERSION);
    private final JPanel toolbarHolder = new JPanel(new BorderLayout());
    private final JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
    /** Modo global das janelas internas. */
    private enum WindowMode { FREE, CASCADE, TILE }

    private WindowMode windowMode = WindowMode.FREE;
    private final Map<String, JInternalFrame> openFrames = new LinkedHashMap<>();

    private final CompanyRepository companies;
    private Company activeCompany;
    private final FiscalService fiscal;
    private WorkArea area;
    private ApuracaoSession session;
    private StepNavigatorPanel navigator;
    private boolean navigatorVisible = true;

    public MainFrame() {
        super(AppInfo.NAME);
        setIconImage(Icons.appIcon(64));
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1000, 560));
        java.awt.Rectangle screen = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setSize(Math.min(1280, screen.width), Math.min(800, screen.height));
        setLocationRelativeTo(null);

        desktop.setBackground(new Color(0xE9ECEF));
        split.setRightComponent(desktop);
        desktop.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                applyWindowMode();
            }
        });
        split.setDividerSize(1);
        split.setBorder(null);

        add(toolbarHolder, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);

        Database db;
        try {
            db = Database.openDefault();
        } catch (RuntimeException ex) {
            JOptionPane.showMessageDialog(null, "Não foi possível abrir o banco de dados interno:\n" + ex.getMessage(),
                    AppInfo.NAME, JOptionPane.ERROR_MESSAGE);
            throw ex;
        }
        companies = new CompanyRepository(db);
        fiscal = new FiscalService(new FiscalDocRepository(db));
        restoreActiveCompany();
        try {
            String wm = companies.setting("window_mode");
            if (wm != null) {
                windowMode = WindowMode.valueOf(wm);
            }
        } catch (IllegalArgumentException ignored) {
            windowMode = WindowMode.FREE;
        }
        loadArea(areas.get(0));
    }

    // ------------------------------------------------------------------ empresa ativa

    private static final String ACTIVE_KEY = "active_company_id";

    private void restoreActiveCompany() {
        String id = companies.setting(ACTIVE_KEY);
        if (id != null) {
            try {
                activeCompany = companies.findById(Long.parseLong(id));
            } catch (NumberFormatException ignored) {
                activeCompany = null;
            }
        }
        statusBar.setCompany(activeCompany == null ? null : activeCompany.legalName());
    }

    private void setActiveCompany(Company company) {
        activeCompany = company;
        closeFrame("mov.importar");
        closeFrame("mov.entradas");
        closeFrame("mov.saidas");
        companies.putSetting(ACTIVE_KEY, company == null ? "" : String.valueOf(company.id()));
        statusBar.setCompany(company == null ? null : company.legalName());
    }

    public Company activeCompany() {
        return activeCompany;
    }

    public CompanyRepository companies() {
        return companies;
    }

    // ------------------------------------------------------------------ área de trabalho

    private void loadArea(WorkArea newArea) {
        closeAllWindows();
        this.area = newArea;
        this.session = new ApuracaoSession(newArea.steps());
        this.session.addListener(this::updateStatus);

        if (newArea.steps().isEmpty()) {
            navigator = null;
            split.setLeftComponent(null);
        } else {
            navigator = new StepNavigatorPanel(session, this::openStep);
            split.setLeftComponent(navigator);
            navigator.setVisible(navigatorVisible);
            split.setDividerLocation(navigatorVisible ? 270 : 0);
        }

        setJMenuBar(buildMenuBar());
        toolbarHolder.removeAll();
        toolbarHolder.add(buildToolbar(), BorderLayout.CENTER);
        toolbarHolder.add(buildSearchPanel(), BorderLayout.EAST);
        toolbarHolder.revalidate();
        statusBar.setArea(newArea.name());
        updateStatus();
        revalidate();
        repaint();
    }

    private void updateStatus() {
        applyNavigator();
        if (area.steps().isEmpty()) {
            statusBar.setStep("");
        } else if (session.allDone()) {
            statusBar.setStep("Rotina concluída");
        } else {
            int i = session.currentIndex();
            statusBar.setStep("Etapa atual: " + (i + 1) + " — " + area.steps().get(i).title());
        }
    }

    // ------------------------------------------------------------------ menus

    private JMenuBar buildMenuBar() {
        JMenuBar bar = new JMenuBar();
        bar.add(buildAreasMenu());
        for (MenuGroup group : area.menus()) {
            bar.add(buildGroupMenu(group));
        }
        bar.add(buildWindowMenu());
        bar.add(buildHelpMenu());
        return bar;
    }

    private JMenu buildAreasMenu() {
        JMenu menu = new JMenu("Áreas de Trabalho");
        ButtonGroup group = new ButtonGroup();
        for (WorkArea candidate : areas) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(candidate.name(), candidate == area);
            item.setToolTipText(candidate.description());
            item.addActionListener(e -> {
                if (candidate != area) {
                    loadArea(candidate);
                }
            });
            group.add(item);
            menu.add(item);
        }
        menu.addSeparator();
        JMenuItem exit = new JMenuItem("Sair");
        exit.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
        exit.addActionListener(e -> exitApp());
        menu.add(exit);
        return menu;
    }

    private void exitApp() {
        dispose();
        System.exit(0);
    }

    private JMenu buildGroupMenu(MenuGroup group) {
        JMenu menu = new JMenu(group.title());
        for (Feature feature : group.items()) {
            if (feature.isSeparator()) {
                menu.addSeparator();
                continue;
            }
            JMenuItem item = new JMenuItem(feature.title(), Icons.of(feature, 16));
            item.setToolTipText(feature.description());
            item.addActionListener(e -> openFeature(feature));
            menu.add(item);
        }
        return menu;
    }

    private JMenu buildWindowMenu() {
        JMenu menu = new JMenu("Janelas");
        menu.addMenuListener(new MenuListener() {
            @Override
            public void menuSelected(MenuEvent e) {
                rebuildWindowMenu(menu);
            }

            @Override
            public void menuDeselected(MenuEvent e) {
            }

            @Override
            public void menuCanceled(MenuEvent e) {
            }
        });
        rebuildWindowMenu(menu);
        return menu;
    }

    private void rebuildWindowMenu(JMenu menu) {
        menu.removeAll();

        ButtonGroup modes = new ButtonGroup();
        menu.add(modeItem(modes, "Janelas livres", WindowMode.FREE,
                "Cada janela fica onde você a colocar."));
        menu.add(modeItem(modes, "Em cascata", WindowMode.CASCADE,
                "Todas as janelas, atuais e novas, ficam em cascata."));
        menu.add(modeItem(modes, "Lado a lado (ocupar toda a área)", WindowMode.TILE,
                "Todas as janelas dividem toda a área de trabalho, sem sobrepor."));
        JMenuItem closeAll = new JMenuItem("Fechar todas");
        closeAll.addActionListener(e -> closeAllWindows());
        menu.add(closeAll);
        menu.addSeparator();

        if (!area.steps().isEmpty()) {
            JCheckBoxMenuItem showNav = new JCheckBoxMenuItem("Exibir roteiro de apuração", navigatorVisible);
            showNav.addActionListener(e -> setNavigatorVisible(showNav.isSelected()));
            menu.add(showNav);
            JMenuItem reset = new JMenuItem("Reiniciar rotina de apuração");
            reset.addActionListener(e -> {
                closeAllWindows();
                session.reset();
            });
            menu.add(reset);
            menu.addSeparator();
        }

        if (openFrames.isEmpty()) {
            JMenuItem none = new JMenuItem("(nenhuma janela aberta)");
            none.setEnabled(false);
            menu.add(none);
        } else {
            int n = 1;
            for (JInternalFrame frame : openFrames.values()) {
                JMenuItem item = new JMenuItem(n++ + "  " + frame.getTitle());
                item.addActionListener(e -> bringToFront(frame));
                menu.add(item);
            }
        }
    }

    private JRadioButtonMenuItem modeItem(ButtonGroup group, String title, WindowMode mode, String tip) {
        JRadioButtonMenuItem item = new JRadioButtonMenuItem(title, windowMode == mode);
        item.setToolTipText(tip);
        item.addActionListener(e -> setWindowMode(mode));
        group.add(item);
        return item;
    }

    private void setWindowMode(WindowMode mode) {
        windowMode = mode;
        companies.putSetting("window_mode", mode.name());
        applyWindowMode();
    }

    private JMenu buildHelpMenu() {
        JMenu menu = new JMenu("Ajuda");
        JMenuItem about = new JMenuItem("Sobre o " + AppInfo.NAME);
        about.setAccelerator(KeyStroke.getKeyStroke("F1"));
        about.addActionListener(e -> AboutDialog.show(this));
        menu.add(about);
        return menu;
    }

    // ------------------------------------------------------------------ barra de ferramentas

    private JToolBar buildToolbar() {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        bar.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        for (Feature feature : area.toolbar()) {
            bar.add(toolbarButton(feature));
        }
        return bar;
    }

    /** Busca fixa à direita: nunca sai da tela, mesmo com a janela estreita. */
    private JPanel buildSearchPanel() {
        JTextField search = new JTextField(16);
        search.putClientProperty("JTextField.placeholderText", "Buscar funcionalidade...");
        search.putClientProperty("JTextField.showClearButton", true);
        search.setToolTipText("Digite e tecle Enter para buscar uma funcionalidade");
        search.addActionListener(e -> searchFeature(search));
        JPanel p = new JPanel(new java.awt.GridBagLayout());
        p.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 10));
        p.add(search);
        return p;
    }

    private JButton toolbarButton(Feature feature) {
        JButton button = new JButton(feature.shortTitle(), Icons.of(feature, 32));
        button.setVerticalTextPosition(SwingConstants.BOTTOM);
        button.setHorizontalTextPosition(SwingConstants.CENTER);
        button.setMargin(new Insets(4, 8, 2, 8));
        button.setFocusable(false);
        button.setToolTipText(feature.description());
        button.putClientProperty("JButton.buttonType", "toolBarButton");
        button.addActionListener(e -> openFeature(feature));
        return button;
    }

    private void searchFeature(JTextField field) {
        String query = normalize(field.getText());
        if (query.trim().isEmpty()) {
            return;
        }
        List<Feature> hits = new ArrayList<>();
        for (Feature f : area.allFeatures()) {
            if (normalize(f.title()).contains(query) || normalize(f.description()).contains(query)) {
                hits.add(f);
            }
        }
        if (hits.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Nenhuma funcionalidade encontrada para \"" + field.getText() + "\".",
                    "Busca", JOptionPane.INFORMATION_MESSAGE);
        } else if (hits.size() == 1) {
            openFeature(hits.get(0));
        } else {
            JPopupMenu popup = new JPopupMenu();
            for (Feature hit : hits) {
                JMenuItem item = new JMenuItem(hit.title(), Icons.of(hit, 16));
                item.addActionListener(e -> openFeature(hit));
                popup.add(item);
            }
            popup.show(field, 0, field.getHeight());
        }
    }

    private static String normalize(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase();
    }

    // ------------------------------------------------------------------ janelas internas

    private void openStep(int index) {
        openFeature(area.steps().get(index).feature());
    }

    private void openFeature(Feature feature) {
        int stepIndex = area.stepIndexOf(feature);
        if (stepIndex >= 0 && !session.isUnlocked(stepIndex)) {
            Step blocking = area.steps().get(session.currentIndex());
            JOptionPane.showMessageDialog(this,
                    "Conclua primeiro a etapa " + (session.currentIndex() + 1) + " — " + blocking.title() + ".",
                    "Etapa bloqueada", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        boolean needsCompany = "mov.importar".equals(feature.id()) || "mov.entradas".equals(feature.id())
                || "mov.saidas".equals(feature.id());
        if (needsCompany && activeCompany == null) {
            JOptionPane.showMessageDialog(this, "Selecione a empresa ativa em Cadastros > Empresas e Estabelecimentos.",
                    "Empresa", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JInternalFrame existing = openFrames.get(feature.id());
        if (existing != null) {
            bringToFront(existing);
            return;
        }

        Step step = stepIndex >= 0 ? area.steps().get(stepIndex) : null;
        PlaceholderFrame.StepActions actions = stepIndex < 0 ? null : new PlaceholderFrame.StepActions() {
            @Override
            public void complete() {
                if ("mov.importar".equals(feature.id()) && !documentsReadyToComplete()) {
                    return;
                }
                if ("cad.empresa".equals(feature.id()) && activeCompany == null) {
                    JOptionPane.showMessageDialog(MainFrame.this, "Defina a empresa ativa para concluir esta etapa.",
                            "Empresa", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }
                if (session.complete(stepIndex)) {
                    closeFrame(feature.id());
                    if (session.allDone()) {
                        JOptionPane.showMessageDialog(MainFrame.this, "Rotina de apuração concluída.",
                                "Apuração", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        openStep(session.currentIndex());
                    }
                }
            }

            @Override
            public void reopen() {
                session.reopen(stepIndex);
                closeFrame(feature.id());
                openFeature(feature);
            }

            @Override
            public boolean isDone() {
                return session.statusOf(stepIndex) == ApuracaoSession.Status.DONE;
            }
        };

        JInternalFrame frame;
        if ("cad.empresa".equals(feature.id())) {
            frame = new CompanyListFrame(feature, companies, new CompanyListFrame.Host() {
                @Override
                public Company activeCompany() {
                    return activeCompany;
                }

                @Override
                public void setActiveCompany(Company company) {
                    MainFrame.this.setActiveCompany(company);
                }
            }, actions);
        } else if ("mov.entradas".equals(feature.id()) || "mov.saidas".equals(feature.id())) {
            frame = new DocumentsFrame(feature, "mov.entradas".equals(feature.id()) ? Direction.ENTRADA : Direction.SAIDA,
                    fiscal, activeCompany);
        } else if ("mov.importar".equals(feature.id())) {
            frame = new DocumentsHubFrame(feature, activeCompany.id(), activeCompany.legalName(), fiscal.repository(),
                    () -> openFeatureById("mov.entradas"), () -> openFeatureById("mov.saidas"), actions);
        } else {
            frame = new PlaceholderFrame(feature, stepIndex + 1, step, actions);
        }
        int offset = (openFrames.size() % 8) * 28 + 20;
        frame.setLocation(offset, offset);
        frame.addInternalFrameListener(new InternalFrameAdapter() {
            @Override
            public void internalFrameClosed(InternalFrameEvent e) {
                openFrames.remove(feature.id());
                applyWindowMode();
            }
        });
        desktop.add(frame);
        openFrames.put(feature.id(), frame);
        frame.setVisible(true);
        bringToFront(frame);
        applyWindowMode();
    }

    /** Etapa 3: sem erros pendentes; com confirmação se não houver nenhuma nota. */
    private boolean documentsReadyToComplete() {
        int total = 0;
        int errors = 0;
        for (Direction dir : Direction.values()) {
            java.util.Map<DocStatus, Integer> c = fiscal.repository().counts(activeCompany.id(), dir);
            for (int v : c.values()) {
                total += v;
            }
            errors += c.get(DocStatus.ERRO);
        }
        if (errors > 0) {
            JOptionPane.showMessageDialog(this, "Há " + errors + " nota(s) com erro. Corrija, revalidade ou exclua antes de concluir.",
                    "Documentos", JOptionPane.WARNING_MESSAGE);
            return false;
        }
        if (total == 0) {
            return JOptionPane.showConfirmDialog(this, "Nenhuma nota foi importada para esta empresa. Concluir mesmo assim?",
                    "Documentos", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
        }
        return true;
    }

    private void closeFrame(String featureId) {
        JInternalFrame frame = openFrames.remove(featureId);
        if (frame != null) {
            frame.dispose();
        }
    }

    private void bringToFront(JInternalFrame frame) {
        try {
            if (frame.isIcon()) {
                frame.setIcon(false);
            }
            frame.toFront();
            frame.setSelected(true);
        } catch (PropertyVetoException ignored) {
            // a janela recusou a seleção; nada a fazer
        }
    }

    private void closeAllWindows() {
        for (JInternalFrame frame : new ArrayList<>(openFrames.values())) {
            frame.dispose();
        }
        openFrames.clear();
    }

    /** Aplica o modo global às janelas abertas: chamado ao abrir, fechar, redimensionar ou trocar o modo. */
    private void applyWindowMode() {
        if (windowMode == WindowMode.FREE || openFrames.isEmpty()) {
            return;
        }
        List<JInternalFrame> frames = new ArrayList<>(openFrames.values());
        int dw = desktop.getWidth();
        int dh = desktop.getHeight();
        if (dw <= 0 || dh <= 0) {
            return;
        }
        for (JInternalFrame frame : frames) {
            try {
                if (frame.isIcon()) {
                    frame.setIcon(false);
                }
                if (frame.isMaximum()) {
                    frame.setMaximum(false);
                }
            } catch (PropertyVetoException ignored) {
                // mantém o estado atual
            }
        }
        if (windowMode == WindowMode.CASCADE) {
            for (int i = 0; i < frames.size(); i++) {
                JInternalFrame frame = frames.get(i);
                int off = 20 + (i % 10) * 28;
                int w = Math.min(frame.getWidth(), Math.max(300, dw - off));
                int h = Math.min(frame.getHeight(), Math.max(200, dh - off));
                frame.setBounds(off, off, w, h);
            }
        } else {
            int cols = (int) Math.ceil(Math.sqrt(frames.size()));
            int rows = (int) Math.ceil(frames.size() / (double) cols);
            for (int i = 0; i < frames.size(); i++) {
                int col = i % cols;
                int row = i / cols;
                int x = col * dw / cols;
                int y = row * dh / rows;
                // a última janela de uma linha incompleta ocupa o espaço que sobra
                int inRow = Math.min(cols, frames.size() - row * cols);
                int x2 = col == inRow - 1 ? dw : (col + 1) * dw / cols;
                if (inRow < cols) {
                    x = col * dw / inRow;
                    x2 = (col + 1) * dw / inRow;
                }
                frames.get(i).setBounds(x, y, x2 - x, (row + 1) * dh / rows - y);
            }
        }
    }

    private void setNavigatorVisible(boolean visible) {
        navigatorVisible = visible;
        applyNavigator();
    }

    /** O roteiro some quando a rotina termina (e volta se alguma etapa for reaberta). */
    private void applyNavigator() {
        if (navigator == null) {
            return;
        }
        boolean show = navigatorVisible && !session.allDone();
        navigator.setVisible(show);
        split.setDividerLocation(show ? 270 : 0);
        split.setDividerSize(show ? 1 : 0);
    }

    /** Usado por testes e capturas de tela: abre uma funcionalidade pelo id. */
    public void openFeatureById(String id) {
        area.findFeature(id).ifPresent(this::openFeature);
    }

    /** Usado por testes: acesso à sessão de apuração. */
    public ApuracaoSession session() {
        return session;
    }
}
