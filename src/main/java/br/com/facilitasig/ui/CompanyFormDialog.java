package br.com.facilitasig.ui;

import br.com.facilitasig.company.BrDocs;
import br.com.facilitasig.company.CadastralSituation;
import br.com.facilitasig.company.Cnae;
import br.com.facilitasig.company.Company;
import br.com.facilitasig.company.CompanyRepository;
import br.com.facilitasig.company.CompanySize;
import br.com.facilitasig.company.CompanyValidator;
import br.com.facilitasig.company.Declared;
import br.com.facilitasig.company.EstablishmentType;
import br.com.facilitasig.company.IdType;
import br.com.facilitasig.company.IeSituation;
import br.com.facilitasig.company.RegimePeriod;
import br.com.facilitasig.company.StateRegistration;
import br.com.facilitasig.company.TaxRegime;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.time.LocalDate;
import java.util.List;

/** Formulário de cadastro de empresa (Cartão CNPJ + dados do e-Contínuo). */
public class CompanyFormDialog extends JDialog {

    private final CompanyRepository repository;
    private final Company original;
    private Company saved;

    // identificação
    private final JComboBox<IdType> idType = new JComboBox<>(IdType.values());
    private final JTextField document = new JTextField(20);
    private final JComboBox<EstablishmentType> establishment = new JComboBox<>(EstablishmentType.values());
    private final JTextField legalName = new JTextField(40);
    private final DeclaredField tradeName = new DeclaredField("Nome fantasia", 30, false, true);
    private final DeclaredField openingDate = new DeclaredField("Data de abertura", 10, true, false);
    private final JComboBox<CompanySize> size = new JComboBox<>(CompanySize.values());
    private final JTextField natureCode = new JTextField(8);
    private final JTextField natureDescription = new JTextField(30);
    private final DeclaredField group = new DeclaredField("Grupo de empresas", 20, false, true);
    private final DeclaredField nire = new DeclaredField("NIRE", 20, false, true);
    private final DeclaredField otherIds = new DeclaredField("Outros identificadores", 20, false, true);
    private final JComboBox<CadastralSituation> situation = new JComboBox<>(CadastralSituation.values());
    private final DeclaredField situationDate = new DeclaredField("Data da situação cadastral", 10, true, false);
    private final DeclaredField situationReason = new DeclaredField("Motivo da situação cadastral", 30, false, true);
    private final DeclaredField specialSituation = new DeclaredField("Situação especial", 30, false, true);
    private final DeclaredField specialSituationDate = new DeclaredField("Data da situação especial", 10, true, true);

    // endereço e contato
    private final JTextField street = new JTextField(36);
    private final JTextField number = new JTextField(8);
    private final DeclaredField complement = new DeclaredField("Complemento", 20, false, true);
    private final JTextField district = new JTextField(24);
    private final JTextField zip = new JTextField(10);
    private final JTextField city = new JTextField(24);
    private final JTextField ibge = new JTextField(8);
    private final JComboBox<String> uf = new JComboBox<>(BrDocs.ufs());
    private final DeclaredField email = new DeclaredField("E-mail", 28, false, true);
    private final DeclaredField phone = new DeclaredField("Telefone", 16, false, true);
    private final DeclaredField website = new DeclaredField("Website", 28, false, true);
    private final DeclaredField efr = new DeclaredField("Ente federativo responsável (EFR)", 20, false, true);

    // atividades
    private final JTextField mainCnaeCode = new JTextField(10);
    private final JTextField mainCnaeDescription = new JTextField(40);
    private final DefaultListModel<Object> secondaryModel = new DefaultListModel<>();
    private final JCheckBox noSecondary = new JCheckBox("Não possui atividades econômicas secundárias");

    // inscrições
    private final DefaultListModel<Object> ieModel = new DefaultListModel<>();
    private final DeclaredField municipalReg = new DeclaredField("Inscrição municipal", 16, false, true);
    private final DeclaredField municipalRegDate = new DeclaredField("Data da inscrição municipal", 10, true, true);

    // regime
    private final DefaultListModel<Object> regimeModel = new DefaultListModel<>();

    public CompanyFormDialog(Window owner, CompanyRepository repository, Company existing) {
        super(owner, existing == null ? "Nova empresa" : "Empresa: " + existing.legalName(), ModalityType.APPLICATION_MODAL);
        this.repository = repository;
        this.original = existing;
        setLayout(new BorderLayout());

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Identificação", scroll(identificationTab()));
        tabs.addTab("Endereço e contato", scroll(addressTab()));
        tabs.addTab("Atividades (CNAE)", scroll(activitiesTab()));
        tabs.addTab("Inscrições", scroll(registrationsTab()));
        tabs.addTab("Regime tributário", scroll(regimeTab()));
        add(tabs, BorderLayout.CENTER);

        JLabel hint = new JLabel("Dado que a empresa não possui: marque \"Não possui\". Nenhum campo fica em branco.");
        hint.setForeground(new Color(0x6B7785));
        hint.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 0));
        JButton save = new JButton("Salvar");
        JButton cancel = new JButton("Cancelar");
        save.addActionListener(e -> onSave());
        cancel.addActionListener(e -> dispose());
        JPanel south = new JPanel(new BorderLayout());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        buttons.add(save);
        buttons.add(cancel);
        south.add(hint, BorderLayout.WEST);
        south.add(buttons, BorderLayout.EAST);
        add(south, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(save);

        idType.addActionListener(e -> updateEstablishmentState());
        if (existing != null) {
            load(existing);
        } else {
            defaults();
        }
        updateEstablishmentState();
        setSize(860, 640);
        setLocationRelativeTo(owner);
    }

    public Company saved() {
        return saved;
    }

    // ------------------------------------------------------------------ abas

    private static JScrollPane scroll(JPanel p) {
        JScrollPane s = new JScrollPane(p);
        s.setBorder(null);
        return s;
    }

    private static JPanel form() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        return p;
    }

    private static int addRow(JPanel p, int row, String label, Component... comps) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = row;
        c.insets = new Insets(4, 4, 4, 8);
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 0;
        p.add(new JLabel(label), c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        JPanel line = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        line.setOpaque(false);
        for (Component comp : comps) {
            line.add(comp);
        }
        p.add(line, c);
        return row + 1;
    }

    private JPanel identificationTab() {
        JPanel p = form();
        int r = 0;
        r = addRow(p, r, "Tipo de identificador", idType);
        r = addRow(p, r, "CNPJ / CPF / CAEPF", document, new JLabel("Estabelecimento"), establishment);
        r = addRow(p, r, "Nome empresarial (razão social)", legalName);
        r = addRow(p, r, "Título do estabelecimento (nome fantasia)", tradeName);
        r = addRow(p, r, "Data de abertura", openingDate, new JLabel("Porte"), size);
        r = addRow(p, r, "Natureza jurídica", natureCode, natureDescription);
        r = addRow(p, r, "Grupo de empresas", group);
        r = addRow(p, r, "NIRE", nire);
        r = addRow(p, r, "Outros identificadores (CPF/CEI)", otherIds);
        r = addRow(p, r, "Situação cadastral", situation, new JLabel("Data"), situationDate);
        r = addRow(p, r, "Motivo da situação cadastral", situationReason);
        r = addRow(p, r, "Situação especial", specialSituation);
        r = addRow(p, r, "Data da situação especial", specialSituationDate);
        return p;
    }

    private JPanel addressTab() {
        JPanel p = form();
        int r = 0;
        r = addRow(p, r, "Logradouro", street, new JLabel("Número"), number);
        r = addRow(p, r, "Complemento", complement);
        r = addRow(p, r, "Bairro/distrito", district, new JLabel("CEP"), zip);
        r = addRow(p, r, "Município", city, new JLabel("UF"), uf);
        r = addRow(p, r, "Código IBGE do município", ibge);
        r = addRow(p, r, "Endereço eletrônico", email);
        r = addRow(p, r, "Telefone", phone);
        r = addRow(p, r, "Website", website);
        r = addRow(p, r, "Ente federativo responsável (EFR)", efr);
        return p;
    }

    private JPanel activitiesTab() {
        JPanel p = form();
        int r = 0;
        r = addRow(p, r, "Atividade principal", mainCnaeCode, mainCnaeDescription);
        JList<Object> list = new JList<>(secondaryModel);
        JScrollPane sp = new JScrollPane(list);
        sp.setPreferredSize(new Dimension(700, 240));
        JButton add = new JButton("Adicionar CNAE...");
        JButton remove = new JButton("Remover selecionado");
        add.addActionListener(e -> addCnae());
        remove.addActionListener(e -> {
            int i = list.getSelectedIndex();
            if (i >= 0) {
                secondaryModel.remove(i);
            }
        });
        noSecondary.addActionListener(e -> {
            boolean n = noSecondary.isSelected();
            if (n && !secondaryModel.isEmpty()) {
                n = JOptionPane.showConfirmDialog(this, "Remover os CNAEs secundários já informados?", "Não possui",
                        JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
                noSecondary.setSelected(n);
            }
            if (n) {
                secondaryModel.clear();
            }
            add.setEnabled(!n);
            remove.setEnabled(!n);
        });
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = r++;
        c.gridx = 0;
        c.gridwidth = 2;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(10, 4, 4, 4);
        p.add(noSecondary, c);
        c.gridy = r++;
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 1;
        c.weighty = 1;
        p.add(sp, c);
        JPanel b = new JPanel(new FlowLayout(FlowLayout.LEFT));
        b.add(add);
        b.add(remove);
        c.gridy = r;
        c.weighty = 0;
        p.add(b, c);
        return p;
    }

    private JPanel registrationsTab() {
        JPanel p = form();
        int r = 0;
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = r++;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(4, 4, 4, 4);
        p.add(new JLabel("Inscrições estaduais (uma por UF). Empresa sem IE: cadastre como NÃO CONTRIBUINTE."), c);
        JList<Object> list = new JList<>(ieModel);
        JScrollPane sp = new JScrollPane(list);
        sp.setPreferredSize(new Dimension(700, 130));
        c.gridy = r++;
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 1;
        c.weighty = 1;
        p.add(sp, c);
        JButton add = new JButton("Adicionar...");
        JButton edit = new JButton("Editar...");
        JButton remove = new JButton("Remover");
        add.addActionListener(e -> editIe(null, -1));
        edit.addActionListener(e -> {
            int i = list.getSelectedIndex();
            if (i >= 0) {
                editIe((StateRegistration) ieModel.get(i), i);
            }
        });
        remove.addActionListener(e -> {
            int i = list.getSelectedIndex();
            if (i >= 0) {
                ieModel.remove(i);
            }
        });
        JPanel b = new JPanel(new FlowLayout(FlowLayout.LEFT));
        b.add(add);
        b.add(edit);
        b.add(remove);
        c.gridy = r++;
        c.weighty = 0;
        p.add(b, c);
        JPanel im = form();
        int ir = 0;
        ir = addRow(im, ir, "Inscrição municipal", municipalReg);
        ir = addRow(im, ir, "Data da inscrição municipal", municipalRegDate);
        c.gridy = r;
        p.add(im, c);
        return p;
    }

    private JPanel regimeTab() {
        JPanel p = form();
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(4, 4, 4, 4);
        p.add(new JLabel("Períodos de regime tributário. O período vigente é o que não tem data final."), c);
        JList<Object> list = new JList<>(regimeModel);
        JScrollPane sp = new JScrollPane(list);
        sp.setPreferredSize(new Dimension(700, 240));
        c.gridy = 1;
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 1;
        c.weighty = 1;
        p.add(sp, c);
        JButton add = new JButton("Adicionar...");
        JButton edit = new JButton("Editar...");
        JButton remove = new JButton("Remover");
        add.addActionListener(e -> editRegime(null, -1));
        edit.addActionListener(e -> {
            int i = list.getSelectedIndex();
            if (i >= 0) {
                editRegime((RegimePeriod) regimeModel.get(i), i);
            }
        });
        remove.addActionListener(e -> {
            int i = list.getSelectedIndex();
            if (i >= 0) {
                regimeModel.remove(i);
            }
        });
        JPanel b = new JPanel(new FlowLayout(FlowLayout.LEFT));
        b.add(add);
        b.add(edit);
        b.add(remove);
        c.gridy = 2;
        c.weighty = 0;
        p.add(b, c);
        return p;
    }

    // ------------------------------------------------------------------ diálogos auxiliares

    private void addCnae() {
        JTextField code = new JTextField(10);
        JTextField desc = new JTextField(36);
        JPanel p = form();
        int r = addRow(p, 0, "Código CNAE (7 dígitos)", code);
        addRow(p, r, "Descrição", desc);
        if (JOptionPane.showConfirmDialog(this, p, "Atividade secundária", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
            Cnae n = new Cnae(code.getText(), desc.getText());
            if (n.code().length() != 7 || n.description().isEmpty()) {
                JOptionPane.showMessageDialog(this, "Informe o código com 7 dígitos e a descrição.");
                return;
            }
            secondaryModel.addElement(n);
            noSecondary.setSelected(false);
        }
    }

    private void editIe(StateRegistration current, int index) {
        JComboBox<String> ufBox = new JComboBox<>(BrDocs.ufs());
        JComboBox<IeSituation> sit = new JComboBox<>(IeSituation.values());
        JTextField num = new JTextField(16);
        DeclaredField date = new DeclaredField("Data da inscrição", 10, true, true);
        if (current != null) {
            ufBox.setSelectedItem(current.uf());
            sit.setSelectedItem(current.situation());
            num.setText(current.situation() == IeSituation.INSCRITA ? current.number() : "");
            date.setDate(current.registrationDate());
        } else {
            ufBox.setSelectedItem(String.valueOf(uf.getSelectedItem()));
            date.setDate(Declared.<LocalDate>none());
        }
        JPanel p = form();
        int r = addRow(p, 0, "UF", ufBox);
        r = addRow(p, r, "Situação", sit);
        r = addRow(p, r, "Número da IE (só se inscrita)", num);
        addRow(p, r, "Data da inscrição", date);
        if (JOptionPane.showConfirmDialog(this, p, "Inscrição estadual", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) {
            return;
        }
        try {
            IeSituation s = (IeSituation) sit.getSelectedItem();
            StateRegistration n = new StateRegistration((String) ufBox.getSelectedItem(), s, num.getText(), date.dateValue());
            if (index >= 0) {
                ieModel.set(index, n);
            } else {
                ieModel.addElement(n);
            }
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Inscrição estadual", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void editRegime(RegimePeriod current, int index) {
        JComboBox<TaxRegime> reg = new JComboBox<>(TaxRegime.values());
        DeclaredField start = new DeclaredField("Início", 10, true, false);
        DeclaredField end = new DeclaredField("Fim", 10, true, true);
        DeclaredField note = new DeclaredField("Observação", 24, false, true);
        if (current != null) {
            reg.setSelectedItem(current.regime());
            start.setDate(current.start());
            end.setDate(current.end());
            note.setValue(current.note());
        } else {
            end.setDate(Declared.<LocalDate>none());
            note.setValue(Declared.<String>none());
        }
        JPanel p = form();
        int r = addRow(p, 0, "Regime tributário", reg);
        r = addRow(p, r, "Início da vigência", start);
        r = addRow(p, r, "Fim da vigência (NÃO POSSUI = vigente)", end);
        addRow(p, r, "Observação", note);
        if (JOptionPane.showConfirmDialog(this, p, "Regime tributário", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) {
            return;
        }
        try {
            RegimePeriod n = new RegimePeriod((TaxRegime) reg.getSelectedItem(), start.requiredDate(), end.dateValue(), note.value());
            if (index >= 0) {
                regimeModel.set(index, n);
            } else {
                regimeModel.addElement(n);
            }
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Regime tributário", JOptionPane.WARNING_MESSAGE);
        }
    }

    // ------------------------------------------------------------------ carga e gravação

    private void updateEstablishmentState() {
        boolean cnpj = idType.getSelectedItem() == IdType.CNPJ;
        establishment.setEnabled(cnpj);
        if (!cnpj) {
            establishment.setSelectedItem(EstablishmentType.NAO_APLICAVEL);
        } else if (establishment.getSelectedItem() == EstablishmentType.NAO_APLICAVEL) {
            establishment.setSelectedItem(EstablishmentType.MATRIZ);
        }
    }

    private void defaults() {
        tradeName.setValue(Declared.<String>none());
        group.setValue(Declared.<String>none());
        nire.setValue(Declared.<String>none());
        otherIds.setValue(Declared.<String>none());
        situationReason.setValue(Declared.<String>none());
        specialSituation.setValue(Declared.<String>none());
        specialSituationDate.setDate(Declared.<LocalDate>none());
        complement.setValue(Declared.<String>none());
        email.setValue(Declared.<String>none());
        phone.setValue(Declared.<String>none());
        website.setValue(Declared.<String>none());
        efr.setValue(Declared.<String>none());
        municipalReg.setValue(Declared.<String>none());
        municipalRegDate.setDate(Declared.<LocalDate>none());
        size.setSelectedItem(CompanySize.DEMAIS);
        number.setText("S/N");
    }

    private void load(Company c) {
        idType.setSelectedItem(c.idType());
        document.setText(c.documentFormatted());
        establishment.setSelectedItem(c.establishment());
        legalName.setText(c.legalName());
        tradeName.setValue(c.tradeName());
        openingDate.setDate(c.openingDate());
        size.setSelectedItem(c.size());
        natureCode.setText(c.legalNatureCode());
        natureDescription.setText(c.legalNatureDescription());
        group.setValue(c.companyGroup());
        nire.setValue(c.nire());
        otherIds.setValue(c.otherIdentifiers());
        situation.setSelectedItem(c.situation());
        situationDate.setDate(c.situationDate());
        situationReason.setValue(c.situationReason());
        specialSituation.setValue(c.specialSituation());
        specialSituationDate.setDate(c.specialSituationDate());
        street.setText(c.street());
        number.setText(c.number());
        complement.setValue(c.complement());
        district.setText(c.district());
        zip.setText(BrDocs.formatCep(c.zip()));
        city.setText(c.city());
        ibge.setText(c.ibgeCode());
        uf.setSelectedItem(c.uf());
        email.setValue(c.email());
        phone.setValue(c.phone());
        website.setValue(c.website());
        efr.setValue(c.efr());
        mainCnaeCode.setText(c.mainCnae().formattedCode());
        mainCnaeDescription.setText(c.mainCnae().description());
        for (Cnae n : c.secondaryCnaes()) {
            secondaryModel.addElement(n);
        }
        noSecondary.setSelected(c.secondaryCnaes().isEmpty());
        for (StateRegistration s : c.stateRegistrations()) {
            ieModel.addElement(s);
        }
        municipalReg.setValue(c.municipalRegistration());
        municipalRegDate.setDate(c.municipalRegistrationDate());
        for (RegimePeriod p : c.regimePeriods()) {
            regimeModel.addElement(p);
        }
    }

    private Company build() {
        Company c = new Company();
        if (original != null) {
            c.setId(original.id());
        }
        c.setIdType((IdType) idType.getSelectedItem());
        c.setDocument(document.getText());
        c.setEstablishment((EstablishmentType) establishment.getSelectedItem());
        c.setLegalName(legalName.getText());
        c.setTradeName(tradeName.value());
        c.setOpeningDate(openingDate.requiredDate());
        c.setSize((CompanySize) size.getSelectedItem());
        c.setLegalNature(natureCode.getText(), natureDescription.getText());
        c.setCompanyGroup(group.value());
        c.setNire(nire.value());
        c.setOtherIdentifiers(otherIds.value());
        c.setSituation((CadastralSituation) situation.getSelectedItem());
        c.setSituationDate(situationDate.requiredDate());
        c.setSituationReason(situationReason.value());
        c.setSpecialSituation(specialSituation.value());
        c.setSpecialSituationDate(specialSituationDate.dateValue());
        c.setStreet(street.getText());
        c.setNumber(number.getText());
        c.setComplement(complement.value());
        c.setDistrict(district.getText());
        c.setZip(zip.getText());
        c.setCity(city.getText());
        c.setIbgeCode(ibge.getText());
        c.setUf((String) uf.getSelectedItem());
        c.setEmail(email.value());
        c.setPhone(phone.value());
        c.setWebsite(website.value());
        c.setEfr(efr.value());
        c.setMainCnae(new Cnae(mainCnaeCode.getText(), mainCnaeDescription.getText()));
        if (!noSecondary.isSelected() && secondaryModel.isEmpty()) {
            throw new IllegalArgumentException("Atividades secundárias: informe os CNAEs ou marque \"Não possui\".");
        }
        for (int i = 0; i < secondaryModel.size(); i++) {
            c.secondaryCnaes().add((Cnae) secondaryModel.get(i));
        }
        for (int i = 0; i < ieModel.size(); i++) {
            c.stateRegistrations().add((StateRegistration) ieModel.get(i));
        }
        c.setMunicipalRegistration(municipalReg.value());
        c.setMunicipalRegistrationDate(municipalRegDate.dateValue());
        for (int i = 0; i < regimeModel.size(); i++) {
            c.regimePeriods().add((RegimePeriod) regimeModel.get(i));
        }
        return c;
    }

    private void onSave() {
        try {
            Company c = build();
            List<String> errors = CompanyValidator.validate(c);
            if (!errors.isEmpty()) {
                StringBuilder sb = new StringBuilder("Corrija antes de salvar:\n");
                for (String e : errors) {
                    sb.append("• ").append(e).append('\n');
                }
                JOptionPane.showMessageDialog(this, sb.toString(), "Cadastro incompleto", JOptionPane.WARNING_MESSAGE);
                return;
            }
            saved = repository.save(c);
            dispose();
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Cadastro incompleto", JOptionPane.WARNING_MESSAGE);
        } catch (RuntimeException ex) {
            JOptionPane.showMessageDialog(this, "Não foi possível salvar: " + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }
}
