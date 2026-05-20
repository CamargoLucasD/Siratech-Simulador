package frontend;

import backend.*;

import javax.swing.*;
import java.awt.*;
import java.util.List;

import static frontend.SimuladorFrame.*;

/**
 * TelaRelatorios — médias de fome, sede e energia do rebanho
 * com barras de progresso estilizadas, e total de ticks rodados.
 */
public class TelaRelatorios extends JPanel implements OverlayPanel.TelaBase {

    private final SimulacaoEngine engine;
    private Timer atualizadorTimer;

    // Barras de médias vitais
    private final BarraMetrica barraFome;
    private final BarraMetrica barraSede;
    private final BarraMetrica barraEnergia;

    // Cards de resumo
    private final ResumoCard cardTicks;
    private final ResumoCard cardTotal;
    private final ResumoCard cardMediaSaude;
    private final ResumoCard cardForaArea;

    // Labels de atualização
    private final JLabel labelUltimaAtualizacao;

    public TelaRelatorios(SimulacaoEngine engine) {
        this.engine = engine;
        setOpaque(false);
        setLayout(new BorderLayout(0, 16));
        setBorder(BorderFactory.createEmptyBorder(20, 28, 20, 28));

        // ── Topo ──────────────────────────────────────────────────────────────
        JPanel topo = new JPanel(new BorderLayout());
        topo.setOpaque(false);

        JLabel sub = new JLabel("Médias e estatísticas do rebanho na simulação atual");
        sub.setFont(new Font("SansSerif", Font.PLAIN, 12));
        sub.setForeground(COR_TEXTO_SEC);
        topo.add(sub, BorderLayout.WEST);

        labelUltimaAtualizacao = new JLabel("Atualizando...");
        labelUltimaAtualizacao.setFont(new Font("Monospaced", Font.PLAIN, 10));
        labelUltimaAtualizacao.setForeground(COR_TEXTO_SEC);
        topo.add(labelUltimaAtualizacao, BorderLayout.EAST);
        add(topo, BorderLayout.NORTH);

        // ── Painel central: barras + cards ────────────────────────────────────
        JPanel centro = new JPanel(new GridLayout(1, 2, 20, 0));
        centro.setOpaque(false);

        // ── Coluna esquerda: barras de saúde ──────────────────────────────────
        JPanel colunaBarras = new JPanel();
        colunaBarras.setOpaque(false);
        colunaBarras.setLayout(new BoxLayout(colunaBarras, BoxLayout.Y_AXIS));

        JLabel tituloBarras = new JLabel("ÍNDICES VITAIS MÉDIOS DO REBANHO");
        tituloBarras.setFont(new Font("SansSerif", Font.BOLD, 11));
        tituloBarras.setForeground(COR_TEXTO_SEC);
        tituloBarras.setAlignmentX(LEFT_ALIGNMENT);
        tituloBarras.setBorder(BorderFactory.createEmptyBorder(0, 0, 14, 0));
        colunaBarras.add(tituloBarras);

        barraFome   = new BarraMetrica("FOME MÉDIA",    "🍃", COR_AMARELO,
                "Média do nível de fome de todos os animais (0 = saciado, 100 = faminto)");
        barraSede   = new BarraMetrica("SEDE MÉDIA",    "💧", COR_AZUL,
                "Média do nível de sede de todos os animais (0 = saciado, 100 = com sede)");
        barraEnergia= new BarraMetrica("ENERGIA MÉDIA", "⚡", COR_VERDE,
                "Média de energia dos animais (100 = descansado, 0 = exausto)");

        colunaBarras.add(barraFome);
        colunaBarras.add(Box.createVerticalStrut(18));
        colunaBarras.add(barraSede);
        colunaBarras.add(Box.createVerticalStrut(18));
        colunaBarras.add(barraEnergia);
        colunaBarras.add(Box.createVerticalGlue());

        centro.add(colunaBarras);

        // ── Coluna direita: cards de resumo ───────────────────────────────────
        JPanel colunaCards = new JPanel();
        colunaCards.setOpaque(false);
        colunaCards.setLayout(new BoxLayout(colunaCards, BoxLayout.Y_AXIS));

        JLabel tituloCards = new JLabel("ESTATÍSTICAS DA SESSÃO");
        tituloCards.setFont(new Font("SansSerif", Font.BOLD, 11));
        tituloCards.setForeground(COR_TEXTO_SEC);
        tituloCards.setAlignmentX(LEFT_ALIGNMENT);
        tituloCards.setBorder(BorderFactory.createEmptyBorder(0, 0, 14, 0));
        colunaCards.add(tituloCards);

        cardTicks      = new ResumoCard("TICKS EXECUTADOS",   "0",      COR_ACENTO,
                "Total de ciclos de simulação processados desde o início");
        cardTotal      = new ResumoCard("ANIMAIS NO REBANHO", "0",      COR_VERDE,
                "Total de animais carregados na simulação");
        cardMediaSaude = new ResumoCard("SAÚDE GERAL",        "—",      COR_VERDE,
                "Índice composto: média de energia menos média de fome e sede");
        cardForaArea   = new ResumoCard("SAÍDAS DE ÁREA",     "0",      COR_VERMELHO,
                "Animais atualmente fora da área delimitada");

        colunaCards.add(cardTicks);
        colunaCards.add(Box.createVerticalStrut(10));
        colunaCards.add(cardTotal);
        colunaCards.add(Box.createVerticalStrut(10));
        colunaCards.add(cardMediaSaude);
        colunaCards.add(Box.createVerticalStrut(10));
        colunaCards.add(cardForaArea);
        colunaCards.add(Box.createVerticalGlue());

        centro.add(colunaCards);
        add(centro, BorderLayout.CENTER);

        // ── Rodapé informativo ────────────────────────────────────────────────
        JPanel rodape = new JPanel(new BorderLayout());
        rodape.setOpaque(false);
        rodape.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, COR_BORDA),
            BorderFactory.createEmptyBorder(10, 0, 0, 0)
        ));

        JLabel aviso = new JLabel(
            "ℹ  Os dados refletem o estado atual da simulação — valores calculados em tempo real a cada tick.");
        aviso.setFont(new Font("SansSerif", Font.ITALIC, 10));
        aviso.setForeground(COR_TEXTO_SEC);
        rodape.add(aviso, BorderLayout.WEST);

        add(rodape, BorderLayout.SOUTH);

        atualizar();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Ciclo de vida
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    public void aoExibir() {
        atualizadorTimer = new Timer(800, e -> SwingUtilities.invokeLater(this::atualizar));
        atualizadorTimer.start();
        atualizar();
    }

    @Override
    public void aoOcultar() {
        if (atualizadorTimer != null) atualizadorTimer.stop();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Dados
    // ══════════════════════════════════════════════════════════════════════════

    private void atualizar() {
        List<AnimalSimulado> animais = engine.getAnimais();
        int total = animais.size();

        if (total == 0) {
            barraFome.setValor(0, "Sem dados");
            barraSede.setValor(0, "Sem dados");
            barraEnergia.setValor(0, "Sem dados");
            cardTicks.setValor(String.valueOf(engine.getTick()));
            cardTotal.setValor("0");
            cardMediaSaude.setValor("—");
            cardForaArea.setValor("0");
            labelUltimaAtualizacao.setText("Tick " + engine.getTick());
            return;
        }

        double mediaFome    = animais.stream().mapToDouble(AnimalSimulado::getFome).average().orElse(0);
        double mediaSede    = animais.stream().mapToDouble(AnimalSimulado::getSede).average().orElse(0);
        double mediaEnergia = animais.stream().mapToDouble(AnimalSimulado::getEnergia).average().orElse(0);

        long foraArea = animais.stream()
            .filter(a -> a.getEstado() == EstadoAnimal.FORA_DA_AREA
                      || a.getEstado() == EstadoAnimal.EM_RESGATE
                      || a.getEstado() == EstadoAnimal.RETORNANDO)
            .count();

        // Índice de saúde composta: energia alta é bom, fome/sede alta é ruim
        double saudeGeral = mediaEnergia - ((mediaFome + mediaSede) / 2.0);
        // Normaliza para 0-100
        double saudeNorm  = Math.max(0, Math.min(100, (saudeGeral + 100) / 2.0));

        barraFome.setValor((int) Math.round(mediaFome),
            String.format("%.1f%%  —  %s",
                mediaFome,
                mediaFome < 30 ? "Rebanho saciado" : mediaFome < 60 ? "Nível moderado" : "Rebanho com fome"));

        barraSede.setValor((int) Math.round(mediaSede),
            String.format("%.1f%%  —  %s",
                mediaSede,
                mediaSede < 30 ? "Rebanho hidratado" : mediaSede < 60 ? "Nível moderado" : "Rebanho com sede"));

        barraEnergia.setValor((int) Math.round(mediaEnergia),
            String.format("%.1f%%  —  %s",
                mediaEnergia,
                mediaEnergia > 70 ? "Rebanho descansado" : mediaEnergia > 40 ? "Nível moderado" : "Rebanho cansado"));

        cardTicks.setValor(String.format("%,d", engine.getTick()));
        cardTotal.setValor(String.valueOf(total));

        // Cor dinâmica da saúde geral
        Color corSaude = saudeNorm > 65 ? COR_VERDE : saudeNorm > 35 ? COR_AMARELO : COR_VERMELHO;
        cardMediaSaude.setValorComCor(String.format("%.0f%%", saudeNorm), corSaude);

        // Cor dinâmica de fora da área
        Color corFora = foraArea == 0 ? COR_VERDE : COR_VERMELHO;
        cardForaArea.setValorComCor(String.valueOf(foraArea), corFora);

        labelUltimaAtualizacao.setText("Tick " + engine.getTick()
            + (engine.isPausado() ? "  ⏸ pausado" : "  ▶ rodando"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Componente: barra de métrica estilizada
    // ══════════════════════════════════════════════════════════════════════════

    private static class BarraMetrica extends JPanel {

        private int valor = 0;
        private final Color cor;
        private final JLabel labelPct;
        private final JLabel labelDesc;
        private final JPanel barraFill;

        BarraMetrica(String titulo, String icone, Color cor, String tooltip) {
            this.cor = cor;
            setOpaque(false);
            setLayout(new BorderLayout(0, 8));
            setAlignmentX(LEFT_ALIGNMENT);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 72));
            setToolTipText(tooltip);

            // ── Linha superior: ícone + título + valor ─────────────────────
            JPanel linha = new JPanel(new BorderLayout(6, 0));
            linha.setOpaque(false);

            JPanel esq = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
            esq.setOpaque(false);

            JLabel labelIcone = new JLabel(icone);
            labelIcone.setFont(new Font("SansSerif", Font.PLAIN, 13));
            esq.add(labelIcone);

            JLabel labelTitulo = new JLabel(titulo);
            labelTitulo.setFont(new Font("SansSerif", Font.BOLD, 11));
            labelTitulo.setForeground(COR_TEXTO_SEC);
            esq.add(labelTitulo);

            linha.add(esq, BorderLayout.WEST);

            labelPct = new JLabel("0%");
            labelPct.setFont(new Font("Monospaced", Font.BOLD, 16));
            labelPct.setForeground(cor);
            linha.add(labelPct, BorderLayout.EAST);
            add(linha, BorderLayout.NORTH);

            // ── Barra de progresso ─────────────────────────────────────────
            JPanel trilho = new JPanel(null) {
                @Override
                protected void paintComponent(Graphics g0) {
                    Graphics2D g = (Graphics2D) g0;
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    // Trilho escuro
                    g.setColor(new Color(COR_BG_DARK.getRed(), COR_BG_DARK.getGreen(), COR_BG_DARK.getBlue(), 220));
                    g.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                    g.setColor(COR_BORDA);
                    g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                }
            };
            trilho.setOpaque(false);
            trilho.setPreferredSize(new Dimension(0, 12));

            barraFill = new JPanel() {
                @Override
                protected void paintComponent(Graphics g0) {
                    Graphics2D g = (Graphics2D) g0;
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    // Gradiente na barra
                    GradientPaint gp = new GradientPaint(
                        0, 0, cor.brighter(),
                        getWidth(), 0, cor.darker()
                    );
                    g.setPaint(gp);
                    g.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                    // Brilho superior
                    g.setColor(new Color(255, 255, 255, 40));
                    g.fillRoundRect(0, 0, getWidth(), getHeight() / 2, 8, 8);
                }
            };
            barraFill.setOpaque(false);
            trilho.add(barraFill);
            add(trilho, BorderLayout.CENTER);

            // ── Descrição ─────────────────────────────────────────────────
            labelDesc = new JLabel(" ");
            labelDesc.setFont(new Font("SansSerif", Font.PLAIN, 10));
            labelDesc.setForeground(COR_TEXTO_SEC);
            add(labelDesc, BorderLayout.SOUTH);

            // Listener para ajustar largura da barra ao tamanho do trilho
            trilho.addComponentListener(new java.awt.event.ComponentAdapter() {
                @Override
                public void componentResized(java.awt.event.ComponentEvent e) {
                    atualizarLarguraBarra(trilho);
                }
            });
        }

        void setValor(int v, String descricao) {
            this.valor = Math.max(0, Math.min(100, v));
            labelPct.setText(this.valor + "%");
            labelDesc.setText(descricao);

            JPanel trilho = (JPanel) barraFill.getParent();
            if (trilho != null) atualizarLarguraBarra(trilho);
            repaint();
        }

        private void atualizarLarguraBarra(JPanel trilho) {
            int w = trilho.getWidth();
            int h = trilho.getHeight();
            if (w <= 0 || h <= 0) return;
            int fill = Math.max(0, (int)(w * (valor / 100.0)));
            barraFill.setBounds(0, 0, fill, h);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Componente: card de resumo compacto
    // ══════════════════════════════════════════════════════════════════════════

    private static class ResumoCard extends JPanel {

        private final JLabel labelValor;
        private final JLabel labelDesc;

        ResumoCard(String titulo, String valorInicial, Color corInicial, String descricao) {
            setOpaque(false);
            setLayout(new BorderLayout(0, 0));
            setAlignmentX(LEFT_ALIGNMENT);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 68));

            JPanel corpo = new JPanel() {
                @Override
                protected void paintComponent(Graphics g0) {
                    Graphics2D g = (Graphics2D) g0;
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setColor(new Color(
                        COR_BG_DARK.getRed(),
                        COR_BG_DARK.getGreen(),
                        COR_BG_DARK.getBlue(), 180));
                    g.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                    g.setColor(COR_BORDA);
                    g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                }
            };
            corpo.setOpaque(false);
            corpo.setLayout(new BorderLayout(12, 0));
            corpo.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));

            // Esquerda: título + descrição
            JPanel textos = new JPanel();
            textos.setOpaque(false);
            textos.setLayout(new BoxLayout(textos, BoxLayout.Y_AXIS));

            JLabel labelTitulo = new JLabel(titulo);
            labelTitulo.setFont(new Font("SansSerif", Font.BOLD, 10));
            labelTitulo.setForeground(COR_TEXTO_SEC);
            textos.add(labelTitulo);
            textos.add(Box.createVerticalStrut(3));

            labelDesc = new JLabel(descricao);
            labelDesc.setFont(new Font("SansSerif", Font.PLAIN, 9));
            labelDesc.setForeground(new Color(
                COR_TEXTO_SEC.getRed(),
                COR_TEXTO_SEC.getGreen(),
                COR_TEXTO_SEC.getBlue(), 160));
            textos.add(labelDesc);

            corpo.add(textos, BorderLayout.CENTER);

            // Direita: valor grande
            labelValor = new JLabel(valorInicial);
            labelValor.setFont(new Font("Monospaced", Font.BOLD, 22));
            labelValor.setForeground(corInicial);
            labelValor.setHorizontalAlignment(SwingConstants.RIGHT);
            corpo.add(labelValor, BorderLayout.EAST);

            add(corpo, BorderLayout.CENTER);
        }

        void setValor(String v) {
            labelValor.setText(v);
        }

        void setValorComCor(String v, Color cor) {
            labelValor.setText(v);
            labelValor.setForeground(cor);
        }
    }
}
