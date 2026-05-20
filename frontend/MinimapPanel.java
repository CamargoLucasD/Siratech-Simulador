package frontend;

import backend.AnimalSimulado;
import backend.EstadoAnimal;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

import static frontend.SimuladorFrame.*;

/**
 * MinimapPanel — minimapa no canto inferior direito.
 *
 * Mostra uma versão reduzida do mapa com:
 *  - Fundo estilo thumbnail do terreno
 *  - Pontos coloridos por estado dos animais
 *  - Água, alimentação, curral, limites
 *  - Câmera atual (retângulo branco)
 *  - Legenda compacta
 */
public class MinimapPanel extends JPanel {

    private static final int MINI_W = 240;
    private static final int MINI_H = 160;
    private static final float ESCALA_X = (float) MINI_W / MapaPanel.MAP_W;
    private static final float ESCALA_Y = (float) MINI_H / MapaPanel.MAP_H;

    private List<AnimalSimulado> animais = new ArrayList<>();
    private final MapaPanel mapaPanel;
    private java.util.function.BiConsumer<Double, Double> onNavegar;

    // Thumbnail do mapa (desenhado uma vez)
    private Image thumbnail;

    public MinimapPanel(MapaPanel mapa) {
        this.mapaPanel = mapa;
        setBackground(COR_BG_CARD);
        setPreferredSize(new Dimension(260, MINI_H + 60 + 10));
        setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, COR_BORDA));
        thumbnail = criarThumbnail();
    }

    public void setAnimais(List<AnimalSimulado> animais) {
        this.animais = animais;
        repaint();
    }

    public void setOnNavigar(java.util.function.BiConsumer<Double, Double> cb) {
        this.onNavegar = cb;
        configurarClique();
    }

    private boolean cliqueConfigurado = false;
    private void configurarClique() {
        if (cliqueConfigurado) return;
        cliqueConfigurado = true;
        addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (onNavegar == null) return;
                int panelW = getWidth();
                int offsetX = (panelW - MINI_W) / 2;
                int offsetY = 8 + 16; // título + margem
                int mx = e.getX() - offsetX;
                int my = e.getY() - offsetY;
                if (mx < 0 || my < 0 || mx > MINI_W || my > MINI_H) return;
                double mapX = mx / ESCALA_X;
                double mapY = my / ESCALA_Y;
                onNavegar.accept(mapX, mapY);
            }
        });
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Thumbnail estático do mapa
    // ══════════════════════════════════════════════════════════════════════════
    private Image criarThumbnail() {
        java.awt.image.BufferedImage img =
            new java.awt.image.BufferedImage(MINI_W, MINI_H,
                java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Gramado base
        g.setColor(new Color(60, 130, 55));
        g.fillRect(0, 0, MINI_W, MINI_H);

        // Variação de tom no gramado
        java.util.Random r = new java.util.Random(3L);
        for (int i = 0; i < 200; i++) {
            int tx = r.nextInt(MINI_W), ty = r.nextInt(MINI_H);
            g.setColor(new Color(50 + r.nextInt(25), 115 + r.nextInt(30), 45 + r.nextInt(20), 80));
            g.fillRect(tx, ty, 4, 3);
        }

        // Caminhos
        g.setColor(new Color(145, 108, 60, 200));
        g.fillRect(0, mapY(480), MINI_W, 4);           // horizontal
        g.fillRect(mapX(640), 0, 4, mapY(520));        // vertical

        // Lago
        g.setColor(new Color(55, 125, 190));
        g.fillOval(mapX(240), mapY(570), mapX(180) - mapX(0), mapY(100) - mapY(0));

        // Bebedouros
        g.setColor(new Color(65, 150, 200));
        g.fillOval(mapX(890), mapY(668), 6, 4);
        g.fillOval(mapX(190), mapY(910), 5, 4);

        // Área de alimentação
        g.setColor(new Color(195, 155, 45));
        g.fillRect(mapX(780), mapY(760), 10, 5);
        g.fillRect(mapX(400), mapY(900), 8, 5);

        // Curral
        g.setColor(new Color(130, 100, 50));
        g.fillRect(mapX(560), mapY(120), mapX(260) - mapX(0), mapY(200) - mapY(0));

        // Árvores — manchas escuras
        int[][] arvores = {
            {40,40},{110,20},{190,50},{1460,40},{1530,80},
            {40,1080},{120,1100},{1480,1060},{1540,1100},
            {340,140},{440,90},{1100,140},{1200,80}
        };
        g.setColor(new Color(35, 95, 35));
        for (int[] ap : arvores) {
            g.fillOval(mapX(ap[0]) - 3, mapY(ap[1]) - 3, 7, 7);
        }

        // Limite da área
        int mx = mapX(200), my = mapY(200);
        int mw = mapX(1400) - mapX(200), mh = mapY(1000) - mapY(200);
        g.setColor(new Color(220, 80, 60, 100));
        Stroke s = g.getStroke();
        g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                0, new float[]{4, 3}, 0));
        g.drawRoundRect(mx, my, mw, mh, 4, 4);
        g.setStroke(s);

        g.dispose();
        return img;
    }

    private int mapX(int x) { return Math.round(x * ESCALA_X); }
    private int mapY(int y) { return Math.round(y * ESCALA_Y); }

    // ══════════════════════════════════════════════════════════════════════════
    // paintComponent
    // ══════════════════════════════════════════════════════════════════════════
    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int panelW = getWidth();
        int offsetX = (panelW - MINI_W) / 2;
        int offsetY = 8;

        // ── Título ─────────────────────────────────────────────────────────────
        g.setFont(new Font("SansSerif", Font.BOLD, 10));
        g.setColor(COR_TEXTO_SEC);
        g.drawString("MINIMAPA", offsetX, offsetY + 12);

        // Botão fechar (apenas visual)
        g.setColor(COR_TEXTO_SEC);
        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g.drawString("×", offsetX + MINI_W - 12, offsetY + 12);

        offsetY += 16;

        // ── Fundo e thumbnail ──────────────────────────────────────────────────
        g.setColor(new Color(10, 12, 15));
        g.fillRoundRect(offsetX - 1, offsetY - 1, MINI_W + 2, MINI_H + 2, 4, 4);
        g.drawImage(thumbnail, offsetX, offsetY, null);

        // ── Animais ────────────────────────────────────────────────────────────
        for (AnimalSimulado a : animais) {
            int ax = offsetX + mapX((int) a.getX());
            int ay = offsetY + mapY((int) a.getY());
            EstadoAnimal est = a.getEstado();

            Color cor;
            int r = 3;
            if (est == EstadoAnimal.FORA_DA_AREA) {
                cor = new Color(220, 60, 60);
                r = 4;
            } else if (est == EstadoAnimal.EM_RESGATE || est == EstadoAnimal.RETORNANDO) {
                cor = COR_AMARELO;
                r = 3;
            } else {
                cor = Color.WHITE;
                r = 2;
            }

            g.setColor(new Color(0, 0, 0, 100));
            g.fillOval(ax - r + 1, ay - r + 1, r * 2, r * 2);
            g.setColor(cor);
            g.fillOval(ax - r, ay - r, r * 2, r * 2);
        }

        // ── Câmera (retângulo da view atual) ───────────────────────────────────
        if (mapaPanel != null) {
            int vx = offsetX + mapX(mapaPanel.getOffsetX());
            int vy = offsetY + mapY(mapaPanel.getOffsetY());
            int vw = mapX(mapaPanel.getWidth());
            int vh = mapY(mapaPanel.getHeight());
            g.setColor(new Color(255, 255, 255, 80));
            g.drawRect(vx, vy, Math.min(vw, MINI_W), Math.min(vh, MINI_H));
        }

        // ── Borda do minimapa ──────────────────────────────────────────────────
        g.setColor(COR_BORDA);
        g.drawRoundRect(offsetX - 1, offsetY - 1, MINI_W + 2, MINI_H + 2, 4, 4);

        offsetY += MINI_H + 6;

        // ── Legenda ────────────────────────────────────────────────────────────
        desenharLegenda(g, offsetX, offsetY);
    }

    private void desenharLegenda(Graphics2D g, int x, int y) {
        Object[][] itens = {
            {"●", "Animal",      Color.WHITE},
            {"●", "Fora da área", new Color(220, 60, 60)},
            {"●", "Em resgate",  COR_AMARELO},
            {"■", "Água",        new Color(55, 125, 190)},
            {"■", "Alimentação", new Color(195, 155, 45)},
            {"■", "Curral",      new Color(130, 100, 50)},
            {"⬜","Limites ⊠",    new Color(200, 200, 200)},
        };

        g.setFont(new Font("SansSerif", Font.PLAIN, 9));
        int col1 = x, col2 = x + (MINI_W / 2) + 4;
        int linha = y + 12;
        for (int i = 0; i < itens.length; i++) {
            int cx = (i % 2 == 0) ? col1 : col2;
            int cy = linha + (i / 2) * 14;
            g.setColor((Color) itens[i][2]);
            g.drawString((String) itens[i][0], cx, cy);
            g.setColor(COR_TEXTO_SEC);
            g.drawString((String) itens[i][1], cx + 12, cy);
        }
    }
}
