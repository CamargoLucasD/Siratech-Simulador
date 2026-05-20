package frontend;

import backend.*;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

import static frontend.SimuladorFrame.*;

/**
 * TelaAnimais — tabela scrollável com todos os animais.
 * Clicar em uma linha fecha o overlay e centraliza o mapa no animal.
 */
public class TelaAnimais extends JPanel implements OverlayPanel.TelaBase {

    private final SimulacaoEngine engine;
    private final Runnable onFecharOverlay;
    private final java.util.function.BiConsumer<Double, Double> onNavegar;
    private final java.util.function.Consumer<AnimalSimulado>   onSelecionarAnimal;

    private final AnimalTableModel modelo;
    private final JTable tabela;
    private final JLabel labelContagem;
    private final JTextField campoBusca;
    private Timer atualizadorTimer;

    public TelaAnimais(SimulacaoEngine engine,
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

        // ── Barra de busca + contagem ──────────────────────────────────────────
        JPanel topBar = new JPanel(new BorderLayout(12, 0));
        topBar.setOpaque(false);

        campoBusca = new JTextField();
        campoBusca.setFont(new Font("SansSerif", Font.PLAIN, 12));
        campoBusca.setForeground(COR_TEXTO);
        campoBusca.setBackground(COR_BG_DARK);
        campoBusca.setCaretColor(COR_ACENTO);
        campoBusca.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(COR_BORDA),
            BorderFactory.createEmptyBorder(6, 10, 6, 10)
        ));
        campoBusca.putClientProperty("JTextField.placeholderText", "Buscar por ID ou nome...");
        campoBusca.addKeyListener(new KeyAdapter() {
            @Override public void keyReleased(KeyEvent e) { filtrar(); }
        });

        labelContagem = new JLabel("0 animais");
        labelContagem.setFont(new Font("SansSerif", Font.PLAIN, 11));
        labelContagem.setForeground(COR_TEXTO_SEC);
        labelContagem.setPreferredSize(new Dimension(100, 30));

        topBar.add(campoBusca,    BorderLayout.CENTER);
        topBar.add(labelContagem, BorderLayout.EAST);
        add(topBar, BorderLayout.NORTH);

        // ── Tabela ─────────────────────────────────────────────────────────────
        modelo = new AnimalTableModel();
        tabela = new JTable(modelo) {
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int row, int col) {
                Component c = super.prepareRenderer(renderer, row, col);
                AnimalSimulado a = modelo.getAnimal(row);
                if (a != null) {
                    boolean fora = a.getEstado() == EstadoAnimal.FORA_DA_AREA;
                    if (isRowSelected(row)) {
                        c.setBackground(new Color(COR_ACENTO.getRed(), COR_ACENTO.getGreen(), COR_ACENTO.getBlue(), 50));
                    } else if (fora) {
                        c.setBackground(new Color(COR_VERMELHO.getRed(), COR_VERMELHO.getGreen(), COR_VERMELHO.getBlue(), 20));
                    } else {
                        c.setBackground(row % 2 == 0 ? COR_BG_DARK : COR_BG_CARD);
                    }
                }
                c.setForeground(COR_TEXTO);
                return c;
            }
        };
        tabela.setFont(new Font("SansSerif", Font.PLAIN, 12));
        tabela.setForeground(COR_TEXTO);
        tabela.setBackground(COR_BG_DARK);
        tabela.setGridColor(COR_BORDA);
        tabela.setRowHeight(32);
        tabela.setSelectionBackground(new Color(COR_ACENTO.getRed(), COR_ACENTO.getGreen(), COR_ACENTO.getBlue(), 60));
        tabela.setSelectionForeground(COR_TEXTO);
        tabela.setShowVerticalLines(false);
        tabela.setFocusable(false);
        tabela.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 11));
        tabela.getTableHeader().setForeground(COR_TEXTO_SEC);
        tabela.getTableHeader().setBackground(COR_BG_CARD);
        tabela.getTableHeader().setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, COR_BORDA));

        // Larguras das colunas
        tabela.getColumnModel().getColumn(0).setPreferredWidth(60);   // ID
        tabela.getColumnModel().getColumn(1).setPreferredWidth(130);  // Nome
        tabela.getColumnModel().getColumn(2).setPreferredWidth(140);  // Estado
        tabela.getColumnModel().getColumn(3).setPreferredWidth(80);   // Fome
        tabela.getColumnModel().getColumn(4).setPreferredWidth(80);   // Sede
        tabela.getColumnModel().getColumn(5).setPreferredWidth(80);   // Energia
        tabela.getColumnModel().getColumn(6).setPreferredWidth(100);  // Posição

        // Clique duplo → navega para o animal
        tabela.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = tabela.rowAtPoint(e.getPoint());
                    if (row >= 0) irParaAnimal(modelo.getAnimal(row));
                }
            }
        });

        JScrollPane scroll = new JScrollPane(tabela);
        scroll.setBackground(COR_BG_DARK);
        scroll.setBorder(BorderFactory.createLineBorder(COR_BORDA));
        scroll.getViewport().setBackground(COR_BG_DARK);
        scroll.getVerticalScrollBar().setPreferredSize(new Dimension(6, 0));
        add(scroll, BorderLayout.CENTER);

        // ── Rodapé com dica ───────────────────────────────────────────────────
        JPanel rodape = new JPanel(new BorderLayout());
        rodape.setOpaque(false);
        JLabel dica = new JLabel("Clique duplo em um animal para centralizá-lo no mapa");
        dica.setFont(new Font("SansSerif", Font.ITALIC, 10));
        dica.setForeground(COR_TEXTO_SEC);
        rodape.add(dica, BorderLayout.WEST);

        JButton btnIrParaSelecionado = criarBotao("↗  Ir para selecionado");
        btnIrParaSelecionado.addActionListener(e -> {
            int row = tabela.getSelectedRow();
            if (row >= 0) irParaAnimal(modelo.getAnimal(row));
        });
        rodape.add(btnIrParaSelecionado, BorderLayout.EAST);
        add(rodape, BorderLayout.SOUTH);

        atualizar();
    }

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

    private void atualizar() {
        modelo.setAnimais(engine.getAnimais());
        filtrar();
    }

    private void filtrar() {
        String termo = campoBusca.getText().trim().toLowerCase();
        modelo.setFiltro(termo);
        labelContagem.setText(modelo.getRowCount() + " animais");
    }

    private void irParaAnimal(AnimalSimulado a) {
        if (a == null) return;
        onSelecionarAnimal.accept(a);
        onNavegar.accept(a.getX(), a.getY());
        onFecharOverlay.run();
    }

    private JButton criarBotao(String texto) {
        JButton b = new JButton(texto) {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0;
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color cor = getModel().isRollover() ? COR_ACENTO.darker() : COR_ACENTO;
                g.setColor(new Color(cor.getRed(), cor.getGreen(), cor.getBlue(), 30));
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
                g.setColor(COR_ACENTO);
                g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
                super.paintComponent(g0);
            }
        };
        b.setFont(new Font("SansSerif", Font.PLAIN, 11));
        b.setForeground(COR_ACENTO);
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setPreferredSize(new Dimension(160, 28));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TableModel
    // ══════════════════════════════════════════════════════════════════════════

    private static class AnimalTableModel extends AbstractTableModel {
        private static final String[] COLUNAS = {"ID", "Nome", "Estado", "Fome", "Sede", "Energia", "Posição"};
        private List<AnimalSimulado> todos = new java.util.ArrayList<>();
        private List<AnimalSimulado> filtrados = new java.util.ArrayList<>();

        void setAnimais(List<AnimalSimulado> animais) {
            this.todos = new java.util.ArrayList<>(animais);
            aplicarFiltroAtual();
        }

        private String filtroAtual = "";
        void setFiltro(String f) {
            this.filtroAtual = f;
            aplicarFiltroAtual();
        }

        private void aplicarFiltroAtual() {
            if (filtroAtual.isEmpty()) {
                filtrados = new java.util.ArrayList<>(todos);
            } else {
                filtrados = todos.stream()
                    .filter(a -> {
                        String id   = String.format("%03d", a.getAnimal().getId());
                        String nome = a.getAnimal().getNome() != null ? a.getAnimal().getNome().toLowerCase() : "";
                        return id.contains(filtroAtual) || nome.contains(filtroAtual);
                    })
                    .toList();
            }
            fireTableDataChanged();
        }

        AnimalSimulado getAnimal(int row) {
            return (row >= 0 && row < filtrados.size()) ? filtrados.get(row) : null;
        }

        @Override public int getRowCount()    { return filtrados.size(); }
        @Override public int getColumnCount() { return COLUNAS.length; }
        @Override public String getColumnName(int col) { return COLUNAS[col]; }

        @Override
        public Object getValueAt(int row, int col) {
            AnimalSimulado a = filtrados.get(row);
            return switch (col) {
                case 0 -> String.format("#%03d", a.getAnimal().getId());
                case 1 -> a.getAnimal().getNome() != null ? a.getAnimal().getNome() : "—";
                case 2 -> a.getEstado().toString().replace("_", " ");
                case 3 -> String.format("%.0f%%", a.getFome());
                case 4 -> String.format("%.0f%%", a.getSede());
                case 5 -> String.format("%.0f%%", a.getEnergia());
                case 6 -> String.format("%.0f, %.0f", a.getX(), a.getY());
                default -> "—";
            };
        }
    }
}
