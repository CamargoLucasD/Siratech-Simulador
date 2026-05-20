package frontend;

import backend.*;

import javax.swing.*;
import java.awt.*;
import java.util.List;

import static frontend.SimuladorFrame.*;

/**
 * TelaDashboard — visão geral do rebanho com cards de métricas.
 * Atualiza a cada segundo enquanto o overlay estiver aberto.
 */
public class TelaDashboard extends JPanel implements OverlayPanel.TelaBase {

    private final SimulacaoEngine engine;

    // Cards dinâmicos
    private final MetricaCard cardTotal;
    private final MetricaCard cardDentro;
    private final MetricaCard cardFora;
    private final MetricaCard cardResgate;
    private final MetricaCard cardTick;
    private final MetricaCard cardResgatadores;

    private final JLabel labelStatus;
    private Timer atualizadorTimer;

    public TelaDashboard(SimulacaoEngine engine) {
        this.engine = engine;
        setOpaque(false);
        setLayout(new BorderLayout(0, 16));
        setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));

        // ── Subtítulo ──────────────────────────────────────────────────────────
        JLabel sub = new JLabel("Visão geral da simulação em tempo real");
        sub.setFont(new Font("SansSerif", Font.PLAIN, 12));
        sub.setForeground(COR_TEXTO_SEC);
        sub.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        add(sub, BorderLayout.NORTH);

        // ── Grid de cards ──────────────────────────────────────────────────────
        JPanel grid = new JPanel(new GridLayout(2, 3, 14, 14));
        grid.setOpaque(false);

        cardTotal       = new MetricaCard("TOTAL DE ANIMAIS", "0", "no rebanho",         COR_ACENTO);
        cardDentro      = new MetricaCard("DENTRO DA ÁREA",   "0", "seguros",             COR_VERDE);
        cardFora        = new MetricaCard("FORA DA ÁREA",     "0", "em alerta",           COR_VERMELHO);
        cardResgate     = new MetricaCard("EM RESGATE",       "0", "sendo atendidos",     COR_AMARELO);
        cardTick        = new MetricaCard("TICK ATUAL",       "0", "ciclos executados",   COR_AZUL);
        cardResgatadores= new MetricaCard("RESGATADORES",     "0", "ativos no campo",     new Color(160, 100, 220));

        grid.add(cardTotal);
        grid.add(cardDentro);
        grid.add(cardFora);
        grid.add(cardResgate);
        grid.add(cardTick);
        grid.add(cardResgatadores);
        add(grid, BorderLayout.CENTER);

        // ── Status bar ─────────────────────────────────────────────────────────
        labelStatus = new JLabel("● Simulação pausada");
        labelStatus.setFont(new Font("Monospaced", Font.PLAIN, 11));
        labelStatus.setForeground(COR_TEXTO_SEC);
        add(labelStatus, BorderLayout.SOUTH);

        atualizar();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Ciclo de vida
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    public void aoExibir() {
        atualizadorTimer = new Timer(1000, e -> SwingUtilities.invokeLater(this::atualizar));
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
        long total    = animais.size();
        long fora     = animais.stream().filter(a -> a.getEstado() == EstadoAnimal.FORA_DA_AREA).count();
        long resgate  = animais.stream().filter(a ->
            a.getEstado() == EstadoAnimal.EM_RESGATE ||
            a.getEstado() == EstadoAnimal.RETORNANDO).count();
        long dentro   = total - fora - resgate;
        long resgats  = engine.getResgatadores().size();

        cardTotal.setValor(String.valueOf(total));
        cardDentro.setValor(String.valueOf(Math.max(0, dentro)));
        cardFora.setValor(String.valueOf(fora));
        cardResgate.setValor(String.valueOf(resgate));
        cardTick.setValor(String.valueOf(engine.getTick()));
        cardResgatadores.setValor(String.valueOf(resgats));

        // Pisca vermelho no card fora da área se houver alertas
        cardFora.setDestaque(fora > 0);

        String statusTxt = engine.isPausado()
            ? "● Simulação pausada"
            : "● Simulação em execução — tick " + engine.getTick();
        Color statusCor = engine.isPausado() ? COR_TEXTO_SEC : COR_VERDE;
        labelStatus.setText(statusTxt);
        labelStatus.setForeground(statusCor);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Componente interno — card de métrica
    // ══════════════════════════════════════════════════════════════════════════

    private static class MetricaCard extends JPanel {
        private final JLabel labelValor;
        private final Color corAcento;
        private boolean destaque = false;

        MetricaCard(String titulo, String valor, String descricao, Color cor) {
            this.corAcento = cor;
            setOpaque(false);
            setLayout(new BorderLayout(0, 6));

            // Topo: linha colorida
            JPanel topo = new JPanel() {
                @Override
                protected void paintComponent(Graphics g0) {
                    Graphics2D g = (Graphics2D) g0;
                    g.setColor(destaque
                        ? new Color(220, 60, 60, (int)(180 * (0.6 + 0.4 * Math.sin(System.currentTimeMillis() / 300.0))))
                        : corAcento);
                    g.fillRoundRect(0, 0, getWidth(), getHeight(), 2, 2);
                }
            };
            topo.setPreferredSize(new Dimension(0, 3));
            topo.setOpaque(false);

            // Corpo
            JPanel corpo = new JPanel() {
                @Override
                protected void paintComponent(Graphics g0) {
                    Graphics2D g = (Graphics2D) g0;
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setColor(new Color(COR_BG_DARK.getRed(), COR_BG_DARK.getGreen(), COR_BG_DARK.getBlue(), 180));
                    g.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                    g.setColor(COR_BORDA);
                    g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                }
            };
            corpo.setOpaque(false);
            corpo.setLayout(new GridBagLayout());

            JPanel textos = new JPanel();
            textos.setOpaque(false);
            textos.setLayout(new BoxLayout(textos, BoxLayout.Y_AXIS));
            textos.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));

            JLabel labelTitulo = new JLabel(titulo);
            labelTitulo.setFont(new Font("SansSerif", Font.BOLD, 10));
            labelTitulo.setForeground(COR_TEXTO_SEC);
            textos.add(labelTitulo);
            textos.add(Box.createVerticalStrut(8));

            labelValor = new JLabel(valor);
            labelValor.setFont(new Font("SansSerif", Font.BOLD, 36));
            labelValor.setForeground(cor);
            textos.add(labelValor);

            JLabel labelDesc = new JLabel(descricao);
            labelDesc.setFont(new Font("SansSerif", Font.PLAIN, 11));
            labelDesc.setForeground(COR_TEXTO_SEC);
            textos.add(Box.createVerticalStrut(4));
            textos.add(labelDesc);

            corpo.add(textos);

            add(topo, BorderLayout.NORTH);
            add(corpo, BorderLayout.CENTER);
        }

        void setValor(String v) {
            labelValor.setText(v);
        }

        void setDestaque(boolean d) {
            this.destaque = d;
            repaint();
        }
    }
}
