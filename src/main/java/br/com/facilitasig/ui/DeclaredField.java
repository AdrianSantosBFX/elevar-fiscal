package br.com.facilitasig.ui;

import br.com.facilitasig.company.Declared;

import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

/**
 * Campo de texto (ou data dd/MM/aaaa) com a opção "Não possui". Marcada, a caixa grava o
 * dado como NÃO POSSUI; o campo nunca fica em branco. Com {@code allowNone=false} o campo é obrigatório.
 */
public class DeclaredField extends JPanel {

    public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);

    private final JTextField field;
    private final JCheckBox none = new JCheckBox("Não possui");
    private final boolean date;
    private final boolean allowNone;
    private final String label;

    public DeclaredField(String label, int columns, boolean date, boolean allowNone) {
        super(new BorderLayout(6, 0));
        this.label = label;
        this.date = date;
        this.allowNone = allowNone;
        this.field = new JTextField(columns);
        setOpaque(false);
        add(field, BorderLayout.CENTER);
        if (allowNone) {
            none.setOpaque(false);
            none.addActionListener(e -> refresh());
            add(none, BorderLayout.EAST);
        }
        if (date) {
            field.putClientProperty("JTextField.placeholderText", "dd/mm/aaaa");
        }
    }

    private void refresh() {
        boolean n = none.isSelected();
        field.setEnabled(!n);
        field.setText(n ? Declared.NONE_TEXT : "");
    }

    public JTextField textField() {
        return field;
    }

    // ---- texto

    public void setValue(Declared<String> value) {
        if (allowNone && !value.isPresent()) {
            none.setSelected(true);
            refresh();
        } else {
            none.setSelected(false);
            field.setEnabled(true);
            field.setText(value.orElse(""));
        }
    }

    public void setText(String text) {
        setValue(text == null || text.isEmpty() ? Declared.<String>none() : Declared.of(text));
    }

    /** Valor opcional. Vazio sem "Não possui" marcado é recusado, para nunca gravar em branco. */
    public Declared<String> value() {
        if (allowNone && none.isSelected()) {
            return Declared.none();
        }
        String t = field.getText().trim();
        if (t.isEmpty() || Declared.isNoneMarker(t)) {
            if (allowNone) {
                throw new IllegalArgumentException(label + ": preencha o campo ou marque \"Não possui\".");
            }
            throw new IllegalArgumentException(label + ": campo obrigatório.");
        }
        return Declared.of(t);
    }

    /** Texto obrigatório. */
    public String required() {
        return value().get();
    }

    // ---- data

    public void setDate(Declared<LocalDate> value) {
        setValue(value.isPresent() ? Declared.of(value.get().format(DATE)) : Declared.<String>none());
    }

    public void setDate(LocalDate value) {
        setDate(value == null ? Declared.<LocalDate>none() : Declared.of(value));
    }

    public Declared<LocalDate> dateValue() {
        Declared<String> v = value();
        if (!v.isPresent()) {
            return Declared.none();
        }
        try {
            return Declared.of(LocalDate.parse(v.get(), DATE));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(label + ": data inválida (use dd/mm/aaaa).");
        }
    }

    public LocalDate requiredDate() {
        return dateValue().get();
    }
}
