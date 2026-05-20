package frontend;

import backend.*;

import javax.swing.*;
import java.awt.*;
import java.util.List;

import static frontend.SimuladorFrame.*;

/**
 * TelaMonitoramento — barras visuais dos estados dos animais em tempo real.
 */
public class TelaMonitoramento extends JPanel implements OverlayPanel.TelaBase {

    private final SimulacaoEngine engine;
    private Timer atualizadorTimer;

    private final BarraEstado[] barras;
    private final JLabel labelTotal;

    private static final Object[][] ESTADOS = {
        {EstadoAnimal.ANDANDO,      "ANDANDO",       "🚶", COR_VERDE},
        {EstadoAnimal.COMENDO,      "COMENDO",       "🍃", COR_AMARELO},
        {EstadoAnimal.BEBENDO,      "BEBENDO",       "💧", COR_AZUL},
        {EstadoAnimal.DESCANSANDO,  "DESCANSANDO",   "😴", new Color(160, 120, 220)},
        {EstadoAnimal.EM_GRUPO,     "EM GRUPO",      "🐄", new Color(80, 200, 200)},
        {EstadoAnimal.FORA_DA_AREA, "FORA DA ÁREA",  "⚠",  COR_VERMELHO},
        {EstadoAnimal.EM_RESGATE,   "EM RESGATE",    "🚨", COR_AMARELO},
        {EstadoAnimal.RETORNANDO,   "RETORNANDO",    "↺",  new Color(100, 180, 240)},
    };

    public TelaMonitoramento(SimulacaoEngine engine) {
        this.engine = engine;
        setOpaque(false);
        setLayout(new BorderLayout(0, 16));
        setBorder(BorderFactory.createEmptyBorder(20, 28, 20, 28));

        // ── Topo ──────────────────────────────────────────────────────────────
        JPanel topo = new JPanel(new BorderLayout());
        topo.setOpaque(false);

        JLabel sub = new JLabel("Distribuição de estados do rebanho");
        sub.setFont(new Font("SansSerif", Font.PLAIN, 12));
        sub.setForeground(COR_TEXTO_SEC);
        topo.add(sub, BorderLayout.WEST);

        labelTotal = new JLabel("Total: 0");
        labelTotal.setFont(new Font("Monospaced", Font.BOLD, 12));
        labelTotal.setForeground(COR_TEXTO);
        topo.add(labelTotal, BorderLayout.EAST);
        add(topo, BorderLayout.NORTH);

        // ── Barras ────────────────────────────────────────────────────────────
        JPanel painelBarras = new JPanel();
        painelBarras.setOpaque(false);
        painelBarras.setLayout(new BoxLayout(painelBarras, BoxLayout.Y_AXIS));

        barras = new BarraEstado[ESTADOS.length];
        for (int i = 0; i < ESTADOS.length; i++) {
            barras[i] = new BarraEstado(
                (String)  ESTADOS[i][1],
                (String)  ESTADOS[i][2],
                (Color)   ESTADOS[i][3]
            );
            painelBarras.add(barras[i]);
            painelBarras.add(Box.createVerticalStrut(10));
        }

        JScrollPane scroll = new JScrollPane(painelBarras);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setPreferredSize(new Dimension(4, 0));
        add(scroll, BorderLayout.CENTER);

        atualizar();
    }

    @Override public void aoExibir() {
        atualizadorTimer = new Timer(500, e -> SwingUtilities.invokeLater(this::atualizar));
        atualizadorTimer.start();
        atualizar();
    }

    @Override public void aoOcultar() {
        if (atualizadorTimer != null) atualizadorTimer.stop();
    }

    private void atualizar() {
        List<AnimalSimulado> animais = engine.getAnimais();
        int total = animais.size();
        if (total == 0) return;

        labelTotal.setText("Total: " + total);

        for (int i = 0; i < ESTADOS.length; i++) {
            EstadoAnimal estado = (EstadoAnimal) ESTADOS[i][0];
            long count = animais.stream().filter(a -> a.getEstado() == estado).count();
            barras[i].setValor((int) count, total);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Componente interno — barra de estado
    // ══════════════════════════════════════════════════════════════════════════

    private static class BarraEstado extends JPanel {
        private int valor = 0;
        private int total = 1;
        private final Color cor;
        private final JLabel labelContagem;
        private final JLabel labelPct;

        BarraEstado(String nome, String icone, Color cor) {
            this.cor = cor;
            setOpaque(false);
            setLayout(new BorderLayout(10, 0));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));

            // Esquerda: ícone + nome
            JPanel esq = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            esq.setOpaque(false);
            esq.setPreferredSize(new Dimension(160, 42));

            JLabel labelIcone = new JLabel(icone);
            labelIcone.setFont(new Font("SansSerif", Font.PLAIN, 14));
            esq.add(labelIcone);

            JLabel labelNome = new JLabel(nome);
            labelNome.setFont(new Font("SansSerif", Font.BOLD, 11));
            labelNome.setForeground(COR_TEXTO);
            esq.add(labelNome);
            add(esq, BorderLayout.WEST);

            // Centro: barra
            JPanel barraWrapper = new JPanel(new GridBagLayout());
            barraWrapper.setOpaque(false);
            JPanel barra = new JPanel() {
                @Override
                protected void paintComponent(Graphics g0) {
                    Graphics2D g = (Graphics2D) g0;
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    // Trilho
                    g.setColor(new Color(COR_BG_DARK.getRed(), COR_BG_DARK.getGreen(), COR_BG_DARK.getBlue(), 200));
                    g.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
                    // Preenchimento
                    double pct = total > 0 ? (double) valor / total : 0;
                    int fill = (int)(getWidth() * pct);
                    if (fill > 0) {
                        g.setColor(cor);
                        g.fillRoundRect(0, 0, fill, getHeight(), 6, 6);
                        // Brilho
                        g.setColor(new Color(255, 255, 255, 30));
                        g.fillRoundRect(0, 0, fill, getHeight() / 2, 6, 6);
                    }
                    // Borda
                    g.setColor(COR_BORDA);
                    g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
                }
            };
            barra.setPreferredSize(new Dimension(0, 16));
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.weightx = 1.0;
            barraWrapper.add(barra, gbc);
            add(barraWrapper, BorderLayout.CENTER);

            // Direita: valor + %
            JPanel dir = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
            dir.setOpaque(false);
            dir.setPreferredSize(new Dimension(90, 42));

            labelContagem = new JLabel("0");
            labelContagem.setFont(new Font("Monospaced", Font.BOLD, 13));
            labelContagem.setForeground(cor);

            labelPct = new JLabel("0%");
            labelPct.setFont(new Font("SansSerif", Font.PLAIN, 11));
            labelPct.setForeground(COR_TEXTO_SEC);

            dir.add(labelContagem);
            dir.add(labelPct);
            add(dir, BorderLayout.EAST);
        }

        void setValor(int v, int t) {
            this.valor = v;
            this.total = t;
            labelContagem.setText(String.valueOf(v));
            labelPct.setText(t > 0 ? String.format("%.0f%%", v * 100.0 / t) : "0%");
            repaint();
        }
    }
}
