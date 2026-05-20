package frontend;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

import static frontend.SimuladorFrame.*;

/**
 * OverlayPanel — painel de overlay semitransparente sobre o MapaPanel.
 *
 * Uso:
 *   overlayPanel.mostrar(tela, "Título");
 *   overlayPanel.fechar();
 *
 * Estrutura interna:
 *   - this (JLayeredPane, tamanho igual ao MapaPanel pai)
 *     ├── painelEscuro (fundo semitransparente, cobre tudo)
 *     └── painelConteudo (card central 75%x80%, fundo sólido)
 *           ├── header (título + botão X)
 *           └── conteudo (a tela passada)
 */
public class OverlayPanel extends JPanel {

    private static final int FADE_DURACAO_MS = 200;
    private static final int FADE_STEPS      = 20;

    private float   alpha         = 0f;
    private boolean visivel       = false;
    private Timer   fadeTimer;
    private boolean fadindoEntrada;

    private final JPanel painelConteudo;
    private final JLabel labelTitulo;
    private JPanel telaAtual;

    // Callback para fechar — o SimuladorFrame pode escutar para restaurar estado do menu
    private Runnable onFechar;

    public OverlayPanel() {
        setLayout(null); // posicionamento absoluto
        setOpaque(false);

        // ── Fundo escuro clicável (fechar ao clicar fora) ──────────────────────
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                // Só fecha se clicou fora do painelConteudo
                Point p = SwingUtilities.convertPoint(OverlayPanel.this, e.getPoint(), painelConteudo);
                if (!painelConteudo.contains(p)) fechar();
            }
        });

        // ── Painel de conteúdo (card central) ─────────────────────────────────
        painelConteudo = new JPanel(new BorderLayout(0, 0)) {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0;
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(COR_BG_CARD);
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
            }
            @Override
            protected void paintBorder(Graphics g0) {
                Graphics2D g = (Graphics2D) g0;
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(COR_BORDA);
                g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
            }
        };
        painelConteudo.setOpaque(false);

        // ── Header do card ─────────────────────────────────────────────────────
        JPanel header = new JPanel(new BorderLayout(0, 0));
        header.setOpaque(false);
        header.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, COR_BORDA),
            BorderFactory.createEmptyBorder(14, 20, 14, 16)
        ));

        labelTitulo = new JLabel("—");
        labelTitulo.setFont(new Font("SansSerif", Font.BOLD, 15));
        labelTitulo.setForeground(COR_TEXTO);
        header.add(labelTitulo, BorderLayout.WEST);

        JButton btnFechar = new JButton("✕") {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0;
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (getModel().isRollover()) {
                    g.setColor(new Color(COR_VERMELHO.getRed(), COR_VERMELHO.getGreen(), COR_VERMELHO.getBlue(), 40));
                    g.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
                }
                super.paintComponent(g0);
            }
        };
        btnFechar.setFont(new Font("SansSerif", Font.PLAIN, 14));
        btnFechar.setForeground(COR_TEXTO_SEC);
        btnFechar.setFocusPainted(false);
        btnFechar.setBorderPainted(false);
        btnFechar.setContentAreaFilled(false);
        btnFechar.setPreferredSize(new Dimension(32, 32));
        btnFechar.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnFechar.addActionListener(e -> fechar());
        btnFechar.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { btnFechar.setForeground(COR_VERMELHO); }
            public void mouseExited(MouseEvent e)  { btnFechar.setForeground(COR_TEXTO_SEC); }
        });
        header.add(btnFechar, BorderLayout.EAST);

        painelConteudo.add(header, BorderLayout.NORTH);
        add(painelConteudo);

        setVisible(false);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // API pública
    // ══════════════════════════════════════════════════════════════════════════

    public void setOnFechar(Runnable cb) {
        this.onFechar = cb;
    }

    /** Exibe o overlay com a tela fornecida e inicia o fade de entrada. */
    public void mostrar(JPanel tela, String titulo) {
        // Troca a tela de conteúdo
        if (telaAtual != null) painelConteudo.remove(telaAtual);
        telaAtual = tela;
        painelConteudo.add(tela, BorderLayout.CENTER);
        labelTitulo.setText(titulo);

        posicionarCard();
        setVisible(true);
        visivel = true;
        fadindoEntrada = true;
        iniciarFade();

        // Notifica a tela que está sendo exibida (para iniciar timers internos)
        if (tela instanceof TelaBase tb) tb.aoExibir();
    }

    /** Inicia o fade de saída e depois oculta. */
    public void fechar() {
        if (!visivel) return;
        fadindoEntrada = false;

        // Notifica a tela que vai ser ocultada (para parar timers internos)
        if (telaAtual instanceof TelaBase tb) tb.aoOcultar();

        iniciarFade();
        if (onFechar != null) onFechar.run();
    }

    public boolean isVisivel() { return visivel; }

    // ══════════════════════════════════════════════════════════════════════════
    // Layout e posicionamento
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    public void doLayout() {
        super.doLayout();
        posicionarCard();
    }

    private void posicionarCard() {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        int cw = (int)(w * 0.80);
        int ch = (int)(h * 0.85);
        int cx = (w - cw) / 2;
        int cy = (h - ch) / 2;
        painelConteudo.setBounds(cx, cy, cw, ch);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Fade
    // ══════════════════════════════════════════════════════════════════════════

    private void iniciarFade() {
        if (fadeTimer != null && fadeTimer.isRunning()) fadeTimer.stop();
        float step = 1f / FADE_STEPS;
        fadeTimer = new Timer(FADE_DURACAO_MS / FADE_STEPS, e -> {
            if (fadindoEntrada) {
                alpha = Math.min(1f, alpha + step);
                if (alpha >= 1f) ((Timer) e.getSource()).stop();
            } else {
                alpha = Math.max(0f, alpha - step);
                if (alpha <= 0f) {
                    ((Timer) e.getSource()).stop();
                    setVisible(false);
                    visivel = false;
                }
            }
            repaint();
        });
        fadeTimer.start();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Pintura — fundo semitransparente + delega alpha ao card
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // Fundo escuro com alpha animado (máx 60% opacidade)
        g.setColor(new Color(0, 0, 0, (int)(160 * alpha)));
        g.fillRect(0, 0, getWidth(), getHeight());
    }

    @Override
    protected void paintChildren(Graphics g0) {
        Graphics2D g = (Graphics2D) g0;
        // Aplica alpha ao card inteiro
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        super.paintChildren(g);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Interface que as telas podem implementar para receber eventos de ciclo
    // ══════════════════════════════════════════════════════════════════════════

    public interface TelaBase {
        /** Chamado quando o overlay torna a tela visível — inicie timers aqui. */
        void aoExibir();
        /** Chamado quando o overlay fecha — pare timers aqui. */
        void aoOcultar();
    }
}
