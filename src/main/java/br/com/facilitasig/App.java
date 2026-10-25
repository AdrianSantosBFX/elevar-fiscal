package br.com.facilitasig;

import br.com.facilitasig.ui.MainFrame;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.util.Locale;

/** Ponto de entrada do Elevar Fiscal. Compatível com Java 8 ou superior. */
public final class App {

    private App() {
    }

    public static void main(String[] args) {
        Locale.setDefault(Locale.forLanguageTag("pt-BR"));
        installLookAndFeel();
        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
        });
    }

    /**
     * Usa o FlatLaf quando a biblioteca está disponível (pasta lib ao lado do JAR ou
     * dependência do Maven); caso contrário usa o visual Nimbus, que acompanha o Java.
     */
    private static void installLookAndFeel() {
        try {
            Class.forName("com.formdev.flatlaf.FlatLightLaf").getMethod("setup").invoke(null);
            return;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // FlatLaf não encontrado: segue para o Nimbus
        }
        try {
            UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
        } catch (Exception ignored) {
            // mantém o visual padrão do Java
        }
    }
}
