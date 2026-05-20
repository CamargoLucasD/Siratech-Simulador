package frontend;

import backend.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

import static frontend.SimuladorFrame.*;

/**
 * TelaAlertas — lista limpa de animais em estado crítico.
 * Botão "Ir até animal" fecha o overlay e centraliza o mapa.
 */
public class TelaAlertas extends JPanel implements OverlayPanel.TelaBase {

    private final SimulacaoEngine engine;
    private final Runnable onFecharOverlay;
    private final java.util.function.BiConsumer<Double, Double> onNavegar;
    private final java.util.function.Consumer<AnimalSimulado>   onSelecionarAnimal;

    private final JPanel listaCriticos;
    private final JLabel labelStatus;
    private Timer atualizadorTimer;

    public TelaAlertas(SimulacaoEngine engine,
                       Runnable onFecharOverlay,
                       java.util.function.BiConsumer<Double, Double> onNavegar,
                       java.util.function.Consumer<AnimalSimulado> onSelecionarAnimal) {
        this.engine             = engine;
        this.onFecharOverlay    = onFecharOverlay;
        this.onNavegar          = onNavegar;
        this.onSelecionarAnimal = onSelecionarAnimal;

        setOpaque(false);
        setLayout(new BorderLayout(0, 12));
        setBorder(BorderFactory.createEmptyBorder(16, 24, 20, 24));

        // ── Topo ──────────────────────────────────────────────────────────────
        JPanel topo = new JPanel(new BorderLayout());
        topo.setOpaque(false);

        JLabel sub = new JLabel("Apenas animais em situação crítica são exibidos aqui");
        sub.setFont(new Font("SansSerif", Font.PLAIN, 12));
        sub.setForeground(COR_TEXTO_SEC);
        topo.add(sub, BorderLayout.WEST);

        labelStatus = new JLabel("0 alertas");
        labelStatus.setFont(new Font("Monospaced", Font.BOLD, 12));
        labelStatus.setForeground(COR_VERMELHO);
        topo.add(labelStatus, BorderLayout.EAST);
        add(topo, BorderLayout.NORTH);

        // ── Lista ──────────────────────────────────────────────────────────────
        listaCriticos = new JPanel();
        listaCriticos.setOpaque(false);
        listaCriticos.setLayout(new BoxLayout(listaCriticos, BoxLayout.Y_AXIS));

        JScrollPane scroll = new JScrollPane(listaCriticos);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setPreferredSize(new Dimension(6, 0));
        add(scroll, BorderLayout.CENTER);

        atualizar();
    }

    @Override public void aoExibir() {
        atualizadorTimer = new Timer(1500, e -> SwingUtilities.invokeLater(this::atualizar));
        atualizadorTimer.start();
        atualizar();
    }

    @Override public void aoOcultar() {
        if (atualizadorTimer != null) atualizadorTimer.stop();
    }

    private void atualizar() {
        List<AnimalSimulado> criticos = engine.getAnimais().stream()
            .filter(a -> a.getEstado() == EstadoAnimal.FORA_DA_AREA
                      || a.getEstado() == EstadoAnimal.EM_RESGATE
                      || a.getEstado() == EstadoAnimal.RETORNANDO)
            .sorted((a, b) -> {
                int pa = a.getEstado() == EstadoAnimal.FORA_DA_AREA ? 0 : 1;
                int pb = b.getEstado() == EstadoAnimal.FORA_DA_AREA ? 0 : 1;
                return Integer.compare(pa, pb);
            })
            .toList();

        listaCriticos.removeAll();

        if (criticos.isEmpty()) {
            JPanel semAlertas = criarPainelVazio();
            listaCriticos.add(semAlertas);
            labelStatus.setText("Sem alertas");
            labelStatus.setForeground(COR_VERDE);
        } else {
            labelStatus.setText(criticos.size() + " alertas");
            labelStatus.setForeground(COR_VERMELHO);
            for (AnimalSimulado a : criticos) {
                listaCriticos.add(criarCardAlerta(a));
                listaCriticos.add(Box.createVerticalStrut(8));
            }
        }

        listaCriticos.revalidate();
        listaCriticos.repaint();
    }

    private JPanel criarPainelVazio() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setOpaque(false);
        p.setPreferredSize(new Dimension(0, 200));
        JLabel l = new JLabel("✅  Nenhum alerta ativo — todos os animais estão dentro da área");
        l.setFont(new Font("SansSerif", Font.ITALIC, 13));
        l.setForeground(COR_VERDE);
        p.add(l);
        return p;
    }

    private JPanel criarCardAlerta(AnimalSimulado a) {
        boolean foraArea  = a.getEstado() == EstadoAnimal.FORA_DA_AREA;
        boolean emResgate = a.getEstado() == EstadoAnimal.EM_RESGATE;

        Color corPrincipal = foraArea ? COR_VERMELHO : COR_AMARELO;
        Color corFundo     = foraArea ? new Color(40, 18, 18) : new Color(32, 28, 16);
        Color corBorda     = foraArea ? new Color(90, 30, 30) : new Color(80, 65, 20);

        JPanel card = new JPanel(new BorderLayout(14, 0)) {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0;
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(corFundo);
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g.setColor(corBorda);
                g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                // Linha lateral colorida
                g.setColor(corPrincipal);
                g.fillRoundRect(0, 8, 4, getHeight() - 16, 2, 2);
            }
        };
        card.setOpaque(false);
        card.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));

        // ── Esquerda: ID grande ────────────────────────────────────────────────
        JLabel labelId = new JLabel(String.format("#%03d", a.getAnimal().getId()));
        labelId.setFont(new Font("Monospaced", Font.BOLD, 24));
        labelId.setForeground(corPrincipal);
        labelId.setPreferredSize(new Dimension(72, 50));
        card.add(labelId, BorderLayout.WEST);

        // ── Centro: nome + estado + resgatador ────────────────────────────────
        JPanel info = new JPanel();
        info.setOpaque(false);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));

        String nome = a.getAnimal().getNome() != null ? a.getAnimal().getNome() : "Animal";
        JLabel labelNome = new JLabel(nome);
        labelNome.setFont(new Font("SansSerif", Font.BOLD, 13));
        labelNome.setForeground(COR_TEXTO);
        info.add(labelNome);
        info.add(Box.createVerticalStrut(3));

        String descEstado = foraArea
            ? "⚠  Fora da área delimitada"
            : emResgate
                ? "🚨  Em resgate" + (a.getResgatadorNome() != null ? " — " + a.getResgatadorNome() : "")
                : "↺  Retornando à área";
        JLabel labelEstado = new JLabel(descEstado);
        labelEstado.setFont(new Font("SansSerif", Font.PLAIN, 11));
        labelEstado.setForeground(corPrincipal);
        info.add(labelEstado);

        String posicao = String.format("📍 %.0f, %.0f", a.getX(), a.getY());
        JLabel labelPos = new JLabel(posicao);
        labelPos.setFont(new Font("Monospaced", Font.PLAIN, 10));
        labelPos.setForeground(COR_TEXTO_SEC);
        info.add(Box.createVerticalStrut(2));
        info.add(labelPos);

        card.add(info, BorderLayout.CENTER);

        // ── Direita: botão ir ──────────────────────────────────────────────────
        JButton btnIr = new JButton("↗  Ir") {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0;
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color c = getModel().isRollover() ? corPrincipal : new Color(corPrincipal.getRed(), corPrincipal.getGreen(), corPrincipal.getBlue(), 180);
                g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 30));
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g.setColor(c);
                g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                super.paintComponent(g0);
            }
        };
        btnIr.setFont(new Font("SansSerif", Font.BOLD, 11));
        btnIr.setForeground(corPrincipal);
        btnIr.setFocusPainted(false);
        btnIr.setBorderPainted(false);
        btnIr.setContentAreaFilled(false);
        btnIr.setPreferredSize(new Dimension(72, 36));
        btnIr.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnIr.addActionListener(e -> {
            onSelecionarAnimal.accept(a);
            onNavegar.accept(a.getX(), a.getY());
            onFecharOverlay.run();
        });
        card.add(btnIr, BorderLayout.EAST);

        return card;
    }
}
