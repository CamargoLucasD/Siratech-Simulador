package frontend;

import backend.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * SimuladorFrame — JFrame principal do simulador.
 *
 * Alterações em relação à versão anterior:
 *   1. instanciarTelas() passa FazendaService e AnimalService para TelaConfiguracoes.
 *   2. conectarListeners() registra engine.setOnFazendaTrocada() para atualizar
 *      o label da fazenda na barra superior, resetar os controles de simulação
 *      e logar o evento.
 */
public class SimuladorFrame extends JFrame {

    // ── Cores tema escuro ─────────────────────────────────────────────────────────
    static final Color COR_BG_DARK   = new Color(15, 17, 21);
    static final Color COR_BG_PANEL  = new Color(22, 26, 32);
    static final Color COR_BG_CARD   = new Color(28, 33, 42);
    static final Color COR_BORDA     = new Color(42, 50, 62);
    static final Color COR_TEXTO     = new Color(210, 215, 220);
    static final Color COR_TEXTO_SEC = new Color(130, 140, 155);
    static final Color COR_VERDE     = new Color(60, 200, 100);
    static final Color COR_VERMELHO  = new Color(220, 60, 60);
    static final Color COR_AMARELO   = new Color(230, 180, 50);
    static final Color COR_AZUL      = new Color(60, 140, 220);
    static final Color COR_ACENTO    = new Color(80, 200, 120);

    // ── Componentes principais ────────────────────────────────────────────────────
    private MapaPanel     mapaPanel;
    private PainelLateral painelLateral;
    private PainelLog     painelLog;
    private MinimapPanel  minimapPanel;

    // ── Overlay ───────────────────────────────────────────────────────────────────
    private OverlayPanel overlayPanel;

    // ── Telas do overlay ──────────────────────────────────────────────────────────
    private TelaDashboard     telaDashboard;
    private TelaAnimais       telaAnimais;
    private TelaMonitoramento telaMonitoramento;
    private TelaAlertas       telaAlertas;
    private TelaRelatorios    telaRelatorios;
    private TelaConfiguracoes telaConfiguracoes;

    // ── Labels da barra superior ──────────────────────────────────────────────────
    private JLabel labelTick;
    private JLabel labelTotal;
    private JLabel labelForaArea;
    private JLabel labelEmResgate;
    private JLabel labelFazenda;
    private JLabel labelData;
    private JLabel labelHora;
    private Timer  clockTimer;

    // ── Botões de controle ────────────────────────────────────────────────────────
    private JButton btnIniciar;
    private JButton btnPausar;
    private JButton btnReset;

    // ── Backend ───────────────────────────────────────────────────────────────────
    private BackendSimulador backend;

    // ══════════════════════════════════════════════════════════════════════════════
    // Construtor
    // ══════════════════════════════════════════════════════════════════════════════
    public SimuladorFrame() {
        super("SIRATECH SIMULADOR");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1400, 900);
        setMinimumSize(new Dimension(1100, 700));
        setLocationRelativeTo(null);
        getContentPane().setBackground(COR_BG_DARK);

        inicializarBackend();
        construirLayout();
        instanciarTelas();
        conectarListeners();
        iniciarRelogio();
        configurarTeclado();
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Inicialização
    // ══════════════════════════════════════════════════════════════════════════════

    private void inicializarBackend() {
        try {
            backend = BackendSimulador.getInstance();
        } catch (RuntimeException ex) {
            JOptionPane.showMessageDialog(null,
                "Erro ao inicializar o simulador:\n" + ex.getMessage(),
                "Erro — SIRATECH Simulador",
                JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }

    private void construirLayout() {
        setLayout(new BorderLayout(0, 0));
        add(criarBarraSuperior(), BorderLayout.NORTH);

        JPanel centro = new JPanel(new BorderLayout(0, 0));
        centro.setBackground(COR_BG_DARK);
        centro.add(criarMenuLateral(), BorderLayout.WEST);

        mapaPanel    = new MapaPanel();
        overlayPanel = new OverlayPanel();

        JLayeredPane mapaLayer = new JLayeredPane() {
            @Override
            public void doLayout() {
                Dimension d = getSize();
                mapaPanel.setBounds(0, 0, d.width, d.height);
                overlayPanel.setBounds(0, 0, d.width, d.height);
            }
        };
        mapaLayer.add(mapaPanel,    JLayeredPane.DEFAULT_LAYER);
        mapaLayer.add(overlayPanel, JLayeredPane.PALETTE_LAYER);
        centro.add(mapaLayer, BorderLayout.CENTER);

        JPanel painelDireito = new JPanel(new BorderLayout(0, 0));
        painelDireito.setBackground(COR_BG_DARK);
        painelDireito.setPreferredSize(new Dimension(260, 0));

        painelLateral = new PainelLateral(backend);
        painelDireito.add(painelLateral, BorderLayout.CENTER);

        minimapPanel = new MinimapPanel(mapaPanel);
        painelDireito.add(minimapPanel, BorderLayout.SOUTH);

        centro.add(painelDireito, BorderLayout.EAST);
        add(centro, BorderLayout.CENTER);

        painelLog = new PainelLog();
        add(painelLog, BorderLayout.SOUTH);
    }

    private void instanciarTelas() {
        SimulacaoEngine engine = backend.getEngine();

        Runnable fecharOverlay = overlayPanel::fechar;

        java.util.function.BiConsumer<Double, Double> navegar =
            (x, y) -> mapaPanel.centralizarEm(x, y);

        java.util.function.Consumer<AnimalSimulado> selecionarAnimal = a -> {
            painelLateral.selecionarAnimal(a);
            mapaPanel.selecionarAnimal(a);
        };

        telaDashboard     = new TelaDashboard(engine);
        telaAnimais       = new TelaAnimais(engine, fecharOverlay, navegar, selecionarAnimal);
        telaMonitoramento = new TelaMonitoramento(engine);
        telaAlertas       = new TelaAlertas(engine, fecharOverlay, navegar, selecionarAnimal);
        telaRelatorios    = new TelaRelatorios(engine);

        // ── TelaConfiguracoes recebe os serviços para listar/trocar fazendas ──────
        telaConfiguracoes = new TelaConfiguracoes(
            engine, mapaPanel,
            backend.getFazendaService(),
            backend.getAnimalService()
        );

        overlayPanel.setOnFechar(this::restaurarMenuSemAtivo);
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Barra Superior
    // ══════════════════════════════════════════════════════════════════════════════

    private JPanel criarBarraSuperior() {
        JPanel barra = new JPanel(new BorderLayout(0, 0));
        barra.setBackground(COR_BG_DARK);
        barra.setPreferredSize(new Dimension(0, 48));
        barra.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, COR_BORDA));

        JPanel esq = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        esq.setBackground(COR_BG_DARK);
        esq.setBorder(new EmptyBorder(0, 16, 0, 0));

        JLabel titulo = new JLabel("SIRATECH");
        titulo.setFont(new Font("SansSerif", Font.BOLD, 14));
        titulo.setForeground(COR_ACENTO);
        esq.add(titulo);

        JLabel subtitulo = new JLabel("  SIMULADOR");
        subtitulo.setFont(new Font("SansSerif", Font.PLAIN, 13));
        subtitulo.setForeground(COR_TEXTO_SEC);
        esq.add(subtitulo);

        JPanel centro = new JPanel(new FlowLayout(FlowLayout.CENTER, 18, 0));
        centro.setBackground(COR_BG_DARK);

        labelData = new JLabel("—");
        labelData.setFont(new Font("SansSerif", Font.PLAIN, 12));
        labelData.setForeground(COR_TEXTO_SEC);

        labelHora = new JLabel("—");
        labelHora.setFont(new Font("Monospaced", Font.BOLD, 13));
        labelHora.setForeground(COR_TEXTO);

        centro.add(labelData);
        centro.add(criarSeparadorBarra());
        centro.add(labelHora);
        centro.add(criarSeparadorBarra());

        JPanel grpTotal = criarGroupStat("0", "animais", COR_VERDE);
        labelTotal = (JLabel) grpTotal.getComponent(1);
        centro.add(grpTotal);

        JPanel grpFora = criarGroupStat("0", "fora da área", COR_VERMELHO);
        labelForaArea = (JLabel) grpFora.getComponent(1);
        centro.add(grpFora);

        JPanel grpResgate = criarGroupStat("0", "em resgate", COR_AMARELO);
        labelEmResgate = (JLabel) grpResgate.getComponent(1);
        centro.add(grpResgate);

        JPanel dir = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        dir.setBackground(COR_BG_DARK);

        labelFazenda = new JLabel("Fazenda —");
        labelFazenda.setFont(new Font("SansSerif", Font.PLAIN, 12));
        labelFazenda.setForeground(COR_TEXTO_SEC);
        dir.add(labelFazenda);

        labelTick = new JLabel("Tick: 0");
        labelTick.setFont(new Font("Monospaced", Font.PLAIN, 11));
        labelTick.setForeground(COR_TEXTO_SEC);
        dir.add(labelTick);

        dir.add(criarSeparadorBarra());

        btnIniciar = criarBotaoAcao("▶  Iniciar", COR_ACENTO);
        btnPausar  = criarBotaoAcao("⏸  Pausar",  COR_AMARELO);
        btnReset   = criarBotaoAcao("↺  Reset",   COR_TEXTO_SEC);
        btnPausar.setVisible(false);
        dir.add(btnIniciar);
        dir.add(btnPausar);
        dir.add(btnReset);
        dir.add(Box.createHorizontalStrut(4));

        JButton btnConfig = criarBotaoIcone("⚙", COR_TEXTO_SEC, 32);
        btnConfig.addActionListener(e -> {
            setMenuAtivo(5);
            overlayPanel.mostrar(telaConfiguracoes, "⚙  Configurações");
        });
        dir.add(btnConfig);
        dir.add(Box.createHorizontalStrut(4));

        barra.add(esq,    BorderLayout.WEST);
        barra.add(centro, BorderLayout.CENTER);
        barra.add(dir,    BorderLayout.EAST);
        return barra;
    }

    private JPanel criarGroupStat(String num, String desc, Color cor) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        p.setBackground(COR_BG_DARK);
        JLabel ponto = new JLabel("●");
        ponto.setFont(new Font("SansSerif", Font.PLAIN, 9));
        ponto.setForeground(cor);
        JLabel numero = new JLabel(num);
        numero.setFont(new Font("SansSerif", Font.BOLD, 15));
        numero.setForeground(Color.WHITE);
        JLabel descLabel = new JLabel(desc);
        descLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        descLabel.setForeground(COR_TEXTO_SEC);
        p.add(ponto);
        p.add(numero);
        p.add(descLabel);
        return p;
    }

    private JSeparator criarSeparadorBarra() {
        JSeparator sep = new JSeparator(JSeparator.VERTICAL);
        sep.setPreferredSize(new Dimension(1, 20));
        sep.setForeground(COR_BORDA);
        return sep;
    }

    private JButton criarBotaoIcone(String icone, Color cor, int size) {
        JButton b = new JButton(icone);
        b.setFont(new Font("SansSerif", Font.PLAIN, 16));
        b.setForeground(cor);
        b.setBackground(COR_BG_DARK);
        b.setPreferredSize(new Dimension(size, size));
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { b.setForeground(Color.WHITE); }
            public void mouseExited(MouseEvent e)  { b.setForeground(cor); }
        });
        return b;
    }

    private JButton criarBotaoAcao(String texto, Color corTexto) {
        JButton b = new JButton(texto) {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0;
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (getModel().isRollover()) {
                    g.setColor(new Color(corTexto.getRed(), corTexto.getGreen(), corTexto.getBlue(), 28));
                    g.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
                }
                g.setColor(COR_BORDA);
                g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
                super.paintComponent(g0);
            }
        };
        b.setFont(new Font("SansSerif", Font.PLAIN, 12));
        b.setForeground(corTexto);
        b.setBackground(COR_BG_DARK);
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setOpaque(false);
        b.setPreferredSize(new Dimension(110, 30));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Menu Lateral
    // ══════════════════════════════════════════════════════════════════════════════

    private int activeMenuIndex = -1;
    private final List<JLabel> menuItems = new java.util.ArrayList<>();

    private JPanel criarMenuLateral() {
        JPanel menu = new JPanel();
        menu.setLayout(new BoxLayout(menu, BoxLayout.Y_AXIS));
        menu.setBackground(COR_BG_PANEL);
        menu.setPreferredSize(new Dimension(52, 0));
        menu.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, COR_BORDA));
        menu.add(Box.createVerticalStrut(8));

        String[][] itens = {
            {"⊞", "Dashboard"},
            {"♦", "Animais"},
            {"◎", "Monitoramento"},
            {"⚠", "Alertas"},
            {"☰", "Relatórios"},
            {"⚙", "Configurações"},
        };

        for (int i = 0; i < itens.length; i++) {
            final int idx = i;
            JLabel item = criarItemMenu(itens[i][0], itens[i][1], false);
            item.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (idx == activeMenuIndex && overlayPanel.isVisivel()) {
                        overlayPanel.fechar();
                        restaurarMenuSemAtivo();
                        return;
                    }
                    setMenuAtivo(idx);
                    executarAcaoMenu(idx);
                }
                @Override public void mouseEntered(MouseEvent e) {
                    if (idx != activeMenuIndex) item.setForeground(COR_TEXTO);
                }
                @Override public void mouseExited(MouseEvent e) {
                    if (idx != activeMenuIndex) item.setForeground(COR_TEXTO_SEC);
                }
            });
            menuItems.add(item);
            menu.add(item);
            menu.add(Box.createVerticalStrut(4));
        }

        menu.add(Box.createVerticalGlue());

        JLabel ico = new JLabel("◈", SwingConstants.CENTER);
        ico.setFont(new Font("SansSerif", Font.BOLD, 18));
        ico.setForeground(COR_ACENTO);
        ico.setAlignmentX(Component.CENTER_ALIGNMENT);
        ico.setMaximumSize(new Dimension(52, 40));
        ico.setToolTipText("Simulador");
        menu.add(ico);
        menu.add(Box.createVerticalStrut(8));

        return menu;
    }

    private void setMenuAtivo(int idx) {
        activeMenuIndex = idx;
        for (int i = 0; i < menuItems.size(); i++) {
            menuItems.get(i).setForeground(i == idx ? COR_ACENTO : COR_TEXTO_SEC);
            menuItems.get(i).repaint();
        }
    }

    private void restaurarMenuSemAtivo() {
        activeMenuIndex = -1;
        for (JLabel item : menuItems) {
            item.setForeground(COR_TEXTO_SEC);
            item.repaint();
        }
    }

    private void executarAcaoMenu(int idx) {
        switch (idx) {
            case 0 -> overlayPanel.mostrar(telaDashboard,     "⊞  Dashboard");
            case 1 -> overlayPanel.mostrar(telaAnimais,       "♦  Animais");
            case 2 -> overlayPanel.mostrar(telaMonitoramento, "◎  Monitoramento");
            case 3 -> overlayPanel.mostrar(telaAlertas,       "⚠  Alertas");
            case 4 -> overlayPanel.mostrar(telaRelatorios,    "☰  Relatórios");
            case 5 -> overlayPanel.mostrar(telaConfiguracoes, "⚙  Configurações");
        }
    }

    private JLabel criarItemMenu(String icone, String tooltip, boolean ativo) {
        JLabel l = new JLabel(icone, SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0;
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                boolean isAtivo = menuItems.indexOf(this) == activeMenuIndex;
                if (isAtivo) {
                    g.setColor(new Color(COR_ACENTO.getRed(), COR_ACENTO.getGreen(), COR_ACENTO.getBlue(), 25));
                    g.fillRoundRect(4, 2, getWidth() - 8, getHeight() - 4, 8, 8);
                    g.setColor(COR_ACENTO);
                    g.fillRoundRect(0, 8, 3, getHeight() - 16, 2, 2);
                }
                super.paintComponent(g0);
            }
        };
        l.setFont(new Font("SansSerif", Font.PLAIN, 18));
        l.setForeground(ativo ? COR_ACENTO : COR_TEXTO_SEC);
        l.setMaximumSize(new Dimension(52, 44));
        l.setPreferredSize(new Dimension(52, 44));
        l.setToolTipText(tooltip);
        l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return l;
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Listeners do engine
    // ══════════════════════════════════════════════════════════════════════════════

    private void conectarListeners() {
        SimulacaoEngine engine = backend.getEngine();

        engine.setOnTick(tick -> SwingUtilities.invokeLater(() -> {
            List<AnimalSimulado> animais = engine.getAnimais();
            mapaPanel.setAnimais(animais);
            mapaPanel.setResgatadores(engine.getResgatadores());
            minimapPanel.setAnimais(animais);
            labelTick.setText("Tick: " + tick);

            long fora    = animais.stream().filter(a -> a.getEstado() == EstadoAnimal.FORA_DA_AREA).count();
            long resgate = animais.stream().filter(a ->
                a.getEstado() == EstadoAnimal.EM_RESGATE ||
                a.getEstado() == EstadoAnimal.RETORNANDO).count();
            labelTotal.setText(String.valueOf(animais.size()));
            labelForaArea.setText(String.valueOf(fora));
            labelEmResgate.setText(String.valueOf(resgate));

            painelLateral.atualizarAnimalSelecionado();
        }));

        engine.setOnSaida(a -> SwingUtilities.invokeLater(() -> {
            String id  = String.format("%03d", a.getAnimal().getId());
            String msg = "Animal #" + id + " saiu da área delimitada";
            painelLog.logAlerta(msg);
            mapaPanel.adicionarNotificacao("⚠  " + msg, COR_VERMELHO);
            painelLateral.selecionarAnimal(a);
            mapaPanel.selecionarAnimal(a);
            mapaPanel.centralizarEm(a.getX(), a.getY());
        }));

        engine.setOnResgate(a -> SwingUtilities.invokeLater(() -> {
            String id = String.format("%03d", a.getAnimal().getId());
            String nomeResgatador = a.getResgatadorNome() != null ? a.getResgatadorNome() : "—";
            painelLog.adicionarResgate(id, nomeResgatador, 0);
            painelLog.logInfo("Resgate iniciado → #" + id + " por " + nomeResgatador);
        }));

        engine.setOnResgateCompleto(a -> SwingUtilities.invokeLater(() -> {
            String id  = String.format("%03d", a.getAnimal().getId());
            String msg = "Animal #" + id + " retornou à área";
            painelLog.logSucesso(msg);
            painelLog.removerResgate(id);
            mapaPanel.adicionarNotificacao("✓  " + msg, COR_VERDE);
        }));

        // ── Troca de fazenda ────────────────────────────────────────────────────────
        // Chamado por engine.trocarFazenda() após recarregar tudo.
        // Atualiza a barra superior e reseta os botões de controle da simulação.
        engine.setOnFazendaTrocada(novaFazenda -> SwingUtilities.invokeLater(() -> {
            // Atualiza label da barra
            labelFazenda.setText(novaFazenda.getNome() + "  ▾");

            // Reseta contadores
            labelTick.setText("Tick: 0");
            labelTotal.setText(String.valueOf(engine.getAnimais().size()));
            labelForaArea.setText("0");
            labelEmResgate.setText("0");

            // Volta botões para o estado inicial (engine parado)
            btnIniciar.setVisible(true);
            btnPausar.setVisible(false);
            btnPausar.setText("⏸  Pausar");

            // Limpa o mapa e o painel lateral
            mapaPanel.setAnimais(engine.getAnimais());
            mapaPanel.setResgatadores(engine.getResgatadores());
            minimapPanel.setAnimais(engine.getAnimais());
            painelLateral.limpar();

            // Log do evento
            painelLog.logInfo("Fazenda alterada para: " + novaFazenda.getNome()
                + " (" + engine.getAnimais().size() + " animais)");
        }));

        mapaPanel.setOnAnimalClick(a -> {
            painelLateral.selecionarAnimal(a);
            mapaPanel.selecionarAnimal(a);
        });

        painelLateral.setOnBuscarAnimal(a -> {
            engine.iniciarResgate(a);
            String msg = "Resgate iniciado para #"
                + String.format("%03d", a.getAnimal().getId());
            painelLog.logInfo(msg);
        });

        minimapPanel.setOnNavigar((mx, my) -> mapaPanel.centralizarEm(mx, my));

        if (backend.getFazenda() != null) {
            labelFazenda.setText(backend.getFazenda().getNome() + "  ▾");
        }

        btnIniciar.addActionListener(e -> {
            engine.iniciar();
            btnIniciar.setVisible(false);
            btnPausar.setVisible(true);
            painelLog.logInfo("Simulação iniciada");
        });

        btnPausar.addActionListener(e -> {
            if (engine.isPausado()) {
                engine.retomar();
                btnPausar.setText("⏸  Pausar");
                painelLog.logInfo("Simulação retomada");
            } else {
                engine.pausar();
                btnPausar.setText("▶  Retomar");
                painelLog.logInfo("Simulação pausada");
            }
        });

        btnReset.addActionListener(e -> mostrarConfirmacaoReset(engine));
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Confirmação de reset
    // ══════════════════════════════════════════════════════════════════════════════

    private void mostrarConfirmacaoReset(SimulacaoEngine engine) {
        JPanel painelConfirm = new JPanel(new GridBagLayout());
        painelConfirm.setOpaque(false);

        JPanel card = new JPanel() {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0;
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(COR_BG_CARD);
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g.setColor(COR_BORDA);
                g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
            }
        };
        card.setOpaque(false);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createEmptyBorder(28, 36, 24, 36));
        card.setPreferredSize(new Dimension(340, 160));

        JLabel icone = new JLabel("↺", SwingConstants.CENTER);
        icone.setFont(new Font("SansSerif", Font.BOLD, 28));
        icone.setForeground(COR_TEXTO_SEC);
        icone.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(icone);
        card.add(Box.createVerticalStrut(10));

        JLabel pergunta = new JLabel("Reiniciar a simulação?");
        pergunta.setFont(new Font("SansSerif", Font.BOLD, 14));
        pergunta.setForeground(COR_TEXTO);
        pergunta.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(pergunta);
        card.add(Box.createVerticalStrut(6));

        JLabel aviso = new JLabel("O progresso atual será perdido.");
        aviso.setFont(new Font("SansSerif", Font.PLAIN, 11));
        aviso.setForeground(COR_TEXTO_SEC);
        aviso.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(aviso);
        card.add(Box.createVerticalStrut(20));

        JPanel botoes = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        botoes.setOpaque(false);

        JButton btnCancelar  = criarBotaoAcao("Cancelar",  COR_TEXTO_SEC);
        JButton btnConfirmar = criarBotaoAcao("Reiniciar", COR_VERMELHO);

        btnCancelar.addActionListener(ev -> overlayPanel.fechar());
        btnConfirmar.addActionListener(ev -> {
            overlayPanel.fechar();
            engine.resetar();
            btnIniciar.setVisible(true);
            btnPausar.setVisible(false);
            btnPausar.setText("⏸  Pausar");
            painelLog.logInfo("Simulação reiniciada");
            restaurarMenuSemAtivo();
        });

        botoes.add(btnCancelar);
        botoes.add(btnConfirmar);
        card.add(botoes);
        painelConfirm.add(card);
        overlayPanel.mostrar(painelConfirm, "↺  Reset");
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Relógio
    // ══════════════════════════════════════════════════════════════════════════════

    private void iniciarRelogio() {
        DateTimeFormatter fmtData = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        DateTimeFormatter fmtHora = DateTimeFormatter.ofPattern("HH:mm");
        clockTimer = new Timer(1000, e -> {
            LocalDateTime agora = LocalDateTime.now();
            labelData.setText(agora.format(fmtData));
            labelHora.setText(agora.format(fmtHora));
        });
        clockTimer.start();
        LocalDateTime agora = LocalDateTime.now();
        labelData.setText(agora.format(fmtData));
        labelHora.setText(agora.format(fmtHora));
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Teclado global
    // ══════════════════════════════════════════════════════════════════════════════

    private void configurarTeclado() {
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "pausar");
        getRootPane().getActionMap().put("pausar", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (btnPausar.isVisible()) btnPausar.doClick();
            }
        });

        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "deselecionar");
        getRootPane().getActionMap().put("deselecionar", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (overlayPanel.isVisivel()) {
                    overlayPanel.fechar();
                    restaurarMenuSemAtivo();
                } else {
                    mapaPanel.deselecionarAnimal();
                    painelLateral.limpar();
                }
            }
        });
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // main
    // ══════════════════════════════════════════════════════════════════════════════

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        UIManager.put("Panel.background",     COR_BG_DARK);
        UIManager.put("Label.foreground",      COR_TEXTO);
        UIManager.put("Button.background",     COR_BG_PANEL);
        UIManager.put("Button.foreground",     COR_TEXTO);
        UIManager.put("ScrollPane.background", COR_BG_DARK);
        UIManager.put("ScrollBar.background",  COR_BG_PANEL);
        UIManager.put("ScrollBar.thumb",       COR_BORDA);
        UIManager.put("ToolTip.background",    COR_BG_CARD);
        UIManager.put("ToolTip.foreground",    COR_TEXTO);
        UIManager.put("ToolTip.border",        BorderFactory.createLineBorder(COR_BORDA));

        SwingUtilities.invokeLater(() -> new SimuladorFrame().setVisible(true));
    }
}
