package frontend;

import backend.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.function.Consumer;

import static frontend.SimuladorFrame.*;

/**
 * PainelLateral — painel direito do simulador.
 *
 * Seções:
 *   1. Cabeçalho "ANIMAL SELECIONADO"
 *   2. ID, nome, status colorido
 *   3. Grid de info (distância, tempo fora, setor)
 *   4. Barras de fome / sede / energia
 *   5. Linha de status + última posição
 *   6. Botão [ Buscar animal ]
 *   7. Lista de alertas ativos
 */
public class PainelLateral extends JPanel {

    private AnimalSimulado animalAtual = null;
    private Consumer<AnimalSimulado> onBuscarAnimal;
    private final BackendSimulador backend;

    // ── Componentes dinâmicos ──────────────────────────────────────────────────
    private JLabel labelId;
    private JLabel labelNome;
    private JLabel labelStatusAnimal;
    private JLabel labelDistancia;
    private JLabel labelTempoFora;
    private JLabel labelSetor;
    private JLabel labelUltimaPosicao;
    private BarraProgresso barraFome;
    private BarraProgresso barraSede;
    private BarraProgresso barraEnergia;
    private JButton btnBuscar;
    private JPanel painelAlertas;
    private JPanel painelConteudo; // visível quando animal selecionado
    private JPanel painelVazio;    // visível quando nada selecionado

    private long inicioForaDaArea = 0;

    // ══════════════════════════════════════════════════════════════════════════
    public PainelLateral(BackendSimulador backend) {
        this.backend = backend;
        setLayout(new BorderLayout());
        setBackground(COR_BG_PANEL);
        setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, COR_BORDA));
        setPreferredSize(new Dimension(260, 0));
        construirLayout();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Layout
    // ══════════════════════════════════════════════════════════════════════════
    private void construirLayout() {
        // ── Cabeçalho ──────────────────────────────────────────────────────────
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(COR_BG_CARD);
        header.setBorder(new EmptyBorder(10, 14, 10, 14));

        JLabel titulo = new JLabel("ANIMAL SELECIONADO");
        titulo.setFont(new Font("SansSerif", Font.BOLD, 11));
        titulo.setForeground(COR_TEXTO_SEC);
        titulo.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton btnFechar = new JButton("×");
        btnFechar.setFont(new Font("SansSerif", Font.PLAIN, 16));
        btnFechar.setForeground(COR_TEXTO_SEC);
        btnFechar.setBackground(COR_BG_CARD);
        btnFechar.setFocusPainted(false);
        btnFechar.setBorderPainted(false);
        btnFechar.setContentAreaFilled(false);
        btnFechar.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnFechar.addActionListener(e -> limpar());

        header.add(titulo, BorderLayout.CENTER);
        header.add(btnFechar, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        // ── CardLayout: conteúdo vs vazio ──────────────────────────────────────
        JPanel cards = new JPanel(new CardLayout());
        cards.setBackground(COR_BG_PANEL);
        add(cards, BorderLayout.CENTER);

        // Painel vazio (nenhum animal selecionado)
        painelVazio = new JPanel(new GridBagLayout());
        painelVazio.setBackground(COR_BG_PANEL);
        JLabel hint = new JLabel("<html><center><span style='font-size:24px'>🐄</span><br>"
            + "<span style='color:#606878;font-size:11px'>Clique em um animal<br>no mapa</span></center></html>");
        hint.setHorizontalAlignment(SwingConstants.CENTER);
        painelVazio.add(hint);
        cards.add(painelVazio, "vazio");

        // Painel com dados do animal
        painelConteudo = criarPainelConteudo();
        cards.add(painelConteudo, "conteudo");

        // Começa mostrando vazio
        ((CardLayout) cards.getLayout()).show(cards, "vazio");

        // Guarda referência ao cards para trocar depois
        this.putClientProperty("cards", cards);

        // ── Seção de alertas ativos ────────────────────────────────────────────
        JPanel secaoAlertas = criarSecaoAlertas();
        add(secaoAlertas, BorderLayout.SOUTH);
    }

    private JPanel criarPainelConteudo() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(COR_BG_PANEL);
        p.setBorder(new EmptyBorder(12, 14, 8, 14));

        // ── ID grande ──────────────────────────────────────────────────────────
        labelId = new JLabel("#000");
        labelId.setFont(new Font("Monospaced", Font.BOLD, 28));
        labelId.setForeground(COR_VERMELHO);
        labelId.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(labelId);
        p.add(Box.createVerticalStrut(4));

        // ── Nome ───────────────────────────────────────────────────────────────
        labelNome = new JLabel("Vaca");
        labelNome.setFont(new Font("SansSerif", Font.PLAIN, 14));
        labelNome.setForeground(COR_TEXTO);
        labelNome.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(labelNome);
        p.add(Box.createVerticalStrut(2));

        // ── Status ─────────────────────────────────────────────────────────────
        labelStatusAnimal = new JLabel("—");
        labelStatusAnimal.setFont(new Font("SansSerif", Font.BOLD, 12));
        labelStatusAnimal.setForeground(COR_VERDE);
        labelStatusAnimal.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(labelStatusAnimal);
        p.add(Box.createVerticalStrut(14));

        // ── Separador ─────────────────────────────────────────────────────────
        p.add(criarSeparador());
        p.add(Box.createVerticalStrut(10));

        // ── Grid de info ──────────────────────────────────────────────────────
        JPanel grid = new JPanel(new GridLayout(3, 2, 0, 6));
        grid.setBackground(COR_BG_PANEL);
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);
        grid.setMaximumSize(new Dimension(232, 80));

        labelDistancia    = criarValorInfo("—");
        labelTempoFora    = criarValorInfo("—");
        labelSetor        = criarValorInfo("—");

        grid.add(criarRotuloInfo("Distância"));   grid.add(labelDistancia);
        grid.add(criarRotuloInfo("Tempo fora"));  grid.add(labelTempoFora);
        grid.add(criarRotuloInfo("Setor"));       grid.add(labelSetor);
        p.add(grid);
        p.add(Box.createVerticalStrut(14));

        // ── Barras de status ──────────────────────────────────────────────────
        barraFome    = new BarraProgresso("Fome",    "🔥", COR_VERMELHO, 0);
        barraSede    = new BarraProgresso("Sede",    "💧", COR_AZUL,    0);
        barraEnergia = new BarraProgresso("Energia", "⚡", COR_VERDE,   0);
        for (BarraProgresso b : new BarraProgresso[]{barraFome, barraSede, barraEnergia}) {
            b.setAlignmentX(Component.LEFT_ALIGNMENT);
            b.setMaximumSize(new Dimension(232, 28));
            p.add(b);
            p.add(Box.createVerticalStrut(4));
        }
        p.add(Box.createVerticalStrut(10));

        // ── Separador ──────────────────────────────────────────────────────────
        p.add(criarSeparador());
        p.add(Box.createVerticalStrut(8));

        // ── Linha de status final ──────────────────────────────────────────────
        JPanel linhaStatus = new JPanel(new BorderLayout());
        linhaStatus.setBackground(COR_BG_PANEL);
        linhaStatus.setAlignmentX(Component.LEFT_ALIGNMENT);
        linhaStatus.setMaximumSize(new Dimension(232, 20));
        linhaStatus.add(criarRotuloInfo("Status"), BorderLayout.WEST);
        JLabel lsv = new JLabel("—");
        lsv.setFont(new Font("SansSerif", Font.BOLD, 11));
        lsv.setForeground(COR_VERDE);
        linhaStatus.add(lsv, BorderLayout.EAST);
        // Reutiliza labelStatusAnimal como referência de cor
        this.putClientProperty("labelStatusFinal", lsv);
        p.add(linhaStatus);
        p.add(Box.createVerticalStrut(4));

        JPanel linhaPos = new JPanel(new BorderLayout());
        linhaPos.setBackground(COR_BG_PANEL);
        linhaPos.setAlignmentX(Component.LEFT_ALIGNMENT);
        linhaPos.setMaximumSize(new Dimension(232, 20));
        linhaPos.add(criarRotuloInfo("Última posição válida"), BorderLayout.WEST);
        labelUltimaPosicao = criarValorInfo("—");
        linhaPos.add(labelUltimaPosicao, BorderLayout.EAST);
        p.add(linhaPos);
        p.add(Box.createVerticalStrut(16));

        // ── Botão buscar ──────────────────────────────────────────────────────
        btnBuscar = criarBotaoBuscar();
        btnBuscar.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnBuscar.setMaximumSize(new Dimension(232, 40));
        p.add(btnBuscar);
        p.add(Box.createVerticalGlue());

        JScrollPane scroll = new JScrollPane(p);
        scroll.setBackground(COR_BG_PANEL);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUI(new javax.swing.plaf.basic.BasicScrollBarUI() {
            protected void configureScrollBarColors() {
                this.thumbColor = COR_BORDA;
                this.trackColor = COR_BG_PANEL;
            }
        });

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(COR_BG_PANEL);
        wrapper.add(scroll, BorderLayout.CENTER);
        return wrapper;
    }

    private JPanel criarSecaoAlertas() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(COR_BG_CARD);
        p.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, COR_BORDA));
        p.setPreferredSize(new Dimension(260, 160));

        JPanel headerAlertas = new JPanel(new BorderLayout());
        headerAlertas.setBackground(COR_BG_CARD);
        headerAlertas.setBorder(new EmptyBorder(8, 14, 6, 14));
        JLabel tituloAlertas = new JLabel("⚠  ALERTAS ATIVOS");
        tituloAlertas.setFont(new Font("SansSerif", Font.BOLD, 10));
        tituloAlertas.setForeground(COR_AMARELO);
        headerAlertas.add(tituloAlertas);
        p.add(headerAlertas, BorderLayout.NORTH);

        painelAlertas = new JPanel();
        painelAlertas.setLayout(new BoxLayout(painelAlertas, BoxLayout.Y_AXIS));
        painelAlertas.setBackground(COR_BG_CARD);
        painelAlertas.setBorder(new EmptyBorder(0, 14, 8, 14));

        JScrollPane scrollAlertas = new JScrollPane(painelAlertas);
        scrollAlertas.setBackground(COR_BG_CARD);
        scrollAlertas.setBorder(null);
        p.add(scrollAlertas, BorderLayout.CENTER);
        return p;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // API pública
    // ══════════════════════════════════════════════════════════════════════════
    public void selecionarAnimal(AnimalSimulado a) {
        this.animalAtual = a;
        if (a.getEstado() == EstadoAnimal.FORA_DA_AREA && inicioForaDaArea == 0) {
            inicioForaDaArea = System.currentTimeMillis();
        }
        mostrarConteudo(true);
        atualizarAnimalSelecionado();
    }

    public void limpar() {
        this.animalAtual = null;
        this.inicioForaDaArea = 0;
        mostrarConteudo(false);
    }

    public void setOnBuscarAnimal(Consumer<AnimalSimulado> cb) {
        this.onBuscarAnimal = cb;
    }

    public void atualizarAnimalSelecionado() {
        if (animalAtual == null) return;
        AnimalSimulado a = animalAtual;
        EstadoAnimal estado = a.getEstado();

        // ID
        labelId.setText("#" + String.format("%03d", a.getAnimal().getId()));

        // Nome
        labelNome.setText(a.getAnimal().getNome() != null ? a.getAnimal().getNome() : "Vaca");

        // Status
        String nomeEstado = estado.toString().replace("_", " ");
        boolean foraArea = (estado == EstadoAnimal.FORA_DA_AREA);
        Color corStatus = foraArea ? COR_VERMELHO : COR_VERDE;
        labelStatusAnimal.setText(nomeEstado);
        labelStatusAnimal.setForeground(corStatus);
        labelId.setForeground(foraArea ? COR_VERMELHO : COR_ACENTO);

        JLabel statusFinal = (JLabel) getClientProperty("labelStatusFinal");
        if (statusFinal != null) {
            statusFinal.setText(estado.toString());
            statusFinal.setForeground(corStatus);
        }

        // Distância ao centro
        double cx = MapaPanel.MAP_W / 2.0;
        double cy = MapaPanel.MAP_H / 2.0;
        double dist = Math.hypot(a.getX() - cx, a.getY() - cy);
        labelDistancia.setText(String.format("%.0f m", dist * 0.5)); // escala aproximada

        // Tempo fora
        if (foraArea && inicioForaDaArea > 0) {
            long seg = (System.currentTimeMillis() - inicioForaDaArea) / 1000;
            long h = seg / 3600, m = (seg % 3600) / 60, s = seg % 60;
            labelTempoFora.setText(String.format("%02d:%02d:%02d", h, m, s));
        } else {
            labelTempoFora.setText("—");
            inicioForaDaArea = 0;
        }

        // Setor
        String setor = calcularSetor(a.getX(), a.getY());
        labelSetor.setText(setor);

        // Barras
        barraFome.setValor((int) a.getFome());
        barraSede.setValor((int) a.getSede());
        barraEnergia.setValor((int) a.getEnergia());

        // Última posição
        labelUltimaPosicao.setText(String.format("%.1f, %.1f",
            a.getLatitude(), a.getLongitude()));

        // Botão buscar — só visível se fora da área e sem resgate em andamento
        boolean mostrarBuscar = foraArea && a.getResgatadorNome() == null;
        btnBuscar.setVisible(mostrarBuscar);

        // Alertas
        atualizarAlertas();
    }

    private int atualizarAlertasContador = 0;

    private void atualizarAlertas() {
        // Throttle: atualiza somente a cada 30 chamadas (~3s) para evitar spam visual
        atualizarAlertasContador++;
        if (atualizarAlertasContador < 30) return;
        atualizarAlertasContador = 0;

        painelAlertas.removeAll();
        try {
            var engine = backend.getEngine();
            if (engine == null) return;

            // Filtra apenas estados críticos — ignora animais em comportamento normal
            var criticos = engine.getAnimais().stream()
                .filter(a -> a.getEstado() == EstadoAnimal.FORA_DA_AREA
                          || a.getEstado() == EstadoAnimal.EM_RESGATE
                          || a.getEstado() == EstadoAnimal.RETORNANDO)
                .sorted((a, b) -> {
                    // FORA_DA_AREA tem prioridade máxima
                    int prioA = a.getEstado() == EstadoAnimal.FORA_DA_AREA ? 0 : 1;
                    int prioB = b.getEstado() == EstadoAnimal.FORA_DA_AREA ? 0 : 1;
                    return Integer.compare(prioA, prioB);
                })
                .limit(5)
                .toList();

            long total = engine.getAnimais().stream()
                .filter(a -> a.getEstado() == EstadoAnimal.FORA_DA_AREA
                          || a.getEstado() == EstadoAnimal.EM_RESGATE
                          || a.getEstado() == EstadoAnimal.RETORNANDO)
                .count();

            if (criticos.isEmpty()) {
                JLabel semAlertas = new JLabel("✅  Nenhum alerta ativo");
                semAlertas.setFont(new Font("SansSerif", Font.ITALIC, 11));
                semAlertas.setForeground(COR_VERDE);
                painelAlertas.add(semAlertas);
            } else {
                for (var a : criticos) {
                    painelAlertas.add(criarItemAlerta(a));
                    painelAlertas.add(Box.createVerticalStrut(3));
                }
                if (total > 5) {
                    JLabel mais = new JLabel("  + " + (total - 5) + " outros alertas...");
                    mais.setFont(new Font("SansSerif", Font.ITALIC, 10));
                    mais.setForeground(COR_TEXTO_SEC);
                    painelAlertas.add(mais);
                }
            }
        } catch (Exception ignored) {}
        painelAlertas.revalidate();
        painelAlertas.repaint();
    }

    private JPanel criarItemAlerta(AnimalSimulado a) {
        boolean foraArea = a.getEstado() == EstadoAnimal.FORA_DA_AREA;
        boolean emResgate = a.getEstado() == EstadoAnimal.EM_RESGATE;

        Color corFundo  = foraArea ? new Color(40, 20, 20) : new Color(28, 35, 25);
        Color corBorda  = foraArea ? new Color(90, 30, 30) : new Color(45, 80, 45);
        Color corIcone  = foraArea ? COR_VERMELHO : COR_AMARELO;
        String icone    = foraArea ? "⚠" : "↺";

        JPanel item = new JPanel(new BorderLayout(6, 0));
        item.setBackground(corFundo);
        item.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(corBorda, 1),
            new EmptyBorder(4, 8, 4, 8)
        ));
        item.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));

        JLabel ico = new JLabel(icone);
        ico.setFont(new Font("SansSerif", Font.BOLD, 11));
        ico.setForeground(corIcone);
        item.add(ico, BorderLayout.WEST);

        JPanel info = new JPanel(new GridLayout(2, 1, 0, 1));
        info.setBackground(corFundo);

        String idStr = String.format("#%03d", a.getAnimal().getId());
        JLabel labelId = new JLabel(idStr);
        labelId.setFont(new Font("Monospaced", Font.BOLD, 11));
        labelId.setForeground(foraArea ? COR_VERMELHO : COR_AMARELO);
        info.add(labelId);

        String descricao = foraArea
            ? "Fora da área"
            : emResgate
                ? "Em resgate" + (a.getResgatadorNome() != null ? " — " + a.getResgatadorNome() : "")
                : "Retornando";
        JLabel labelDesc = new JLabel(descricao);
        labelDesc.setFont(new Font("SansSerif", Font.PLAIN, 10));
        labelDesc.setForeground(COR_TEXTO_SEC);
        info.add(labelDesc);

        item.add(info, BorderLayout.CENTER);
        return item;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Helpers de layout
    // ══════════════════════════════════════════════════════════════════════════
    private void mostrarConteudo(boolean mostrar) {
        JPanel cards = (JPanel) getClientProperty("cards");
        if (cards != null) {
            CardLayout cl = (CardLayout) cards.getLayout();
            cl.show(cards, mostrar ? "conteudo" : "vazio");
        }
    }

    private JLabel criarRotuloInfo(String texto) {
        JLabel l = new JLabel(texto);
        l.setFont(new Font("SansSerif", Font.PLAIN, 11));
        l.setForeground(COR_TEXTO_SEC);
        return l;
    }

    private JLabel criarValorInfo(String texto) {
        JLabel l = new JLabel(texto);
        l.setFont(new Font("SansSerif", Font.BOLD, 11));
        l.setForeground(COR_TEXTO);
        l.setHorizontalAlignment(SwingConstants.RIGHT);
        return l;
    }

    private JSeparator criarSeparador() {
        JSeparator sep = new JSeparator(JSeparator.HORIZONTAL);
        sep.setForeground(COR_BORDA);
        sep.setMaximumSize(new Dimension(232, 1));
        return sep;
    }

    private JButton criarBotaoBuscar() {
        JButton b = new JButton("  Buscar animal") {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0;
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color cor = getModel().isRollover()
                    ? new Color(70, 220, 120)
                    : COR_ACENTO;
                g.setColor(cor);
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g.setColor(new Color(0, 0, 0, 40));
                g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                super.paintComponent(g0);
            }
        };
        b.setFont(new Font("SansSerif", Font.BOLD, 13));
        b.setForeground(new Color(10, 30, 15));
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setOpaque(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addActionListener(e -> {
            if (animalAtual != null && onBuscarAnimal != null) {
                onBuscarAnimal.accept(animalAtual);
                b.setVisible(false); // Esconde após acionar
            }
        });
        return b;
    }

    private String calcularSetor(double x, double y) {
        double cx = MapaPanel.MAP_W / 2.0;
        double cy = MapaPanel.MAP_H / 2.0;
        double ang = Math.toDegrees(Math.atan2(y - cy, x - cx));
        if (ang < 0) ang += 360;
        if (ang < 45 || ang >= 315) return "Leste";
        if (ang < 135) return "Sul";
        if (ang < 225) return "Oeste";
        return "Norte";
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Componente interno — barra de progresso estilizada
    // ══════════════════════════════════════════════════════════════════════════
    private static class BarraProgresso extends JPanel {
        private final String rotulo;
        private final String icone;
        private final Color cor;
        private int valor;

        BarraProgresso(String rotulo, String icone, Color cor, int valor) {
            this.rotulo = rotulo;
            this.icone  = icone;
            this.cor    = cor;
            this.valor  = valor;
            setBackground(COR_BG_PANEL);
            setPreferredSize(new Dimension(232, 24));
        }

        void setValor(int v) {
            this.valor = Math.max(0, Math.min(100, v));
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth(), h = getHeight();
            int barX = 70, barY = 7, barW = w - barX - 36, barH = 8;

            // Rótulo
            g.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g.setColor(COR_TEXTO_SEC);
            g.drawString(rotulo, 0, h/2 + 4);

            // Trilho
            g.setColor(new Color(50, 55, 65));
            g.fillRoundRect(barX, barY, barW, barH, 4, 4);

            // Preenchimento
            int preenchido = (int)(barW * valor / 100.0);
            if (preenchido > 0) {
                // Cor varia por nível
                Color corBarra = valor > 60 ? cor
                    : valor > 30 ? COR_AMARELO
                    : COR_VERMELHO;
                g.setColor(corBarra);
                g.fillRoundRect(barX, barY, preenchido, barH, 4, 4);
            }

            // Percentual
            g.setFont(new Font("Monospaced", Font.BOLD, 10));
            g.setColor(COR_TEXTO_SEC);
            String pct = valor + "%";
            g.drawString(pct, w - 32, h/2 + 4);
        }
    }
}
