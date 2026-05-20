package frontend;

import backend.Animal;
import backend.AnimalService;
import backend.Fazenda;
import backend.FazendaService;
import backend.SimulacaoEngine;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

import static frontend.SimuladorFrame.*;

/**
 * TelaConfiguracoes — velocidade, toggles visuais e troca de fazenda.
 *
 * Novidade: seção "🏠 FAZENDA ATIVA" com JComboBox listando todas as
 * fazendas do banco. Ao confirmar, chama engine.trocarFazenda() que
 * para o engine, recarrega os animais e notifica o frontend via
 * onFazendaTrocada (registrado no SimuladorFrame).
 */
public class TelaConfiguracoes extends JPanel implements OverlayPanel.TelaBase {

    private final SimulacaoEngine engine;
    private final MapaPanel       mapaPanel;
    private final FazendaService  fazendaService;
    private final AnimalService   animalService;

    // ── Velocidade ────────────────────────────────────────────────────────────────
    private static final int[]    VELOCIDADES = {50, 100, 200, 500};
    private static final String[] LABELS_VEL  = {"Muito rápido", "Rápido", "Normal", "Lento"};
    private static final String[] SUBLABELS   = {"50 ms/tick", "100 ms/tick", "200 ms/tick", "500 ms/tick"};

    private int velocidadeAtualMs;
    private final BotaoVelocidade[] botoesVel = new BotaoVelocidade[VELOCIDADES.length];

    // ── Toggles visuais ───────────────────────────────────────────────────────────
    private ToggleSwitch toggleGrade;
    private ToggleSwitch toggleNomes;
    private ToggleSwitch toggleEstados;
    private ToggleSwitch toggleResgatadores;
    private ToggleSwitch toggleMinimap;

    // ── Labels de status ──────────────────────────────────────────────────────────
    private final JLabel labelStatusEngine;

    // ── Fazenda ───────────────────────────────────────────────────────────────────
    private JComboBox<Fazenda> comboFazendas;
    private JLabel             labelFazendaAtual;
    private JLabel             labelAnimaisFazenda;

    // ─────────────────────────────────────────────────────────────────────────────

    public TelaConfiguracoes(SimulacaoEngine engine, MapaPanel mapaPanel,
                             FazendaService fazendaService, AnimalService animalService) {
        this.engine         = engine;
        this.mapaPanel      = mapaPanel;
        this.fazendaService = fazendaService;
        this.animalService  = animalService;
        this.velocidadeAtualMs = engine.getIntervaloMs();

        setOpaque(false);
        setLayout(new BorderLayout(0, 0));
        setBorder(BorderFactory.createEmptyBorder(20, 28, 20, 28));

        JPanel conteudo = new JPanel();
        conteudo.setOpaque(false);
        conteudo.setLayout(new BoxLayout(conteudo, BoxLayout.Y_AXIS));

        // ── Seção: Fazenda ────────────────────────────────────────────────────────
        conteudo.add(criarTituloSecao("🏠  FAZENDA ATIVA"));
        conteudo.add(Box.createVerticalStrut(10));

        JLabel descFaz = new JLabel(
            "Selecione a fazenda e clique em \"Trocar\" para recarregar a simulação.");
        descFaz.setFont(new Font("SansSerif", Font.PLAIN, 11));
        descFaz.setForeground(COR_TEXTO_SEC);
        descFaz.setAlignmentX(LEFT_ALIGNMENT);
        conteudo.add(descFaz);
        conteudo.add(Box.createVerticalStrut(12));

        conteudo.add(criarPainelFazenda());

        conteudo.add(Box.createVerticalStrut(24));
        conteudo.add(criarSeparador());
        conteudo.add(Box.createVerticalStrut(22));

        // ── Seção: Velocidade ─────────────────────────────────────────────────────
        conteudo.add(criarTituloSecao("⚙  VELOCIDADE DA SIMULAÇÃO"));
        conteudo.add(Box.createVerticalStrut(10));

        JLabel descVel = new JLabel(
            "Define o intervalo entre cada tick da simulação. Valores menores = simulação mais rápida.");
        descVel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        descVel.setForeground(COR_TEXTO_SEC);
        descVel.setAlignmentX(LEFT_ALIGNMENT);
        conteudo.add(descVel);
        conteudo.add(Box.createVerticalStrut(14));

        JPanel gridVel = new JPanel(new GridLayout(1, 4, 10, 0));
        gridVel.setOpaque(false);
        gridVel.setAlignmentX(LEFT_ALIGNMENT);
        gridVel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));

        for (int i = 0; i < VELOCIDADES.length; i++) {
            final int idx = i;
            botoesVel[i] = new BotaoVelocidade(
                LABELS_VEL[i], SUBLABELS[i], VELOCIDADES[i] == velocidadeAtualMs);
            botoesVel[i].addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) { selecionarVelocidade(idx); }
            });
            gridVel.add(botoesVel[i]);
        }
        conteudo.add(gridVel);
        conteudo.add(Box.createVerticalStrut(10));

        labelStatusEngine = new JLabel(getTextoStatusEngine());
        labelStatusEngine.setFont(new Font("Monospaced", Font.PLAIN, 10));
        labelStatusEngine.setForeground(COR_TEXTO_SEC);
        labelStatusEngine.setAlignmentX(LEFT_ALIGNMENT);
        conteudo.add(labelStatusEngine);

        conteudo.add(Box.createVerticalStrut(26));
        conteudo.add(criarSeparador());
        conteudo.add(Box.createVerticalStrut(22));

        // ── Seção: Elementos visuais ──────────────────────────────────────────────
        conteudo.add(criarTituloSecao("🗺  ELEMENTOS VISUAIS DO MAPA"));
        conteudo.add(Box.createVerticalStrut(10));

        JLabel descMap = new JLabel(
            "Ative ou desative elementos visuais exibidos sobre o mapa da simulação.");
        descMap.setFont(new Font("SansSerif", Font.PLAIN, 11));
        descMap.setForeground(COR_TEXTO_SEC);
        descMap.setAlignmentX(LEFT_ALIGNMENT);
        conteudo.add(descMap);
        conteudo.add(Box.createVerticalStrut(16));

        toggleNomes        = criarToggle(mapaPanel.isExibirNomes(),        v -> mapaPanel.setExibirNomes(v));
        toggleEstados      = criarToggle(mapaPanel.isExibirEstados(),      v -> mapaPanel.setExibirEstados(v));
        toggleResgatadores = criarToggle(mapaPanel.isExibirResgatadores(), v -> mapaPanel.setExibirResgatadores(v));
        toggleGrade        = criarToggle(mapaPanel.isExibirGrade(),        v -> mapaPanel.setExibirGrade(v));
        toggleMinimap      = criarToggle(true, v -> mapaPanel.putClientProperty("minimap.visivel", v));

        JPanel painelToggles = new JPanel();
        painelToggles.setOpaque(false);
        painelToggles.setLayout(new BoxLayout(painelToggles, BoxLayout.Y_AXIS));
        painelToggles.setAlignmentX(LEFT_ALIGNMENT);
        painelToggles.add(criarLinhaToggle(toggleNomes,        "Nomes dos animais",       "Exibe o nome de cada animal sobre seu ícone"));
        painelToggles.add(Box.createVerticalStrut(10));
        painelToggles.add(criarLinhaToggle(toggleEstados,      "Estado dos animais",      "Exibe o estado comportamental de cada animal"));
        painelToggles.add(Box.createVerticalStrut(10));
        painelToggles.add(criarLinhaToggle(toggleResgatadores, "Resgatadores virtuais",   "Exibe os agentes de resgate ativos no mapa"));
        painelToggles.add(Box.createVerticalStrut(10));
        painelToggles.add(criarLinhaToggle(toggleGrade,        "Grade de referência",     "Exibe uma grade de pontos sobre o mapa"));
        painelToggles.add(Box.createVerticalStrut(10));
        painelToggles.add(criarLinhaToggle(toggleMinimap,      "Minimapa lateral",        "Exibe o minimapa no painel direito"));
        conteudo.add(painelToggles);

        conteudo.add(Box.createVerticalStrut(26));
        conteudo.add(criarSeparador());
        conteudo.add(Box.createVerticalStrut(22));

        // ── Seção: Ações rápidas ──────────────────────────────────────────────────
        conteudo.add(criarTituloSecao("🔧  AÇÕES RÁPIDAS"));
        conteudo.add(Box.createVerticalStrut(14));

        JPanel acoes = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        acoes.setOpaque(false);
        acoes.setAlignmentX(LEFT_ALIGNMENT);

        JButton btnPausar = criarBotaoAcao(
            engine.isPausado() ? "▶  Retomar Simulação" : "⏸  Pausar Simulação", COR_ACENTO);
        btnPausar.addActionListener(e -> {
            if (engine.isPausado()) engine.retomar(); else engine.pausar();
            btnPausar.setText(engine.isPausado() ? "▶  Retomar Simulação" : "⏸  Pausar Simulação");
            labelStatusEngine.setText(getTextoStatusEngine());
        });

        JButton btnCentralizar = criarBotaoAcao("⌂  Centralizar Mapa", COR_VERDE);
        btnCentralizar.addActionListener(e -> mapaPanel.centralizarMapa());

        acoes.add(btnPausar);
        acoes.add(btnCentralizar);
        conteudo.add(acoes);
        conteudo.add(Box.createVerticalStrut(20));

        // ── Scroll ────────────────────────────────────────────────────────────────
        JScrollPane scroll = new JScrollPane(conteudo);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setPreferredSize(new Dimension(4, 0));
        add(scroll, BorderLayout.CENTER);
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Painel de troca de fazenda
    // ═════════════════════════════════════════════════════════════════════════════

    private JPanel criarPainelFazenda() {
        JPanel painel = new JPanel();
        painel.setOpaque(false);
        painel.setLayout(new BoxLayout(painel, BoxLayout.Y_AXIS));
        painel.setAlignmentX(LEFT_ALIGNMENT);

        // Label fazenda atual
        Fazenda atual = engine.getFazenda();
        labelFazendaAtual = new JLabel("Atual: " + (atual != null ? atual.toString() : "—"));
        labelFazendaAtual.setFont(new Font("SansSerif", Font.BOLD, 12));
        labelFazendaAtual.setForeground(COR_ACENTO);
        labelFazendaAtual.setAlignmentX(LEFT_ALIGNMENT);
        painel.add(labelFazendaAtual);
        painel.add(Box.createVerticalStrut(6));

        // Contagem de animais
        int qtd = engine.getAnimais().size();
        labelAnimaisFazenda = new JLabel(qtd + " animal(is) carregado(s)");
        labelAnimaisFazenda.setFont(new Font("SansSerif", Font.PLAIN, 11));
        labelAnimaisFazenda.setForeground(COR_TEXTO_SEC);
        labelAnimaisFazenda.setAlignmentX(LEFT_ALIGNMENT);
        painel.add(labelAnimaisFazenda);
        painel.add(Box.createVerticalStrut(12));

        // Linha: combo + botão
        JPanel linhaCombo = new JPanel(new BorderLayout(10, 0));
        linhaCombo.setOpaque(false);
        linhaCombo.setAlignmentX(LEFT_ALIGNMENT);
        linhaCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

        comboFazendas = new JComboBox<>();
        comboFazendas.setBackground(COR_BG_DARK);
        comboFazendas.setForeground(COR_TEXTO);
        comboFazendas.setFont(new Font("SansSerif", Font.PLAIN, 12));
        comboFazendas.setBorder(BorderFactory.createLineBorder(COR_BORDA));
        comboFazendas.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                    int idx, boolean sel, boolean focus) {
                super.getListCellRendererComponent(list, value, idx, sel, focus);
                setBackground(sel ? COR_BG_PANEL : COR_BG_DARK);
                setForeground(sel ? COR_ACENTO : COR_TEXTO);
                setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
                return this;
            }
        });
        carregarFazendasNoCombo();

        JButton btnTrocar = criarBotaoAcao("🔄  Trocar", COR_AMARELO);
        btnTrocar.setPreferredSize(new Dimension(120, 34));
        btnTrocar.addActionListener(e -> executarTrocaFazenda());

        linhaCombo.add(comboFazendas, BorderLayout.CENTER);
        linhaCombo.add(btnTrocar,     BorderLayout.EAST);
        painel.add(linhaCombo);

        return painel;
    }

    private void carregarFazendasNoCombo() {
        comboFazendas.removeAllItems();
        List<Fazenda> fazendas = fazendaService.listarTodas();
        for (Fazenda f : fazendas) {
            comboFazendas.addItem(f);
        }
        // Pré-seleciona a fazenda atual do engine
        Fazenda atual = engine.getFazenda();
        if (atual != null) {
            for (int i = 0; i < comboFazendas.getItemCount(); i++) {
                if (comboFazendas.getItemAt(i).getId() == atual.getId()) {
                    comboFazendas.setSelectedIndex(i);
                    break;
                }
            }
        }
    }

    private void executarTrocaFazenda() {
        Fazenda selecionada = (Fazenda) comboFazendas.getSelectedItem();
        if (selecionada == null) return;

        // Não troca se já é a mesma
        Fazenda atual = engine.getFazenda();
        if (atual != null && selecionada.getId() == atual.getId()) {
            labelFazendaAtual.setText("Atual: " + atual);
            return;
        }

        // Busca animais da nova fazenda
        final int novaId = selecionada.getId();
        List<Animal> animaisNovos = animalService.listarAtivos().stream()
            .filter(a -> a.getFazendaId() != null && a.getFazendaId() == novaId)
            .toList();

        // Troca no engine (para simulação, recarrega tudo)
        engine.trocarFazenda(selecionada, animaisNovos);

        // Atualiza labels
        labelFazendaAtual.setText("Atual: " + selecionada);
        labelAnimaisFazenda.setText(animaisNovos.size() + " animal(is) carregado(s)");

        System.out.println("[TelaConfiguracoes] Fazenda trocada para: " + selecionada.getNome()
            + " | " + animaisNovos.size() + " animais.");
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Ciclo de vida
    // ═════════════════════════════════════════════════════════════════════════════

    @Override
    public void aoExibir() {
        velocidadeAtualMs = engine.getIntervaloMs();
        atualizarBotoesVelocidade();
        labelStatusEngine.setText(getTextoStatusEngine());

        // Recarrega fazendas no combo (pode ter sido cadastrada nova no ERP)
        carregarFazendasNoCombo();

        // Atualiza contagem de animais
        labelAnimaisFazenda.setText(engine.getAnimais().size() + " animal(is) carregado(s)");

        Fazenda f = engine.getFazenda();
        if (f != null) labelFazendaAtual.setText("Atual: " + f);
    }

    @Override
    public void aoOcultar() {
        // sem timers internos
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Velocidade
    // ═════════════════════════════════════════════════════════════════════════════

    private void selecionarVelocidade(int idx) {
        velocidadeAtualMs = VELOCIDADES[idx];
        engine.setIntervaloMs(velocidadeAtualMs);
        atualizarBotoesVelocidade();
        labelStatusEngine.setText(getTextoStatusEngine());
    }

    private void atualizarBotoesVelocidade() {
        for (int i = 0; i < VELOCIDADES.length; i++) {
            botoesVel[i].setSelecionado(VELOCIDADES[i] == velocidadeAtualMs);
        }
    }

    private String getTextoStatusEngine() {
        return "Engine: " + engine.getIntervaloMs() + " ms/tick"
            + (engine.isPausado() ? "  ⏸ pausado" : "  ▶ em execução");
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Helpers de UI
    // ═════════════════════════════════════════════════════════════════════════════

    private JLabel criarTituloSecao(String texto) {
        JLabel l = new JLabel(texto);
        l.setFont(new Font("SansSerif", Font.BOLD, 12));
        l.setForeground(COR_TEXTO);
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    private JPanel criarSeparador() {
        JPanel sep = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                g.setColor(COR_BORDA);
                g.fillRect(0, 0, getWidth(), 1);
            }
        };
        sep.setOpaque(false);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        sep.setAlignmentX(LEFT_ALIGNMENT);
        return sep;
    }

    private ToggleSwitch criarToggle(boolean inicial,
                                     java.util.function.Consumer<Boolean> onChange) {
        ToggleSwitch ts = new ToggleSwitch(inicial);
        ts.setOnChange(onChange);
        return ts;
    }

    private JPanel criarLinhaToggle(ToggleSwitch toggle, String titulo, String descricao) {
        JPanel linha = new JPanel(new BorderLayout(12, 0));
        linha.setOpaque(false);
        linha.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        linha.setAlignmentX(LEFT_ALIGNMENT);

        JPanel textos = new JPanel();
        textos.setOpaque(false);
        textos.setLayout(new BoxLayout(textos, BoxLayout.Y_AXIS));

        JLabel lTitulo = new JLabel(titulo);
        lTitulo.setFont(new Font("SansSerif", Font.BOLD, 12));
        lTitulo.setForeground(COR_TEXTO);
        textos.add(lTitulo);

        JLabel lDesc = new JLabel(descricao);
        lDesc.setFont(new Font("SansSerif", Font.PLAIN, 10));
        lDesc.setForeground(COR_TEXTO_SEC);
        textos.add(lDesc);

        linha.add(textos, BorderLayout.CENTER);
        linha.add(toggle, BorderLayout.EAST);
        return linha;
    }

    private JButton criarBotaoAcao(String texto, Color cor) {
        JButton b = new JButton(texto) {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0;
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color bg = getModel().isRollover()
                    ? new Color(cor.getRed(), cor.getGreen(), cor.getBlue(), 50)
                    : new Color(cor.getRed(), cor.getGreen(), cor.getBlue(), 25);
                g.setColor(bg);
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g.setColor(new Color(cor.getRed(), cor.getGreen(), cor.getBlue(),
                    getModel().isRollover() ? 255 : 180));
                g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                super.paintComponent(g0);
            }
        };
        b.setFont(new Font("SansSerif", Font.BOLD, 11));
        b.setForeground(cor);
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setPreferredSize(new Dimension(180, 34));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Componente: BotaoVelocidade
    // ═════════════════════════════════════════════════════════════════════════════

    private static class BotaoVelocidade extends JPanel {
        private boolean selecionado;
        private final String labelPrincipal;
        private final String labelSub;

        BotaoVelocidade(String label, String sub, boolean selecionado) {
            this.labelPrincipal = label;
            this.labelSub       = sub;
            this.selecionado    = selecionado;
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(0, 70));
            setToolTipText(sub);
        }

        void setSelecionado(boolean s) { this.selecionado = s; repaint(); }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color corBorda = selecionado ? COR_ACENTO : COR_BORDA;
            Color corFundo = selecionado
                ? new Color(COR_ACENTO.getRed(), COR_ACENTO.getGreen(), COR_ACENTO.getBlue(), 25)
                : new Color(COR_BG_DARK.getRed(), COR_BG_DARK.getGreen(), COR_BG_DARK.getBlue(), 180);
            g.setColor(corFundo);
            g.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
            g.setColor(corBorda);
            if (selecionado) g.setStroke(new BasicStroke(2f));
            g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
            g.setStroke(new BasicStroke(1f));
            g.setFont(new Font("SansSerif", Font.BOLD, 12));
            g.setColor(selecionado ? COR_ACENTO : COR_TEXTO);
            FontMetrics fm = g.getFontMetrics();
            int tw = fm.stringWidth(labelPrincipal);
            g.drawString(labelPrincipal, (getWidth() - tw) / 2, getHeight() / 2 - 2);
            g.setFont(new Font("Monospaced", Font.PLAIN, 10));
            g.setColor(selecionado ? COR_ACENTO : COR_TEXTO_SEC);
            fm = g.getFontMetrics();
            tw = fm.stringWidth(labelSub);
            g.drawString(labelSub, (getWidth() - tw) / 2, getHeight() / 2 + 14);
            if (selecionado) {
                g.setColor(COR_ACENTO);
                g.fillOval(getWidth() / 2 - 3, getHeight() - 12, 6, 6);
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Componente: ToggleSwitch
    // ═════════════════════════════════════════════════════════════════════════════

    private static class ToggleSwitch extends JPanel {
        private boolean ligado;
        private java.util.function.Consumer<Boolean> onChange;
        private float thumbX;
        private Timer animTimer;
        private static final int W = 44, H = 24, RAIO_THUMB = 9;

        ToggleSwitch(boolean ligado) {
            this.ligado  = ligado;
            this.thumbX  = ligado ? W - H / 2f : H / 2f;
            setOpaque(false);
            setPreferredSize(new Dimension(W + 4, H + 4));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) { toggle(); }
            });
        }

        void setOnChange(java.util.function.Consumer<Boolean> cb) { this.onChange = cb; }

        void toggle() {
            ligado = !ligado;
            if (onChange != null) onChange.accept(ligado);
            iniciarAnimacao();
        }

        private void iniciarAnimacao() {
            if (animTimer != null) animTimer.stop();
            float alvo = ligado ? W - H / 2f : H / 2f;
            animTimer = new Timer(16, e -> {
                thumbX += (alvo - thumbX) * 0.3f;
                if (Math.abs(thumbX - alvo) < 0.5f) { thumbX = alvo; ((Timer) e.getSource()).stop(); }
                repaint();
            });
            animTimer.start();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int ox = 2, oy = (getHeight() - H) / 2;
            Color trilhoCor = ligado
                ? new Color(COR_VERDE.getRed(), COR_VERDE.getGreen(), COR_VERDE.getBlue(), 180)
                : new Color(COR_BG_DARK.getRed(), COR_BG_DARK.getGreen(), COR_BG_DARK.getBlue(), 220);
            g.setColor(trilhoCor);
            g.fillRoundRect(ox, oy, W, H, H, H);
            g.setColor(COR_BORDA);
            g.drawRoundRect(ox, oy, W - 1, H - 1, H, H);
            int tx = (int)(ox + thumbX) - RAIO_THUMB;
            int ty = oy + (H / 2) - RAIO_THUMB;
            g.setColor(new Color(30, 30, 30, 120));
            g.fillOval(tx + 1, ty + 1, RAIO_THUMB * 2, RAIO_THUMB * 2);
            g.setColor(Color.WHITE);
            g.fillOval(tx, ty, RAIO_THUMB * 2, RAIO_THUMB * 2);
        }
    }
}
