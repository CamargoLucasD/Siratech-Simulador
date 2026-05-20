package frontend;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;

import static frontend.SimuladorFrame.*;

/**
 * PainelLog — log de eventos em tempo real no rodapé.
 *
 * Exibe duas colunas:
 *  - Esquerda: "EVENTOS RECENTES" (alertas, movimentações)
 *  - Direita:  "RESGATES ATIVOS"
 */
public class PainelLog extends JPanel {

    private static final int MAX_EVENTOS = 50;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final Deque<LogEntry> eventos = new ArrayDeque<>();
    private final JPanel painelEventos;
    private final JPanel painelResgates;

    public PainelLog() {
        setLayout(new BorderLayout());
        setBackground(COR_BG_CARD);
        setPreferredSize(new Dimension(0, 96));
        setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, COR_BORDA));

        // ── Divisão em duas colunas ────────────────────────────────────────────
        JPanel colunas = new JPanel(new GridLayout(1, 2, 1, 0));
        colunas.setBackground(COR_BG_CARD);

        // ── Coluna esquerda: eventos ───────────────────────────────────────────
        JPanel esq = new JPanel(new BorderLayout());
        esq.setBackground(COR_BG_CARD);

        JLabel titEv = new JLabel("  EVENTOS RECENTES");
        titEv.setFont(new Font("SansSerif", Font.BOLD, 10));
        titEv.setForeground(COR_TEXTO_SEC);
        titEv.setBorder(new EmptyBorder(5, 8, 4, 0));
        esq.add(titEv, BorderLayout.NORTH);

        painelEventos = new JPanel();
        painelEventos.setLayout(new BoxLayout(painelEventos, BoxLayout.Y_AXIS));
        painelEventos.setBackground(COR_BG_CARD);

        JScrollPane scrollEv = new JScrollPane(painelEventos);
        scrollEv.setBackground(COR_BG_CARD);
        scrollEv.setBorder(null);
        scrollEv.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollEv.getVerticalScrollBar().setPreferredSize(new Dimension(4, 0));
        esq.add(scrollEv, BorderLayout.CENTER);

        // ── Coluna direita: resgates ───────────────────────────────────────────
        JPanel dir = new JPanel(new BorderLayout());
        dir.setBackground(COR_BG_CARD);
        dir.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, COR_BORDA));

        JLabel titRe = new JLabel("  RESGATES ATIVOS");
        titRe.setFont(new Font("SansSerif", Font.BOLD, 10));
        titRe.setForeground(COR_TEXTO_SEC);
        titRe.setBorder(new EmptyBorder(5, 8, 4, 0));
        dir.add(titRe, BorderLayout.NORTH);

        painelResgates = new JPanel();
        painelResgates.setLayout(new BoxLayout(painelResgates, BoxLayout.Y_AXIS));
        painelResgates.setBackground(COR_BG_CARD);

        JScrollPane scrollRe = new JScrollPane(painelResgates);
        scrollRe.setBackground(COR_BG_CARD);
        scrollRe.setBorder(null);
        scrollRe.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollRe.getVerticalScrollBar().setPreferredSize(new Dimension(4, 0));
        dir.add(scrollRe, BorderLayout.CENTER);

        colunas.add(esq);
        colunas.add(dir);
        add(colunas, BorderLayout.CENTER);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // API pública
    // ══════════════════════════════════════════════════════════════════════════

    /** Evento de alerta — ponto vermelho */
    public void logAlerta(String mensagem) {
        adicionarEvento(mensagem, COR_VERMELHO);
    }

    /** Evento de sucesso — ponto verde */
    public void logSucesso(String mensagem) {
        adicionarEvento(mensagem, COR_VERDE);
    }

    /** Evento informativo — ponto amarelo */
    public void logInfo(String mensagem) {
        adicionarEvento(mensagem, COR_AMARELO);
    }

    /** Adiciona card de resgate ativo na coluna direita */
    public void adicionarResgate(String idAnimal, String nomeResgatador, double distancia) {
        SwingUtilities.invokeLater(() -> {
            JPanel card = criarCardResgate(idAnimal, nomeResgatador, distancia);
            card.setName("resgate_" + idAnimal);
            // Remove card anterior do mesmo animal se existir
            for (Component c : painelResgates.getComponents()) {
                if (("resgate_" + idAnimal).equals(c.getName())) {
                    painelResgates.remove(c);
                    break;
                }
            }
            painelResgates.add(card);
            painelResgates.revalidate();
            painelResgates.repaint();
        });
    }

    /** Remove card de resgate quando concluído */
    public void removerResgate(String idAnimal) {
        SwingUtilities.invokeLater(() -> {
            for (Component c : painelResgates.getComponents()) {
                if (("resgate_" + idAnimal).equals(c.getName())) {
                    painelResgates.remove(c);
                    break;
                }
            }
            painelResgates.revalidate();
            painelResgates.repaint();
        });
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Internos
    // ══════════════════════════════════════════════════════════════════════════
    private void adicionarEvento(String mensagem, Color cor) {
        SwingUtilities.invokeLater(() -> {
            String hora = LocalTime.now().format(FMT);
            LogEntry entry = new LogEntry(hora, mensagem, cor);
            eventos.addFirst(entry);
            if (eventos.size() > MAX_EVENTOS) eventos.removeLast();

            painelEventos.removeAll();
            int count = 0;
            for (LogEntry e : eventos) {
                painelEventos.add(criarLinhaEvento(e));
                if (++count >= 5) break; // Mostra só os 5 mais recentes
            }
            painelEventos.revalidate();
            painelEventos.repaint();
        });
    }

    private JPanel criarLinhaEvento(LogEntry entry) {
        JPanel linha = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        linha.setBackground(COR_BG_CARD);
        linha.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));

        // Ponto colorido
        JLabel ponto = new JLabel("●");
        ponto.setFont(new Font("SansSerif", Font.PLAIN, 8));
        ponto.setForeground(entry.cor);

        // Hora
        JLabel hora = new JLabel(entry.hora);
        hora.setFont(new Font("Monospaced", Font.PLAIN, 10));
        hora.setForeground(COR_TEXTO_SEC);

        // Mensagem — pode ter negrito inline (entre ** **)
        JLabel msg = new JLabel(entry.mensagem);
        msg.setFont(new Font("SansSerif", Font.PLAIN, 10));
        msg.setForeground(COR_TEXTO);

        linha.add(ponto);
        linha.add(hora);
        linha.add(msg);
        return linha;
    }

    private JPanel criarCardResgate(String idAnimal, String nomeResgatador, double distancia) {
        JPanel card = new JPanel(new BorderLayout(6, 0));
        card.setBackground(new Color(30, 35, 25));
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(50, 90, 50), 1),
            new EmptyBorder(4, 8, 4, 8)
        ));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));

        // Avatar do resgatador
        JLabel avatar = new JLabel("👤") {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0;
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(new Color(50, 90, 50));
                g.fillOval(0, 0, getWidth(), getHeight());
                super.paintComponent(g0);
            }
        };
        avatar.setPreferredSize(new Dimension(32, 32));
        avatar.setHorizontalAlignment(SwingConstants.CENTER);
        card.add(avatar, BorderLayout.WEST);

        // Info
        JPanel info = new JPanel(new GridLayout(2, 1, 0, 1));
        info.setBackground(new Color(30, 35, 25));

        JPanel linha1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        linha1.setBackground(new Color(30, 35, 25));
        JLabel labelId = new JLabel("#" + idAnimal);
        labelId.setFont(new Font("Monospaced", Font.BOLD, 12));
        labelId.setForeground(COR_ACENTO);
        linha1.add(labelId);
        info.add(linha1);

        JPanel linha2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        linha2.setBackground(new Color(30, 35, 25));
        JLabel labelNome = new JLabel(nomeResgatador);
        labelNome.setFont(new Font("SansSerif", Font.PLAIN, 10));
        labelNome.setForeground(COR_AMARELO);
        JLabel labelDist = new JLabel("A caminho do animal");
        labelDist.setFont(new Font("SansSerif", Font.PLAIN, 10));
        labelDist.setForeground(COR_TEXTO_SEC);
        linha2.add(labelNome);
        linha2.add(labelDist);
        info.add(linha2);
        card.add(info, BorderLayout.CENTER);

        // Distância
        JLabel labelDistDir = new JLabel(String.format("%.0f m", distancia));
        labelDistDir.setFont(new Font("Monospaced", Font.BOLD, 11));
        labelDistDir.setForeground(COR_VERDE);
        card.add(labelDistDir, BorderLayout.EAST);

        return card;
    }

    // ── Classe interna ─────────────────────────────────────────────────────────
    private record LogEntry(String hora, String mensagem, Color cor) {}
}
