package frontend;

import backend.AnimalSimulado;
import backend.EstadoAnimal;
import backend.ResgatadorVirtual;
import backend.TotemCaptura;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.ArrayList;
import java.util.Random;
import java.util.function.Consumer;

/**
 * MapaPanel — mapa 2D estilo RPG pixel-art top-down (Zelda / Stardew Valley).
 * Tudo desenhado via Graphics2D puro, sem imagens externas.
 *
 * Coordenadas:
 *   - O mapa tem tamanho lógico MAP_W x MAP_H pixels
 *   - A câmera (offsetX, offsetY) controla o scroll
 *   - Arrastar com botão ESQUERDO move a câmera
 *   - Clique simples (< 5px movimento) → dispara onAnimalClick
 *   - Scroll vertical: roda do mouse
 *   - Scroll horizontal: Shift + roda do mouse
 *   - Navegação com teclas ←→↑↓ (40px por tecla)
 */
public class MapaPanel extends JPanel {

    // ── Dimensões ──────────────────────────────────────────────────────────────
    public static final int MAP_W = 1600;
    public static final int MAP_H = 1200;
    private static final int TILE = 32;

    // ── Câmera ─────────────────────────────────────────────────────────────────
    private int offsetX = 0;
    private int offsetY = 0;
    private int dragStartX, dragStartY;
    private int dragOffsetX, dragOffsetY;
    private boolean dragging = false;
    private int mousePressX, mousePressY;

    // ── Estado ─────────────────────────────────────────────────────────────────
    private List<AnimalSimulado> animais = new ArrayList<>();
    private List<ResgatadorVirtual> resgatadores = new ArrayList<>();
    private List<TotemCaptura> totens = new ArrayList<>();
    private AnimalSimulado animalSelecionado = null;
    private TotemCaptura totemArrastando = null; // totem sendo movido pelo usuário
    private Consumer<AnimalSimulado> onAnimalClick;

    // ── Flags de visibilidade ─────────────────────────────────────────────────
    private boolean exibirNomes        = true;
    private boolean exibirEstados      = true;
    private boolean exibirResgatadores = true;
    private boolean exibirGrade        = false;

    // ── Mapa pré-renderizado ────────────────────────────────────────────────────
    private BufferedImage mapaCache = null;
    private boolean mapaSujo = true;

    // ── Cache de sprites de vaca por estado ──────────────────────────────────
    private final java.util.EnumMap<EstadoAnimal, BufferedImage> spriteCache =
            new java.util.EnumMap<>(EstadoAnimal.class);

    private BufferedImage getSpriteVaca(EstadoAnimal estado) {
        return spriteCache.computeIfAbsent(estado, s -> {
            BufferedImage img = new BufferedImage(60, 80, BufferedImage.TYPE_INT_ARGB);
            Graphics2D sg = img.createGraphics();
            sg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            sg.translate(30, 48); // centro do sprite dentro da imagem
            boolean foraArea = s == EstadoAnimal.FORA_DA_AREA;
            boolean emResgate = s == EstadoAnimal.EM_RESGATE || s == EstadoAnimal.RETORNANDO;
            desenharVacaSprite(sg, s, emResgate, foraArea);
            sg.dispose();
            return img;
        });
    }

    // Seeds para variações determinísticas ──────────────────────────────────
    private final Random rng = new Random(42L);

    // ── Pulso de alerta ────────────────────────────────────────────────────────
    private float pulsePhase = 0f;
    private Timer pulseTimer;

    // ── Notificações flutuantes ────────────────────────────────────────────────
    private final List<Notificacao> notificacoes = new ArrayList<>();

    // ── Paleta estilo RPG pixel-art ───────────────────────────────────────────
    // Grama
    private static final Color GRAMA_BASE    = new Color(80, 152, 56);
    private static final Color GRAMA_CLARA   = new Color(96, 172, 68);
    private static final Color GRAMA_ESCURA  = new Color(60, 124, 44);
    private static final Color GRAMA_SOMBRA  = new Color(48, 100, 36);
    // Água
    private static final Color AGUA_RASA     = new Color(72, 168, 220);
    private static final Color AGUA_MEDIA    = new Color(48, 128, 192);
    private static final Color AGUA_FUNDA    = new Color(28, 88, 156);
    private static final Color AGUA_BRILHO   = new Color(140, 210, 248);
    private static final Color AREIA_BORDA   = new Color(148, 124, 72);
    private static final Color AREIA_ESCURA  = new Color(120, 100, 56);
    // Terra / caminhos
    private static final Color TERRA_BASE    = new Color(164, 128, 72);
    private static final Color TERRA_CLARA   = new Color(184, 148, 92);
    private static final Color TERRA_ESCURA  = new Color(132, 100, 52);
    private static final Color TERRA_BORDA   = new Color(108, 80, 40);
    // Madeira
    private static final Color MADEIRA_BASE  = new Color(124, 84, 40);
    private static final Color MADEIRA_CLARA = new Color(148, 108, 60);
    private static final Color MADEIRA_ESC   = new Color(92, 60, 24);
    // Pedra
    private static final Color PEDRA_BASE    = new Color(112, 108, 100);
    private static final Color PEDRA_CLARA   = new Color(144, 140, 132);
    private static final Color PEDRA_ESC     = new Color(80, 76, 68);
    // Alertas
    private static final Color GEOFENCE_COLOR = new Color(80, 200, 120);
    private static final Color GEOFENCE_FILL  = new Color(80, 200, 120, 8);
    private static final Color ANEL_DENTRO    = new Color(60, 220, 100);
    private static final Color ANEL_FORA_1    = new Color(255, 60, 60);
    private static final Color ANEL_FORA_2    = new Color(255, 140, 60);

    // ══════════════════════════════════════════════════════════════════════════
    // Construtor
    // ══════════════════════════════════════════════════════════════════════════
    public MapaPanel() {
        setBackground(new Color(20, 25, 20));
        setOpaque(true);
        setFocusable(true);
        configurarMouse();
        configurarTeclado();

        pulseTimer = new Timer(50, e -> {
            pulsePhase += 0.12f;
            if (pulsePhase > (float)(Math.PI * 2)) pulsePhase -= (float)(Math.PI * 2);
            if (isShowing()) repaint();
        });

        // Inicia/para o timer junto com a visibilidade do painel
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0) {
                if (isShowing()) {
                    if (!pulseTimer.isRunning()) pulseTimer.start();
                } else {
                    pulseTimer.stop();
                }
            }
        });
    }

    // ══════════════════════════════════════════════════════════════════════════
    // API pública
    // ══════════════════════════════════════════════════════════════════════════
    public void setAnimais(List<AnimalSimulado> animais) {
        this.animais = animais;
        repaint();
    }

    public void setResgatadores(List<ResgatadorVirtual> resgatadores) {
        this.resgatadores = resgatadores;
        repaint();
    }

    public void setTotens(List<TotemCaptura> totens) {
        this.totens = totens;
        repaint();
    }

    public void setOnAnimalClick(Consumer<AnimalSimulado> cb) {
        this.onAnimalClick = cb;
    }

    public void selecionarAnimal(AnimalSimulado a) {
        this.animalSelecionado = a;
        repaint();
    }

    public void deselecionarAnimal() {
        this.animalSelecionado = null;
        repaint();
    }

    public void adicionarNotificacao(String texto, Color cor) {
        notificacoes.add(new Notificacao(texto, cor));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // paintComponent
    // ══════════════════════════════════════════════════════════════════════════
    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);

        // Painel ainda sem tamanho definido — aguarda próximo repaint
        if (getWidth() <= 0 || getHeight() <= 0) return;

        Graphics2D g = (Graphics2D) g0.create();
        try {
            // Pixel-art: desligar antialiasing para bordas nítidas nos tiles,
            // mas manter para sprites e overlays
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_OFF);
            g.setRenderingHint(RenderingHints.KEY_RENDERING,         RenderingHints.VALUE_RENDER_SPEED);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            // 1. Mapa de fundo — constrói cache uma única vez (ou quando marcado sujo)
            if (mapaSujo || mapaCache == null) {
                BufferedImage novo = new BufferedImage(MAP_W, MAP_H, BufferedImage.TYPE_INT_ARGB);
                Graphics2D mg = novo.createGraphics();
                mg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
                try {
                    desenharMapa(mg);
                } finally {
                    mg.dispose();
                }
                mapaCache = novo;   // só substitui se chegou até aqui sem exceção
                mapaSujo  = false;
            }

            g.translate(-offsetX, -offsetY);
            g.drawImage(mapaCache, 0, 0, null);

            // sprites com antialiasing ligado
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // 2. Totens de captura
            for (TotemCaptura t : totens) {
                desenharTotem(g, t);
            }

            // 3. Resgatadores
            if (exibirResgatadores) {
                for (ResgatadorVirtual r : resgatadores) {
                    desenharResgatador(g, r);
                }
            }

            // 3. Animais
            for (AnimalSimulado a : animais) {
                desenharAnimal(g, a);
            }

            // 4. Seleção
            if (animalSelecionado != null) {
                desenharSelecao(g, animalSelecionado);
            }

            // Voltar para coordenadas de tela antes das notificações
            g.translate(offsetX, offsetY);

            // 5. Notificações (coordenadas de tela)
            desenharNotificacoes(g);

        } catch (Exception ex) {
            // Garante que qualquer exceção de renderização não deixe a tela branca sem diagnóstico
            ex.printStackTrace();
        } finally {
            g.dispose();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MAPA — tiles e elementos estáticos estilo RPG
    // ══════════════════════════════════════════════════════════════════════════
    private void desenharMapa(Graphics2D g) {
        // Ordem de pintura (painter's algorithm):
        desenharGramadoRPG(g);           // 1. chão base em tiles
        desenharRioLateral(g);           // 2. rio/lago com margem de areia
        desenharLago(g, 320, 640, 180, 110);
        desenharBebedouro(g, 900, 680, 60, 44);
        desenharBebedouro(g, 200, 920, 50, 38);
        desenharCaminhoTerraBordado(g);  // 3. caminhos com borda de degrau
        desenharAreaAlimentacao(g, 780, 760, 120, 70);
        desenharAreaAlimentacao(g, 400, 900, 100, 60);
        desenharCercasRPG(g);            // 4. cercas pixel-art
        desenharCurralRPG(g, 560, 120, 260, 200);
        desenharParedePedra(g);          // 5. mureta no topo
        desenharArvoresRPG(g);           // 6. árvores volumosas com sombra
        desenharArbustosRPG(g);          // 7. arbustos e flores
        desenharPedrasRPG(g);            // 8. pedras decorativas
        desenharLimiteArea(g);
        desenharVignetteEscura(g);       // 9. vinheta nas bordas
    }

    // ── Gramado em tiles com variação de brilho por bloco ────────────────────
    // Cada tile 32x32 tem uma cor ligeiramente diferente, criando o aspecto
    // "chequerboard suave" dos jogos RPG.
    private void desenharGramadoRPG(Graphics2D g) {
        Color[] verdes = {
            GRAMA_BASE,
            GRAMA_CLARA,
            new Color(88, 160, 60),
            new Color(72, 144, 52),
            GRAMA_ESCURA,
            new Color(84, 156, 60),
        };

        Random r = new Random(7L);
        int cols = MAP_W / TILE + 1;
        int rows = MAP_H / TILE + 1;

        for (int ty = 0; ty < rows; ty++) {
            for (int tx = 0; tx < cols; tx++) {
                int x = tx * TILE;
                int y = ty * TILE;

                // cor base do tile (determinística pela posição)
                int idx = Math.abs((tx * 3 + ty * 7 + r.nextInt(3))) % verdes.length;
                Color base = verdes[idx];
                g.setColor(base);
                g.fillRect(x, y, TILE, TILE);

                // borda sutil inferior/direita (simula profundidade de tile)
                g.setColor(new Color(0, 0, 0, 18));
                g.fillRect(x, y + TILE - 2, TILE, 2);   // sombra inferior
                g.fillRect(x + TILE - 2, y, 2, TILE);   // sombra direita

                // brincadeiras de grama: linhas verticais curtas
                if (r.nextInt(5) == 0) {
                    int gx = x + 3 + r.nextInt(TILE - 8);
                    int gy = y + 4 + r.nextInt(TILE - 10);
                    Color grc = new Color(
                        Math.max(0, base.getRed() - 20),
                        Math.max(0, base.getGreen() - 15),
                        Math.max(0, base.getBlue() - 10), 200);
                    g.setColor(grc);
                    g.fillRect(gx,     gy, 2, 6);
                    g.fillRect(gx + 4, gy + 1, 2, 5);
                    g.fillRect(gx + 8, gy, 2, 6);
                }
                // manchas de terra ocasionais
                if (r.nextInt(18) == 0) {
                    g.setColor(new Color(120, 90, 48, 80));
                    g.fillOval(x + r.nextInt(20), y + r.nextInt(20), 8, 6);
                }
            }
        }
    }

    // ── Rio na borda esquerda com areia nas margens ───────────────────────────
    // Imita o lago/rio com ponte da imagem de referência.
    private void desenharRioLateral(Graphics2D g) {
        int rx = 0, ry = 300, rw = 140, rh = 500;

        // Margem de areia/barro (2 camadas)
        g.setColor(AREIA_BORDA);
        g.fillRect(rx, ry - 12, rw + 20, rh + 24);

        // Borda escura (degrau pixel-art)
        g.setColor(AREIA_ESCURA);
        g.fillRect(rx, ry - 12, rw + 20, 4);          // topo
        g.fillRect(rx, ry + rh + 8, rw + 20, 4);      // base

        // Água — gradiente concêntrico em faixas (estilo tile)
        // Faixa rasa (borda)
        g.setColor(AGUA_RASA);
        g.fillRect(rx, ry, rw + 20, rh);
        // Faixa média
        g.setColor(AGUA_MEDIA);
        g.fillRect(rx, ry, rw, rh);
        // Faixa funda (centro)
        g.setColor(AGUA_FUNDA);
        g.fillRect(rx, ry + 20, rw - 20, rh - 40);

        // Linhas de ondulação (horizontais, estilo pixel)
        desenharOndasRio(g, rx, ry, rw, rh);

        // Reflexo de luz (diagonal, opaco)
        g.setColor(new Color(AGUA_BRILHO.getRed(), AGUA_BRILHO.getGreen(), AGUA_BRILHO.getBlue(), 60));
        g.fillRect(rx + 8, ry + 40, 18, rh - 80);

        // Ponte de madeira sobre o rio
        desenharPonteRPG(g, rx + 20, ry + rh / 2 - 30, 120, 60);
    }

    private void desenharOndasRio(Graphics2D g, int rx, int ry, int rw, int rh) {
        g.setColor(new Color(AGUA_BRILHO.getRed(), AGUA_BRILHO.getGreen(), AGUA_BRILHO.getBlue(), 90));
        for (int wy = ry + 30; wy < ry + rh - 20; wy += 28) {
            // onda curta pixel
            g.fillRect(rx + 10, wy,     20, 2);
            g.fillRect(rx + 38, wy + 6, 14, 2);
            g.fillRect(rx + 60, wy + 2, 18, 2);
        }
    }

    // ── Ponte de madeira pixel-art ────────────────────────────────────────────
    private void desenharPonteRPG(Graphics2D g, int x, int y, int w, int h) {
        int tabuaH = 8;
        int numTabuas = h / (tabuaH + 2);

        // Trilhos laterais (madeira escura)
        g.setColor(MADEIRA_ESC);
        g.fillRect(x, y, 8, h);
        g.fillRect(x + w - 8, y, 8, h);

        // Tábuas horizontais
        for (int i = 0; i < numTabuas; i++) {
            int ty = y + i * (tabuaH + 2);
            g.setColor(i % 2 == 0 ? MADEIRA_BASE : MADEIRA_CLARA);
            g.fillRect(x + 8, ty, w - 16, tabuaH);
            // sombra inferior de cada tábua
            g.setColor(new Color(0, 0, 0, 40));
            g.fillRect(x + 8, ty + tabuaH - 2, w - 16, 2);
            // highlight superior
            g.setColor(new Color(255, 255, 255, 20));
            g.fillRect(x + 8, ty, w - 16, 2);
        }

        // Grade lateral dos trilhos (postes)
        g.setColor(MADEIRA_ESC);
        for (int py = y; py < y + h; py += 20) {
            g.fillRect(x - 2, py, 12, 4);
            g.fillRect(x + w - 10, py, 12, 4);
        }
    }

    // ── Lago com margem de areia estilo RPG ───────────────────────────────────
    private void desenharLago(Graphics2D g, int cx, int cy, int rw, int rh) {
        // Margem exterior — sombra elíptica (dá impressão de profundidade)
        g.setColor(new Color(0, 0, 0, 50));
        g.fillOval(cx - rw - 6, cy - rh - 6, (rw + 6) * 2, (rh + 6) * 2);

        // Areia/barro na margem (2 faixas, pixel-art nítido)
        g.setColor(AREIA_ESCURA);
        g.fillOval(cx - rw - 18, cy - rh - 14, (rw + 18) * 2, (rh + 14) * 2);
        g.setColor(AREIA_BORDA);
        g.fillOval(cx - rw - 10, cy - rh - 8, (rw + 10) * 2, (rh + 8) * 2);

        // Pedrinhas na margem
        Random r = new Random(cx);
        for (int i = 0; i < 28; i++) {
            double ang = i * Math.PI * 2 / 28 + r.nextDouble() * 0.2;
            int px = (int)(cx + Math.cos(ang) * (rw + 4));
            int py = (int)(cy + Math.sin(ang) * (rh + 4));
            int ps = 4 + r.nextInt(6);
            g.setColor(r.nextBoolean() ? PEDRA_CLARA : PEDRA_BASE);
            g.fillRect(px - ps/2, py - ps/3, ps, ps * 2 / 3);
            g.setColor(PEDRA_ESC);
            g.fillRect(px - ps/2, py, ps, 2); // sombra inferior pixel
        }

        // Água: faixas concêntricas (pixel-art, sem gradiente suave)
        // Borda rasa
        g.setColor(AGUA_RASA);
        g.fillOval(cx - rw, cy - rh, rw * 2, rh * 2);
        // Média
        g.setColor(AGUA_MEDIA);
        g.fillOval(cx - (int)(rw * 0.78), cy - (int)(rh * 0.78),
                   (int)(rw * 1.56), (int)(rh * 1.56));
        // Funda (centro)
        g.setColor(AGUA_FUNDA);
        g.fillOval(cx - (int)(rw * 0.5), cy - (int)(rh * 0.5),
                   (int)(rw), (int)(rh));

        // Linhas de ondulação pixel
        g.setColor(new Color(AGUA_BRILHO.getRed(), AGUA_BRILHO.getGreen(), AGUA_BRILHO.getBlue(), 100));
        g.fillRect(cx - rw/3,      cy - 6, rw * 2/3, 3);
        g.fillRect(cx - rw/4 + 10, cy + 12, rw/3, 3);
        g.fillRect(cx - rw/5 - 5,  cy - 20, rw/4, 3);

        // Reflexo de luz (diagonal)
        g.setColor(new Color(255, 255, 255, 30));
        g.fillOval(cx - rw/3, cy - rh/2, rw * 2/3, rh/3);

        // Nenúfares pixel-art
        desenharNenufarRPG(g, cx - 40, cy + 20);
        desenharNenufarRPG(g, cx + 45, cy - 18);
        desenharNenufarRPG(g, cx + 15, cy + 38);

        // Entrada d'água (cascata)
        g.setColor(AGUA_RASA);
        g.fillRect(cx - 6, cy - rh - 14, 12, 16);
        g.setColor(AGUA_BRILHO);
        g.fillRect(cx - 2, cy - rh - 14, 4, 16);
    }

    private void desenharNenufarRPG(Graphics2D g, int x, int y) {
        // Folha — verde escuro com borda verde médio
        g.setColor(new Color(44, 128, 48));
        g.fillRect(x - 8, y - 4, 16, 10);
        g.setColor(new Color(68, 160, 60));
        g.fillRect(x - 6, y - 6, 12, 10);
        // Flor central rosa
        g.setColor(new Color(220, 72, 112));
        g.fillRect(x - 3, y - 3, 6, 5);
        g.setColor(new Color(255, 200, 200));
        g.fillRect(x - 1, y - 2, 2, 2);
    }

    // ── Bebedouro circular ─────────────────────────────────────────────────────
    private void desenharBebedouro(Graphics2D g, int cx, int cy, int rw, int rh) {
        // Base de pedra
        g.setColor(PEDRA_ESC);
        g.fillOval(cx - rw - 5, cy - rh - 4, (rw + 5) * 2, (rh + 4) * 2);
        g.setColor(PEDRA_BASE);
        g.fillOval(cx - rw - 2, cy - rh - 2, (rw + 2) * 2, (rh + 2) * 2);
        g.setColor(PEDRA_CLARA);
        g.fillRect(cx - rw, cy - rh - 2, rw * 2, 5); // highlight topo

        // Água
        g.setColor(AGUA_RASA);
        g.fillOval(cx - rw, cy - rh, rw * 2, rh * 2);
        g.setColor(AGUA_MEDIA);
        g.fillOval(cx - (int)(rw*0.7), cy - (int)(rh*0.7),
                   (int)(rw*1.4), (int)(rh*1.4));
        // Reflexo
        g.setColor(new Color(255, 255, 255, 55));
        g.fillRect(cx - rw/2, cy - rh/2, rw/2, rh/3);
    }

    // ── Área de alimentação estilo RPG ────────────────────────────────────────
    private void desenharAreaAlimentacao(Graphics2D g, int x, int y, int w, int h) {
        // Moldura de madeira (4 tábuas, pixel)
        g.setColor(MADEIRA_ESC);
        g.fillRect(x - 8, y - 6, w + 16, h + 12);
        g.setColor(MADEIRA_BASE);
        g.fillRect(x - 6, y - 4, w + 12, h + 8);
        g.setColor(MADEIRA_CLARA);
        g.fillRect(x - 6, y - 4, w + 12, 4); // highlight topo
        // Sombra inferior
        g.setColor(new Color(0, 0, 0, 50));
        g.fillRect(x - 6, y + h + 2, w + 12, 6);

        // Feno
        g.setColor(new Color(212, 172, 52));
        g.fillRect(x, y, w, h);
        // Textura do feno (linhas curtas)
        Random r = new Random(x);
        g.setColor(new Color(180, 140, 32, 200));
        for (int i = 0; i < 28; i++) {
            int lx = x + 2 + r.nextInt(w - 6);
            int ly = y + 2 + r.nextInt(h - 6);
            g.fillRect(lx, ly, 6, 2);
        }
        g.setColor(new Color(240, 200, 80, 140));
        for (int i = 0; i < 14; i++) {
            int lx = x + 2 + r.nextInt(w - 6);
            int ly = y + 2 + r.nextInt(h - 6);
            g.fillRect(lx, ly, 4, 2);
        }

        // Label
        g.setFont(new Font("Monospaced", Font.BOLD, 8));
        FontMetrics fm = g.getFontMetrics();
        String label = "ALIMENTAÇÃO";
        g.setColor(new Color(80, 50, 10, 180));
        g.drawString(label, x + (w - fm.stringWidth(label)) / 2, y + h / 2 + 3);
    }

    // ── Caminhos de terra com borda de "degrau" pixel-art ─────────────────────
    // O "degrau" é a borda escura de 4px que cria ilusão de profundidade.
    private void desenharCaminhoTerraBordado(Graphics2D g) {
        // Caminho horizontal (principal)
        desenharCaminhoRPG(g, 0, 480, MAP_W, 48, true);
        // Caminho vertical
        desenharCaminhoRPG(g, 640, 0, 48, 528, false);
        // Trilha diagonal (pontilhada)
        desenharTrilhaRPG(g, 780, 480, 900, 680);
    }

    private void desenharCaminhoRPG(Graphics2D g, int x, int y, int w, int h, boolean horiz) {
        // Sombra (degrau escuro)
        g.setColor(TERRA_BORDA);
        if (horiz) {
            g.fillRect(x, y, w, h);
            // borda superior e inferior mais escura (profundidade)
            g.setColor(TERRA_ESCURA);
            g.fillRect(x, y, w, 4);
            g.fillRect(x, y + h - 4, w, 4);
        } else {
            g.fillRect(x, y, w, h);
            g.setColor(TERRA_ESCURA);
            g.fillRect(x, y, 4, h);
            g.fillRect(x + w - 4, y, 4, h);
        }

        // Camada base de terra
        g.setColor(TERRA_BASE);
        if (horiz) {
            g.fillRect(x, y + 4, w, h - 8);
        } else {
            g.fillRect(x + 4, y, w - 8, h);
        }

        // Highlight central claro
        g.setColor(TERRA_CLARA);
        if (horiz) {
            g.fillRect(x, y + 4, w, 4);
        } else {
            g.fillRect(x + 4, y, 4, h);
        }

        // Pedrinhas e detalhes
        Random r = new Random(x + y * 31);
        g.setColor(new Color(132, 100, 52, 160));
        for (int i = 0; i < (w * h) / 60; i++) {
            int px = x + r.nextInt(w);
            int py = y + r.nextInt(h);
            g.fillRect(px, py, 2, 2);
        }
    }

    private void desenharTrilhaRPG(Graphics2D g, int x1, int y1, int x2, int y2) {
        // Trilha de pegadas de terra (quadradinhos pontilhados)
        double dx = x2 - x1, dy = y2 - y1;
        double len = Math.sqrt(dx * dx + dy * dy);
        double ux = dx / len, uy = dy / len;
        g.setColor(TERRA_ESCURA);
        for (double t = 0; t < len; t += 20) {
            int tx = (int)(x1 + ux * t);
            int ty = (int)(y1 + uy * t);
            g.fillRect(tx - 5, ty - 5, 10, 10);
        }
        g.setColor(TERRA_BASE);
        for (double t = 0; t < len; t += 20) {
            int tx = (int)(x1 + ux * t);
            int ty = (int)(y1 + uy * t);
            g.fillRect(tx - 4, ty - 4, 8, 8);
        }
    }

    // ── Cerca de madeira estilo RPG (mourões + trilhos) ───────────────────────
    private void desenharCercasRPG(Graphics2D g) {
        desenharSegmentoCercaRPG(g, 0,     240, MAP_W, 240, true);
        desenharSegmentoCercaRPG(g, 0,    1000, MAP_W, 1000, true);
        desenharSegmentoCercaRPG(g, 200,   240, 200,  1000, false);
        desenharSegmentoCercaRPG(g, 1400,  240, 1400, 1000, false);
        desenharPortaoRPG(g, 620,  240, true);
        desenharPortaoRPG(g, 620, 1000, true);
        desenharPortaoRPG(g, 200,  600, false);
        desenharPortaoRPG(g, 1400, 600, false);
    }

    private void desenharSegmentoCercaRPG(Graphics2D g, int x1, int y1, int x2, int y2, boolean horiz) {
        if (horiz) {
            int len = x2 - x1;
            // Trilhos horizontais (2 barras)
            // sombra dos trilhos
            g.setColor(MADEIRA_ESC);
            g.fillRect(x1, y1 - 2, len, 6);
            g.fillRect(x1, y1 + 14, len, 6);
            // trilhos
            g.setColor(MADEIRA_BASE);
            g.fillRect(x1, y1 - 2, len, 5);
            g.fillRect(x1, y1 + 14, len, 5);
            // highlight topo
            g.setColor(MADEIRA_CLARA);
            g.fillRect(x1, y1 - 2, len, 2);
            g.fillRect(x1, y1 + 14, len, 2);
            // Mourões a cada 40px
            for (int x = x1; x <= x2; x += 40) {
                desenharMouraoRPG(g, x, y1 - 12);
            }
        } else {
            int len = y2 - y1;
            g.setColor(MADEIRA_ESC);
            g.fillRect(x1 - 2, y1, 6, len);
            g.fillRect(x1 + 14, y1, 6, len);
            g.setColor(MADEIRA_BASE);
            g.fillRect(x1 - 2, y1, 5, len);
            g.fillRect(x1 + 14, y1, 5, len);
            g.setColor(MADEIRA_CLARA);
            g.fillRect(x1 - 2, y1, 2, len);
            g.fillRect(x1 + 14, y1, 2, len);
            for (int y = y1; y <= y2; y += 40) {
                desenharMouraoRPG(g, x1 - 2, y - 12);
            }
        }
    }

    private void desenharMouraoRPG(Graphics2D g, int x, int y) {
        // Mourão pixel: retângulo com sombra lateral e topo pontudo
        // Sombra direita/baixo
        g.setColor(MADEIRA_ESC);
        g.fillRect(x + 2, y + 2, 10, 36);
        // Corpo
        g.setColor(MADEIRA_BASE);
        g.fillRect(x, y, 10, 36);
        // Highlight esquerdo
        g.setColor(MADEIRA_CLARA);
        g.fillRect(x, y, 3, 36);
        // Topo triangular (pico do mourão)
        g.setColor(MADEIRA_ESC);
        int[] xp = {x, x + 5, x + 10};
        int[] yp = {y, y - 7,  y};
        g.fillPolygon(xp, yp, 3);
        g.setColor(MADEIRA_BASE);
        int[] xp2 = {x, x + 5, x + 10};
        int[] yp2 = {y, y - 6,  y};
        g.fillPolygon(xp2, yp2, 3);
    }

    private void desenharPortaoRPG(Graphics2D g, int cx, int cy, boolean horiz) {
        int w = horiz ? 80 : 14;
        int h = horiz ? 14 : 80;
        int x = horiz ? cx - 40 : cx - 7;
        int y = horiz ? cy - 7 : cy - 40;

        // Fundo escuro
        g.setColor(MADEIRA_ESC);
        g.fillRect(x - 2, y - 2, w + 4, h + 4);
        // Madeira
        g.setColor(MADEIRA_BASE);
        g.fillRect(x, y, w, h);
        // Linhas das tábuas
        g.setColor(MADEIRA_ESC);
        if (horiz) {
            for (int i = 0; i < 3; i++) g.fillRect(x, y + 4 + i * 4, w, 1);
            // highlight
            g.setColor(MADEIRA_CLARA);
            g.fillRect(x, y, w, 2);
        } else {
            for (int i = 0; i < 3; i++) g.fillRect(x + 4 + i * 4, y, 1, h);
            g.setColor(MADEIRA_CLARA);
            g.fillRect(x, y, 2, h);
        }
        // Dobradiças de ferro
        g.setColor(new Color(70, 65, 60));
        if (horiz) {
            g.fillRect(x + 4, y + 3, 8, 8);
            g.fillRect(x + w - 12, y + 3, 8, 8);
        } else {
            g.fillRect(x + 3, y + 4, 8, 8);
            g.fillRect(x + 3, y + h - 12, 8, 8);
        }
    }

    // ── Curral estilo RPG ─────────────────────────────────────────────────────
    private void desenharCurralRPG(Graphics2D g, int x, int y, int w, int h) {
        // Chão de terra
        g.setColor(TERRA_ESCURA);
        g.fillRect(x + 2, y + 2, w, h);
        g.setColor(TERRA_BASE);
        g.fillRect(x, y, w, h);

        // Textura de terra
        Random r = new Random(x + y);
        g.setColor(new Color(130, 96, 48, 160));
        for (int i = 0; i < 60; i++) {
            g.fillRect(x + r.nextInt(w - 4), y + r.nextInt(h - 4), 2 + r.nextInt(4), 2);
        }

        // Teto/cobertura — faixas de telha de madeira escura
        g.setColor(MADEIRA_ESC);
        g.fillRect(x, y, w, 44);
        g.setColor(MADEIRA_BASE);
        for (int tx = x; tx < x + w; tx += 14) {
            g.fillRect(tx, y, 7, 44);
        }
        g.setColor(MADEIRA_CLARA);
        g.fillRect(x, y, w, 3); // highlight no topo
        // Sombra do teto no chão
        g.setColor(new Color(0, 0, 0, 60));
        g.fillRect(x, y + 44, w, 24);
        // gradiente rápido (2 faixas)
        g.setColor(new Color(0, 0, 0, 35));
        g.fillRect(x, y + 44, w, 12);

        // Colunas de sustentação
        for (int i = 0; i <= 2; i++) {
            int cx = x + i * (w / 2);
            g.setColor(MADEIRA_ESC);
            g.fillRect(cx - 5, y, 10, h);
            g.setColor(MADEIRA_BASE);
            g.fillRect(cx - 5, y, 8, h);
            g.setColor(MADEIRA_CLARA);
            g.fillRect(cx - 5, y, 3, h);
        }

        // Borda da parede
        g.setColor(MADEIRA_ESC);
        g.drawRect(x, y, w, h);

        // Grade interna
        g.setColor(new Color(100, 68, 28, 180));
        for (int fx = x + 18; fx < x + w - 18; fx += 28) {
            g.fillRect(fx, y + h - 64, 3, 54);
        }
        g.fillRect(x + 18, y + h - 52, w - 36, 3);
        g.fillRect(x + 18, y + h - 34, w - 36, 3);
    }

    // ── Mureta de pedra no topo (perto da entrada da masmorra) ────────────────
    private void desenharParedePedra(Graphics2D g) {
        int px = 420, py = 60, pw = 260, ph = 48;

        // Sombra
        g.setColor(new Color(0, 0, 0, 60));
        g.fillRect(px + 4, py + 4, pw, ph);

        // Blocos de pedra (padrão tijolo alternado)
        int bw = 32, bh = 16;
        for (int row = 0; row < ph / bh; row++) {
            int offset = (row % 2 == 0) ? 0 : bw / 2;
            for (int col = -1; col <= pw / bw + 1; col++) {
                int bx = px + col * bw - offset;
                int by_ = py + row * bh;
                if (bx + bw < px || bx > px + pw) continue;
                // clip
                int cbx = Math.max(bx, px);
                int cby = by_;
                int cbw = Math.min(bx + bw, px + pw) - cbx;
                if (cbw <= 0) continue;

                g.setColor(row % 2 == 0 ? PEDRA_BASE : new Color(104, 100, 92));
                g.fillRect(cbx, cby, cbw, bh - 1);
                g.setColor(PEDRA_CLARA);
                g.fillRect(cbx, cby, cbw, 3); // highlight topo
                g.setColor(PEDRA_ESC);
                g.fillRect(cbx, cby + bh - 2, cbw, 2); // sombra base
                // juntas verticais
                g.setColor(new Color(60, 56, 50));
                if (bx >= px) g.fillRect(cbx, cby, 1, bh - 1);
            }
        }

        // Abertura da caverna/entrada (arco escuro)
        int ax = px + pw / 2 - 24, ay = py - 4;
        g.setColor(new Color(20, 15, 10));
        g.fillRect(ax, ay, 48, ph + 4);
        g.fillOval(ax, ay - 12, 48, 24); // arco superior
        // Borda do arco em pedra
        g.setColor(PEDRA_ESC);
        g.drawRect(ax, ay, 48, ph + 4);
    }

    // ── Árvores estilo RPG com copa em camadas quadradas ─────────────────────
    private void desenharArvoresRPG(Graphics2D g) {
        int[][] posicoes = {
            {40, 40}, {110, 20}, {190, 50}, {20, 140}, {80, 160}, {150, 90},
            {1460, 40}, {1530, 80}, {1550, 160}, {1480, 20}, {1400, 60},
            {40, 1080}, {120, 1100}, {200, 1060}, {80, 1140}, {20, 1020},
            {1480, 1060}, {1540, 1100}, {1420, 1080}, {1560, 1020},
            {340, 140}, {440, 90}, {500, 200}, {380, 300},
            {1100, 140}, {1200, 80}, {1280, 200}, {1150, 300},
            {100, 550}, {140, 700}, {80, 850},
            {1500, 550}, {1530, 700}, {1490, 850},
            {440, 1060}, {600, 1100}, {740, 1080}, {860, 1060},
            {1000, 1100}, {1120, 1080}, {1250, 1060},
            {700, 340}, {760, 380}, {820, 320},
            {1000, 700}, {1050, 750}, {980, 800},
        };

        Random r = new Random(13L);
        for (int[] p : posicoes) {
            int tamanho = 28 + r.nextInt(20);
            desenharArvoreRPG(g, p[0], p[1], tamanho, r);
        }
    }

    /**
     * Árvore estilo RPG:
     *  - Sombra elíptica no chão
     *  - Tronco retangular escuro com highlight
     *  - Copa em 3 camadas de retângulos (base larga + topo menor + destaque)
     *  - Borda escura de 1px ao redor da copa (pixel-art outline)
     */
    private void desenharArvoreRPG(Graphics2D g, int cx, int cy, int size, Random r) {
        // Sombra elíptica no chão
        g.setColor(new Color(0, 0, 0, 48));
        g.fillOval(cx - size/2 + 4, cy + size/3, size + 8, size/3 + 4);

        // Tronco
        int tw = Math.max(8, size / 4);
        int th = size / 2 + 8;
        g.setColor(MADEIRA_ESC);
        g.fillRect(cx - tw/2 + 2, cy + 2, tw, th); // sombra
        g.setColor(new Color(112 + r.nextInt(16), 72 + r.nextInt(12), 32 + r.nextInt(10)));
        g.fillRect(cx - tw/2, cy, tw, th);
        g.setColor(new Color(148, 108, 60)); // highlight
        g.fillRect(cx - tw/2, cy, tw/3, th);
        // textura casca (linhas horizontais escuras)
        g.setColor(new Color(0, 0, 0, 40));
        for (int i = 1; i < 4; i++) {
            g.fillRect(cx - tw/2 + tw/3, cy + i * (th/4), tw - tw/3, 1);
        }

        // Paleta da copa (variação por semente)
        Color copaBase  = new Color(32 + r.nextInt(16), 100 + r.nextInt(24), 32 + r.nextInt(16));
        Color copaMedia = new Color(56 + r.nextInt(16), 132 + r.nextInt(20), 52 + r.nextInt(16));
        Color copaClara = new Color(80 + r.nextInt(14), 164 + r.nextInt(16), 68 + r.nextInt(14));
        Color copaBorda = new Color(20, 60, 20);

        int s2  = size + 4;
        int s2h = (int)(s2 * 0.9);

        // Camada 1 — base (retângulo arredondado, cor escura)
        // outline preto pixel-art
        g.setColor(copaBorda);
        g.fillRoundRect(cx - s2/2 - 1, cy - size - 1, s2 + 2, s2h + 2, 4, 4);
        g.setColor(copaBase);
        g.fillRoundRect(cx - s2/2, cy - size, s2, s2h, 4, 4);

        // Camada 2 — média (sobre a base)
        int s3 = (int)(size * 0.80);
        g.setColor(copaBorda);
        g.fillRoundRect(cx - s3/2 - 1, cy - size - s3/4 - 1, s3 + 2, s3 + 2, 4, 4);
        g.setColor(copaMedia);
        g.fillRoundRect(cx - s3/2, cy - size - s3/4, s3, s3, 4, 4);

        // Camada 3 — destaque topo (pequeno, claro)
        int s4 = (int)(size * 0.44);
        g.setColor(copaClara);
        g.fillRoundRect(cx - s4/2 - 2, cy - size - s3/4 - s4/3, s4, s4, 3, 3);

        // Highlight de luz (pixel branco no canto superior)
        g.setColor(new Color(255, 255, 255, 50));
        g.fillRect(cx - s3/2 + 4, cy - size - s3/4 + 4, s3/4, s3/5);
    }

    // ── Arbustos e flores estilo RPG ──────────────────────────────────────────
    private void desenharArbustosRPG(Graphics2D g) {
        Random r = new Random(77L);
        Color[] coresFlor = {
            new Color(220, 72, 112),
            new Color(240, 200, 52),
            new Color(176, 96, 216),
            new Color(240, 116, 52),
            new Color(248, 248, 248),
        };

        int[][] arbustos = {
            {280, 550}, {740, 200}, {920, 300}, {1100, 500}, {380, 820},
            {820, 880}, {1300, 700}, {600, 1000}, {1050, 920}, {450, 380},
            {1250, 400}, {700, 840}, {150, 400}, {1350, 350}
        };

        for (int[] p : arbustos) {
            int sz = 12 + r.nextInt(10);
            // Sombra
            g.setColor(new Color(0, 0, 0, 36));
            g.fillRect(p[0] - sz + 3, p[1] - sz/2 + 4, sz * 2, (int)(sz * 1.4));
            // outline escuro
            g.setColor(new Color(28, 80, 24));
            g.fillRect(p[0] - sz - 1, p[1] - sz/2 - 1, sz * 2 + 2, (int)(sz * 1.4) + 2);
            // corpo escuro
            g.setColor(new Color(44 + r.nextInt(16), 112 + r.nextInt(20), 36 + r.nextInt(16)));
            g.fillRect(p[0] - sz, p[1] - sz/2, sz * 2, (int)(sz * 1.4));
            // topo mais claro
            g.setColor(new Color(64 + r.nextInt(18), 148 + r.nextInt(18), 52 + r.nextInt(18)));
            g.fillRect(p[0] - sz/2, p[1] - sz, sz, sz);
            // highlight
            g.setColor(new Color(255, 255, 255, 30));
            g.fillRect(p[0] - sz/4, p[1] - sz + 2, sz/3, sz/4);
        }

        // Flores espalhadas
        for (int i = 0; i < 60; i++) {
            int fx = 250 + r.nextInt(1100);
            int fy = 280 + r.nextInt(680);
            Color flor = coresFlor[r.nextInt(coresFlor.length)];
            // Caule
            g.setColor(new Color(52, 120, 44));
            g.fillRect(fx, fy, 2, 8);
            // Pétalas (4 pixels ao redor)
            g.setColor(flor);
            g.fillRect(fx - 2, fy - 2, 6, 2);
            g.fillRect(fx - 2, fy + 2, 6, 2);
            g.fillRect(fx - 2, fy - 2, 2, 6);
            g.fillRect(fx + 4, fy - 2, 2, 6);
            // Centro amarelo
            g.setColor(new Color(240, 216, 52));
            g.fillRect(fx, fy, 2, 2);
        }
    }

    // ── Pedras decorativas pixel-art ─────────────────────────────────────────
    private void desenharPedrasRPG(Graphics2D g) {
        int[][] pedras = {
            {260, 660, 16}, {300, 680, 12}, {390, 620, 10},
            {850, 730, 14}, {920, 720, 11}, {960, 760, 9},
            {1180, 560, 13}, {1220, 590, 10}, {1160, 600, 8},
            {520, 990, 15}, {570, 1010, 11}, {490, 1020, 9},
        };
        Random r = new Random(55L);
        for (int[] p : pedras) {
            int px = p[0], py = p[1], ps = p[2];
            // Sombra
            g.setColor(new Color(0, 0, 0, 48));
            g.fillRect(px - ps/2 + 2, py + 2, ps, ps * 2/3);
            // Corpo (retangular pixel)
            g.setColor(r.nextBoolean() ? PEDRA_BASE : new Color(120, 116, 108));
            g.fillRect(px - ps/2, py - ps/3, ps, ps * 2/3);
            // Highlight topo
            g.setColor(PEDRA_CLARA);
            g.fillRect(px - ps/2, py - ps/3, ps, 3);
            // Sombra base escura
            g.setColor(PEDRA_ESC);
            g.fillRect(px - ps/2, py + ps/3 - 3, ps, 3);
        }
    }

    // ── Limite da área (geofence) ─────────────────────────────────────────────
    private void desenharLimiteArea(Graphics2D g) {
        int margem = 200;
        int x = margem, y = margem;
        int w = MAP_W - margem * 2;
        int h = MAP_H - margem * 2;

        g.setColor(GEOFENCE_FILL);
        g.fillRoundRect(x, y, w, h, 24, 24);

        Stroke s = g.getStroke();
        g.setColor(new Color(GEOFENCE_COLOR.getRed(), GEOFENCE_COLOR.getGreen(), GEOFENCE_COLOR.getBlue(), 100));
        g.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER,
                10f, new float[]{14, 7}, 0));
        g.drawRoundRect(x, y, w, h, 24, 24);

        g.setColor(new Color(GEOFENCE_COLOR.getRed(), GEOFENCE_COLOR.getGreen(), GEOFENCE_COLOR.getBlue(), 40));
        g.setStroke(new BasicStroke(5.0f));
        g.drawRoundRect(x + 3, y + 3, w - 6, h - 6, 20, 20);
        g.setStroke(s);
    }

    // ── Vinheta escura nas bordas ─────────────────────────────────────────────
    private void desenharVignetteEscura(Graphics2D g) {
        int borda = 64;
        // Usa faixas sólidas de opacidade decrescente (pixel-art, sem gradiente suave)
        int[] alphas = {80, 55, 35, 18};
        for (int i = 0; i < alphas.length; i++) {
            int espessura = borda / alphas.length;
            int off = i * espessura;
            g.setColor(new Color(0, 0, 0, alphas[i]));
            g.fillRect(0,           off, MAP_W,         espessura);   // topo
            g.fillRect(0, MAP_H - off - espessura, MAP_W, espessura); // base
            g.fillRect(off,          0, espessura, MAP_H);             // esq
            g.fillRect(MAP_W - off - espessura, 0, espessura, MAP_H); // dir
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TOTENS — ícone de sensor fixo com raio de detecção animado
    // ══════════════════════════════════════════════════════════════════════════
    private void desenharTotem(Graphics2D g, TotemCaptura totem) {
        int cx = (int) totem.getX();
        int cy = (int) totem.getY();
        int raio = (int) totem.getRaioDeteccao();

        // Pular totens fora da área visível
        if (cx + raio < offsetX || cx - raio > offsetX + getWidth() ||
            cy + raio < offsetY || cy - raio > offsetY + getHeight()) return;

        boolean temAnimal = totem.quantidadeDentroAgora() > 0;

        // ── Raio de detecção (anel pulsante) ─────────────────────────────────
        double alpha = totem.getPulsoAlpha();
        Color corRaio = temAnimal
            ? new Color(60, 220, 160, (int)(alpha * 80))
            : new Color(80, 160, 240, (int)(alpha * 55));
        Color corRaioB = temAnimal
            ? new Color(60, 220, 160, (int)(alpha * 180))
            : new Color(80, 160, 240, (int)(alpha * 130));

        g.setColor(corRaio);
        g.fillOval(cx - raio, cy - raio, raio * 2, raio * 2);
        Stroke s = g.getStroke();
        g.setStroke(new BasicStroke(temAnimal ? 2.0f : 1.4f,
            BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER,
            10f, new float[]{8, 5}, (float)(pulsePhase * 6)));
        g.setColor(corRaioB);
        g.drawOval(cx - raio, cy - raio, raio * 2, raio * 2);
        g.setStroke(s);

        // ── Sombra elíptica no chão ───────────────────────────────────────────
        g.setColor(new Color(0, 0, 0, 55));
        g.fillOval(cx - 10, cy + 14, 20, 7);

        // ── Poste (haste vertical) ────────────────────────────────────────────
        g.setColor(new Color(70, 70, 80));
        g.fillRect(cx - 3, cy - 6, 6, 22);
        g.setColor(new Color(110, 110, 125));
        g.fillRect(cx - 3, cy - 6, 3, 22); // highlight esquerdo

        // ── Base do poste ─────────────────────────────────────────────────────
        g.setColor(new Color(55, 55, 65));
        g.fillRect(cx - 7, cy + 14, 14, 5);
        g.setColor(new Color(90, 90, 100));
        g.fillRect(cx - 7, cy + 14, 14, 2);

        // ── Corpo do sensor (caixa eletrônica) ────────────────────────────────
        g.setColor(new Color(40, 44, 55));
        g.fillRoundRect(cx - 11, cy - 20, 22, 16, 4, 4);
        // Borda iluminada
        Color corBorda = temAnimal ? new Color(60, 230, 150) : new Color(80, 160, 240);
        g.setColor(corBorda);
        g.setStroke(new BasicStroke(1.4f));
        g.drawRoundRect(cx - 11, cy - 20, 22, 16, 4, 4);
        g.setStroke(s);

        // ── LED pulsante ──────────────────────────────────────────────────────
        float pulso = (float)(0.5 + 0.5 * Math.sin(pulsePhase + totem.getId()));
        Color corLED = temAnimal
            ? new Color(60, 255, 160, (int)(180 + 75 * pulso))
            : new Color(80, 180, 255, (int)(160 + 80 * pulso));
        g.setColor(corLED);
        g.fillOval(cx - 3, cy - 16, 6, 6);
        g.setColor(new Color(255, 255, 255, (int)(120 * pulso)));
        g.fillOval(cx - 2, cy - 16, 3, 3); // reflexo

        // ── Antena ────────────────────────────────────────────────────────────
        g.setColor(new Color(90, 90, 100));
        g.fillRect(cx + 4, cy - 28, 2, 10);
        g.setColor(corLED);
        g.fillOval(cx + 3, cy - 31, 5, 5); // ponta da antena

        // ── Ícone de sinal (ondas de rádio quando ativo) ──────────────────────
        if (temAnimal) {
            int wa = (int)(alpha * 120);
            g.setStroke(new BasicStroke(1.2f));
            g.setColor(new Color(60, 220, 160, wa));
            g.drawArc(cx + 8, cy - 26, 8, 8, -30, 120);
            g.drawArc(cx + 5, cy - 30, 14, 14, -30, 120);
            g.setStroke(s);
        }

        // ── Nome do totem ─────────────────────────────────────────────────────
        g.setFont(new Font("Monospaced", Font.BOLD, 8));
        FontMetrics fm = g.getFontMetrics();
        String label = totem.getNome();
        int lw = fm.stringWidth(label);
        g.setColor(new Color(0, 0, 0, 150));
        g.fillRoundRect(cx - lw/2 - 3, cy + 22, lw + 6, 11, 3, 3);
        g.setColor(temAnimal ? new Color(100, 255, 180) : new Color(150, 200, 255));
        g.drawString(label, cx - lw/2, cy + 31);

        // ── Badge com contador de capturas ────────────────────────────────────
        if (totem.totalCapturas() > 0) {
            String count = String.valueOf(totem.totalCapturas());
            g.setFont(new Font("Monospaced", Font.BOLD, 9));
            fm = g.getFontMetrics();
            int cw = fm.stringWidth(count) + 6;
            g.setColor(new Color(240, 160, 40));
            g.fillOval(cx + 8, cy - 24, cw, 13);
            g.setColor(Color.BLACK);
            g.drawString(count, cx + 11, cy - 14);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ANIMAIS — sprites top-down com anel indicador
    // ══════════════════════════════════════════════════════════════════════════
    private void desenharAnimal(Graphics2D g, AnimalSimulado a) {
        int x = (int) a.getX();
        int y = (int) a.getY();

        // Pular animais fora da área visível (viewport culling)
        int vx1 = offsetX - 60, vy1 = offsetY - 80;
        int vx2 = offsetX + getWidth() + 60, vy2 = offsetY + getHeight() + 80;
        if (x < vx1 || x > vx2 || y < vy1 || y > vy2) return;

        EstadoAnimal estado = a.getEstado();
        boolean foraArea = (estado == EstadoAnimal.FORA_DA_AREA);
        boolean emResgate = (estado == EstadoAnimal.EM_RESGATE || estado == EstadoAnimal.RETORNANDO);

        // Anel indicador
        if (foraArea) {
            float pulso = (float)(0.5 + 0.5 * Math.sin(pulsePhase + a.getBlinkPhase()));
            Stroke s = g.getStroke();
            int r2 = (int)(24 + 5 * pulso);
            g.setColor(new Color(ANEL_FORA_1.getRed(), ANEL_FORA_1.getGreen(), ANEL_FORA_1.getBlue(), (int)(180 * pulso)));
            g.setStroke(new BasicStroke(2.5f));
            g.drawOval(x - r2, y - r2, r2 * 2, r2 * 2);
            int r3 = r2 - 5;
            g.setColor(new Color(ANEL_FORA_2.getRed(), ANEL_FORA_2.getGreen(), ANEL_FORA_2.getBlue(), (int)(160 * pulso)));
            g.setStroke(new BasicStroke(1.8f));
            g.drawOval(x - r3, y - r3, r3 * 2, r3 * 2);
            g.setColor(new Color(220, 40, 40, (int)(35 * pulso)));
            g.fillOval(x - r3, y - r3, r3 * 2, r3 * 2);
            g.setStroke(s);
        } else {
            Stroke s = g.getStroke();
            g.setColor(new Color(ANEL_DENTRO.getRed(), ANEL_DENTRO.getGreen(), ANEL_DENTRO.getBlue(), 90));
            g.setStroke(new BasicStroke(1.8f));
            g.drawOval(x - 18, y - 18, 36, 36);
            g.setStroke(s);
        }

        // Sombra elíptica no chão
        g.setColor(new Color(0, 0, 0, 55));
        g.fillOval(x - 13, y + 9, 26, 9);

        // Sprite cacheado — rotacionado conforme direção de movimento
        BufferedImage sprite = getSpriteVaca(estado);
        double angulo = Math.atan2(a.getVy(), a.getVx());
        Graphics2D gc = (Graphics2D) g.create();
        gc.translate(x, y);
        gc.rotate(angulo + Math.PI / 2);
        gc.drawImage(sprite, -30, -48, null); // offset = centro do sprite
        gc.dispose();

        if (foraArea) desenharIconeAlerta(g, x, y - 32);
        if (emResgate) desenharIconeResgate(g, x, y - 30);
        if (a == animalSelecionado) desenharBarrasStatus(g, a, x, y);

        // ID
        g.setFont(new Font("Monospaced", Font.BOLD, 9));
        String id = "#" + String.format("%03d", a.getAnimal().getId());
        FontMetrics fm = g.getFontMetrics();
        int tw = fm.stringWidth(id);
        g.setColor(new Color(0, 0, 0, 130));
        g.fillRoundRect(x - tw/2 - 2, y + 15, tw + 4, 12, 3, 3);
        g.setColor(foraArea ? new Color(255, 120, 120) : Color.WHITE);
        g.drawString(id, x - tw/2, y + 24);
    }

    private void desenharVacaSprite(Graphics2D g, EstadoAnimal estado, boolean emResgate, boolean foraArea) {
        Color corCorpo, corManchas;
        if (foraArea) {
            corCorpo = new Color(220, 180, 160);
            corManchas = new Color(180, 80, 60);
        } else if (emResgate) {
            corCorpo = new Color(200, 200, 180);
            corManchas = new Color(80, 80, 100);
        } else {
            corCorpo = new Color(230, 220, 200);
            corManchas = new Color(40, 35, 30);
        }
        Color corPernas = new Color(180, 160, 130);
        Color corFocinho = new Color(210, 170, 150);

        Stroke sOrig = g.getStroke();

        g.setColor(new Color(0, 0, 0, 30));
        g.fillOval(-11, -14, 22, 30);

        g.setColor(corCorpo);
        g.fillOval(-10, -16, 20, 28);

        g.setColor(corManchas);
        g.fillOval(-6, -12, 7, 9);
        g.fillOval(1, -4, 6, 8);
        g.fillOval(-7, 4, 5, 6);

        g.setColor(new Color(0, 0, 0, 80));
        g.setStroke(new BasicStroke(1.2f));
        g.drawOval(-10, -16, 20, 28);
        g.setStroke(sOrig);

        g.setColor(corPernas);
        g.fillOval(-12, -8, 5, 5);
        g.fillOval(7, -8, 5, 5);
        g.fillOval(-12, 8, 5, 5);
        g.fillOval(7, 8, 5, 5);
        g.setColor(new Color(0, 0, 0, 60));
        g.drawOval(-12, -8, 5, 5);
        g.drawOval(7, -8, 5, 5);
        g.drawOval(-12, 8, 5, 5);
        g.drawOval(7, 8, 5, 5);

        g.setColor(corCorpo);
        g.fillOval(-7, -24, 14, 12);
        g.setColor(corManchas);
        g.fillOval(-3, -22, 5, 4);
        g.setColor(new Color(0, 0, 0, 80));
        g.setStroke(new BasicStroke(1f));
        g.drawOval(-7, -24, 14, 12);
        g.setStroke(sOrig);

        g.setColor(new Color(200, 180, 120));
        g.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D chifEsq = new Path2D.Float();
        chifEsq.moveTo(-5, -24);
        chifEsq.curveTo(-10, -32, -12, -30, -8, -27);
        g.draw(chifEsq);
        Path2D chifDir = new Path2D.Float();
        chifDir.moveTo(5, -24);
        chifDir.curveTo(10, -32, 12, -30, 8, -27);
        g.draw(chifDir);
        g.setStroke(sOrig);

        g.setColor(corFocinho);
        g.fillOval(-5, -20, 10, 7);
        g.setColor(new Color(160, 100, 80));
        g.fillOval(-4, -18, 3, 2);
        g.fillOval(1, -18, 3, 2);

        g.setColor(new Color(30, 20, 10));
        g.fillOval(-5, -22, 3, 3);
        g.fillOval(2, -22, 3, 3);
        g.setColor(new Color(255, 255, 255, 200));
        g.fillOval(-5, -23, 2, 2);
        g.fillOval(2, -23, 2, 2);

        g.setColor(corCorpo);
        g.fillOval(-11, -24, 6, 5);
        g.fillOval(5, -24, 6, 5);
        g.setColor(new Color(200, 160, 140));
        g.fillOval(-10, -23, 4, 3);
        g.fillOval(6, -23, 4, 3);

        g.setColor(new Color(180, 160, 130));
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D rabo = new Path2D.Float();
        rabo.moveTo(0, 12);
        rabo.curveTo(10, 16, 14, 14, 12, 18);
        g.draw(rabo);
        g.setStroke(sOrig);

        Color corEstado = corEstado(estado);
        g.setColor(new Color(0, 0, 0, 50));
        g.fillOval(-3, -31, 8, 8);
        g.setColor(corEstado);
        g.fillOval(-4, -32, 8, 8);
        g.setColor(new Color(255, 255, 255, 100));
        g.fillOval(-3, -32, 4, 4);
        g.setColor(new Color(0, 0, 0, 60));
        g.setStroke(new BasicStroke(0.8f));
        g.drawOval(-4, -32, 8, 8);
        g.setStroke(sOrig);
    }

    private Color corEstado(EstadoAnimal estado) {
        return switch (estado) {
            case ANDANDO      -> new Color(100, 200, 100);
            case COMENDO      -> new Color(240, 180, 50);
            case BEBENDO      -> new Color(80, 160, 240);
            case DESCANSANDO  -> new Color(180, 140, 220);
            case EM_GRUPO     -> new Color(100, 220, 200);
            case FORA_DA_AREA -> new Color(240, 60, 60);
            case EM_RESGATE   -> new Color(240, 140, 40);
            case RETORNANDO   -> new Color(60, 200, 240);
        };
    }

    private void desenharIconeAlerta(Graphics2D g, int cx, int cy) {
        float pulso = (float)(0.5 + 0.5 * Math.sin(pulsePhase * 1.5f));
        g.setColor(new Color(220, 30, 30, (int)(210 * pulso)));
        g.fillOval(cx - 8, cy - 8, 16, 16);
        g.setColor(new Color(255, 80, 80, (int)(180 * pulso)));
        Stroke s = g.getStroke();
        g.setStroke(new BasicStroke(1.4f));
        g.drawOval(cx - 8, cy - 8, 16, 16);
        g.setStroke(s);
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 11));
        FontMetrics fm = g.getFontMetrics();
        g.drawString("!", cx - fm.stringWidth("!")/2, cy + fm.getAscent()/2 - 1);
    }

    private void desenharIconeResgate(Graphics2D g, int cx, int cy) {
        g.setColor(new Color(60, 200, 100));
        g.fillOval(cx - 8, cy - 8, 16, 16);
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 10));
        g.drawString("+", cx - 4, cy + 3);
    }

    private void desenharBarrasStatus(Graphics2D g, AnimalSimulado a, int cx, int cy) {
        int bx = cx + 20, by = cy - 24, bw = 50, bh = 5, gap = 7;
        desenharBarrinha(g, bx, by,         bw, bh, a.getFome()    / 100.0, new Color(220, 80, 80));
        desenharBarrinha(g, bx, by + gap,   bw, bh, a.getSede()    / 100.0, new Color(80, 140, 220));
        desenharBarrinha(g, bx, by + gap*2, bw, bh, a.getEnergia() / 100.0, new Color(80, 200, 80));
    }

    private void desenharBarrinha(Graphics2D g, int x, int y, int w, int h, double val, Color cor) {
        g.setColor(new Color(0, 0, 0, 140));
        g.fillRoundRect(x - 1, y - 1, w + 2, h + 2, 3, 3);
        g.setColor(new Color(50, 50, 50, 180));
        g.fillRoundRect(x, y, w, h, 3, 3);
        g.setColor(cor);
        g.fillRoundRect(x, y, (int)(w * val), h, 3, 3);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // RESGATADOR
    // ══════════════════════════════════════════════════════════════════════════
    private void desenharResgatador(Graphics2D g, ResgatadorVirtual r) {
        int x = (int) r.getX();
        int y = (int) r.getY();

        // Pular resgatadores fora da área visível
        if (x < offsetX - 60 || x > offsetX + getWidth() + 60 ||
            y < offsetY - 80 || y > offsetY + getHeight() + 80) return;

        g.setColor(new Color(0, 0, 0, 55));
        g.fillOval(x - 11, y + 12, 22, 8);

        g.setColor(new Color(70, 48, 22));
        g.fillRoundRect(x - 7, y + 12, 7, 6, 3, 3);
        g.fillRoundRect(x + 1, y + 12, 7, 6, 3, 3);
        g.setColor(new Color(110, 80, 40, 120));
        g.fillRect(x - 6, y + 12, 3, 3);
        g.fillRect(x + 2, y + 12, 3, 3);

        g.setColor(new Color(55, 55, 95));
        g.fillRoundRect(x - 6, y + 3, 5, 10, 2, 2);
        g.fillRoundRect(x + 1, y + 3, 5, 10, 2, 2);
        g.setColor(new Color(40, 40, 80, 150));
        g.fillRect(x - 5, y + 11, 4, 2);
        g.fillRect(x + 2, y + 11, 4, 2);

        g.setColor(new Color(100, 70, 30));
        g.fillRect(x - 7, y + 1, 14, 3);
        g.setColor(new Color(190, 160, 55));
        g.fillRect(x - 2, y + 1, 4, 3);
        g.setColor(new Color(140, 110, 30));
        Stroke s = g.getStroke();
        g.setStroke(new BasicStroke(0.8f));
        g.drawRect(x - 2, y + 1, 4, 3);
        g.setStroke(s);

        int hash = r.getNome().hashCode();
        Color[] camisas = {
            new Color(50, 130, 60),
            new Color(60, 100, 180),
            new Color(180, 80, 50),
            new Color(150, 100, 40),
            new Color(120, 60, 160),
        };
        Color camisa = camisas[Math.abs(hash) % camisas.length];
        Color camisaEscura = camisa.darker();
        g.setColor(new Color(0,0,0,30));
        g.fillRoundRect(x - 6, y - 11, 12, 14, 4, 4);
        g.setColor(camisa);
        g.fillRoundRect(x - 7, y - 12, 14, 14, 4, 4);
        g.setColor(camisaEscura);
        g.fillRect(x - 1, y - 11, 2, 10);
        g.setColor(camisa);
        g.fillOval(x - 11, y - 10, 6, 5);
        g.fillOval(x + 5, y - 10, 6, 5);

        g.setColor(new Color(210, 170, 130));
        g.fillRect(x - 3, y - 16, 6, 5);
        g.setColor(new Color(220, 180, 140));
        g.fillOval(x - 7, y - 26, 14, 13);
        g.setColor(new Color(180, 140, 100, 120));
        g.setStroke(new BasicStroke(0.8f));
        g.drawOval(x - 7, y - 26, 14, 13);
        g.setStroke(s);
        g.setColor(new Color(40, 30, 20));
        g.fillOval(x - 4, y - 22, 2, 2);
        g.fillOval(x + 2, y - 22, 2, 2);
        g.setColor(new Color(255, 255, 255, 200));
        g.fillOval(x - 4, y - 23, 1, 1);
        g.fillOval(x + 2, y - 23, 1, 1);
        g.setColor(new Color(160, 100, 80));
        g.setStroke(new BasicStroke(1.0f));
        g.drawArc(x - 3, y - 19, 6, 4, 200, 140);
        g.setStroke(s);

        g.setColor(new Color(75, 50, 20));
        g.fillRoundRect(x - 9, y - 30, 18, 5, 3, 3);
        g.setColor(new Color(55, 35, 12));
        g.setStroke(new BasicStroke(0.7f));
        g.drawRoundRect(x - 9, y - 30, 18, 5, 3, 3);
        g.setStroke(s);
        g.setColor(new Color(85, 58, 24));
        g.fillRoundRect(x - 6, y - 38, 12, 10, 3, 3);
        g.setColor(new Color(50, 30, 10));
        g.fillRect(x - 6, y - 30, 12, 2);
        g.setColor(new Color(110, 78, 38, 150));
        g.fillRect(x - 5, y - 38, 4, 8);

        g.setFont(new Font("SansSerif", Font.BOLD, 9));
        String nome = r.getNome();
        FontMetrics fm = g.getFontMetrics();
        int tw = fm.stringWidth(nome);
        int bh = 13, bw2 = tw + 10;
        int bx = x - bw2/2, by = y - 52;
        g.setColor(new Color(20, 22, 30, 200));
        g.fillRoundRect(bx, by, bw2, bh, 5, 5);
        g.setColor(new Color(camisa.getRed(), camisa.getGreen(), camisa.getBlue(), 200));
        g.setStroke(new BasicStroke(1.2f));
        g.drawRoundRect(bx, by, bw2, bh, 5, 5);
        g.setStroke(s);
        g.setColor(camisa);
        g.fillRoundRect(bx, by + 2, 3, bh - 4, 2, 2);
        g.setColor(new Color(220, 235, 220));
        g.drawString(nome, bx + 7, by + bh - 3);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SELEÇÃO
    // ══════════════════════════════════════════════════════════════════════════
    private void desenharSelecao(Graphics2D g, AnimalSimulado a) {
        int x = (int) a.getX(), y = (int) a.getY();
        float pulso = (float)(0.6 + 0.4 * Math.sin(pulsePhase));
        int r = (int)(22 + 4 * pulso);
        Stroke s = g.getStroke();
        g.setColor(new Color(255, 230, 60, (int)(200 * pulso)));
        g.setStroke(new BasicStroke(2.5f));
        g.drawOval(x - r, y - r, r * 2, r * 2);
        g.setColor(new Color(255, 240, 100, 60));
        g.fillOval(x - r, y - r, r * 2, r * 2);
        g.setStroke(s);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // NOTIFICAÇÕES
    // ══════════════════════════════════════════════════════════════════════════
    private void desenharNotificacoes(Graphics2D g) {
        notificacoes.removeIf(n -> n.expirou());
        int baseY = getHeight() - 80;
        for (int i = notificacoes.size() - 1; i >= 0; i--) {
            Notificacao n = notificacoes.get(i);
            n.desenhar(g, 16, baseY);
            baseY -= 44;
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // NAVEGAÇÃO POR TECLADO
    // ══════════════════════════════════════════════════════════════════════════
    private void configurarTeclado() {
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                int step = 40;
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_LEFT  -> offsetX -= step;
                    case KeyEvent.VK_RIGHT -> offsetX += step;
                    case KeyEvent.VK_UP    -> offsetY -= step;
                    case KeyEvent.VK_DOWN  -> offsetY += step;
                    default -> { return; }
                }
                clampOffsets();
                repaint();
            }
        });
    }

    private void clampOffsets() {
        int pw = getWidth();
        int ph = getHeight();
        offsetX = (pw <= 0) ? 0 : Math.max(0, Math.min(MAP_W - pw,  offsetX));
        offsetY = (ph <= 0) ? 0 : Math.max(0, Math.min(MAP_H - ph, offsetY));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MOUSE
    // ══════════════════════════════════════════════════════════════════════════
    private void configurarMouse() {
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                if (SwingUtilities.isLeftMouseButton(e)) {
                    mousePressX = e.getX();
                    mousePressY = e.getY();
                    dragStartX  = e.getX() + offsetX;
                    dragStartY  = e.getY() + offsetY;
                    dragOffsetX = offsetX;
                    dragOffsetY = offsetY;

                    // Verificar se clicou em cima de um totem (prioridade sobre câmera)
                    int mx = e.getX() + offsetX;
                    int my = e.getY() + offsetY;
                    totemArrastando = null;
                    for (TotemCaptura t : totens) {
                        if (Math.hypot(t.getX() - mx, t.getY() - my) <= 24) {
                            totemArrastando = t;
                            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                            break;
                        }
                    }

                    dragging = totemArrastando == null; // só ativa drag de câmera se não pegou totem
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    if (totemArrastando != null) {
                        // Solta o totem
                        totemArrastando = null;
                        setCursor(Cursor.getDefaultCursor());
                        repaint();
                        return;
                    }
                    if (dragging) {
                        double dist = Math.hypot(e.getX() - mousePressX, e.getY() - mousePressY);
                        if (dist < 5.0) {
                            int mx = e.getX() + offsetX;
                            int my = e.getY() + offsetY;
                            AnimalSimulado clicado = null;
                            double menorDist = 22;
                            for (AnimalSimulado a : animais) {
                                double d = Math.hypot(a.getX() - mx, a.getY() - my);
                                if (d < menorDist) {
                                    menorDist = d;
                                    clicado = a;
                                }
                            }
                            if (clicado != null && onAnimalClick != null) {
                                onAnimalClick.accept(clicado);
                            }
                        }
                        dragging = false;
                        setCursor(Cursor.getDefaultCursor());
                    }
                }
            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int mx = e.getX() + offsetX;
                int my = e.getY() + offsetY;
                boolean sobreTotem = totens.stream()
                        .anyMatch(t -> Math.hypot(t.getX() - mx, t.getY() - my) <= 24);
                setCursor(sobreTotem
                        ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                        : Cursor.getDefaultCursor());
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) return;

                // Arrastar totem tem prioridade
                if (totemArrastando != null) {
                    totemArrastando.setX(e.getX() + offsetX);
                    totemArrastando.setY(e.getY() + offsetY);
                    repaint();
                    return;
                }

                // Arrastar câmera
                if (dragging) {
                    double dist = Math.hypot(e.getX() - mousePressX, e.getY() - mousePressY);
                    if (dist >= 5.0) {
                        setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                        offsetX = dragStartX - e.getX();
                        offsetY = dragStartY - e.getY();
                        clampOffsets();
                        repaint();
                    }
                }
            }
        });

        addMouseWheelListener(e -> {
            boolean shift = (e.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) != 0;
            int delta = e.getWheelRotation() * 30;
            if (shift) offsetX += delta;
            else       offsetY += delta;
            clampOffsets();
            repaint();
        });
    }

    // ── API de câmera ──────────────────────────────────────────────────────────
    public void centralizarEm(double x, double y) {
        offsetX = (int)(x - getWidth() / 2.0);
        offsetY = (int)(y - getHeight() / 2.0);
        clampOffsets();
        repaint();
    }

    public int getOffsetX() { return offsetX; }
    public int getOffsetY() { return offsetY; }

    public boolean isExibirNomes()        { return exibirNomes; }
    public void    setExibirNomes(boolean v)        { this.exibirNomes = v; repaint(); }

    public boolean isExibirEstados()      { return exibirEstados; }
    public void    setExibirEstados(boolean v)      { this.exibirEstados = v; repaint(); }

    public boolean isExibirResgatadores() { return exibirResgatadores; }
    public void    setExibirResgatadores(boolean v) { this.exibirResgatadores = v; repaint(); }

    public boolean isExibirGrade()        { return exibirGrade; }
    public void    setExibirGrade(boolean v)        { this.exibirGrade = v; repaint(); }

    public void centralizarMapa() {
        centralizarEm(MAP_W / 2.0, MAP_H / 2.0);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Classe interna — Notificação flutuante
    // ══════════════════════════════════════════════════════════════════════════
    private static class Notificacao {
        private final String texto;
        private final Color cor;
        private final long criado;
        private static final long DURACAO = 4000;

        Notificacao(String texto, Color cor) {
            this.texto = texto;
            this.cor = cor;
            this.criado = System.currentTimeMillis();
        }

        boolean expirou() {
            return System.currentTimeMillis() - criado > DURACAO;
        }

        void desenhar(Graphics2D g, int x, int y) {
            long age = System.currentTimeMillis() - criado;
            float alpha = age < 300
                ? age / 300f
                : age > DURACAO - 500
                    ? (DURACAO - age) / 500f
                    : 1f;
            alpha = Math.max(0, Math.min(1, alpha));

            Font f = new Font("SansSerif", Font.BOLD, 13);
            g.setFont(f);
            FontMetrics fm = g.getFontMetrics();
            int tw = fm.stringWidth(texto);
            int pw = tw + 28, ph = 34;
            int bx = x, by = y - ph;

            g.setColor(new Color(20, 22, 26, (int)(220 * alpha)));
            g.fillRoundRect(bx, by, pw, ph, 10, 10);
            g.setColor(new Color(cor.getRed(), cor.getGreen(), cor.getBlue(), (int)(200 * alpha)));
            Stroke s = g.getStroke();
            g.setStroke(new BasicStroke(2));
            g.drawRoundRect(bx, by, pw, ph, 10, 10);
            g.setStroke(s);
            g.setColor(new Color(cor.getRed(), cor.getGreen(), cor.getBlue(), (int)(240 * alpha)));
            g.fillRoundRect(bx, by + 4, 4, ph - 8, 3, 3);
            g.setColor(new Color(230, 230, 220, (int)(230 * alpha)));
            g.drawString(texto, bx + 14, by + ph/2 + fm.getAscent()/2 - 2);
        }
    }
}
