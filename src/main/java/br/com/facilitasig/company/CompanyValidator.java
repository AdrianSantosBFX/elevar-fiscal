package br.com.facilitasig.company;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Validação do cadastro. Devolve as mensagens de erro (lista vazia = cadastro válido). */
public final class CompanyValidator {

    private CompanyValidator() {
    }

    public static List<String> validate(Company c) {
        List<String> e = new ArrayList<String>();
        // identificação
        switch (c.idType()) {
            case CNPJ:
                if (!BrDocs.isValidCnpj(c.document())) {
                    e.add("CNPJ inválido (confira os dígitos verificadores).");
                } else {
                    boolean headquarters = "0001".equals(BrDocs.cnpjOrder(c.document()));
                    if (c.establishment() == EstablishmentType.NAO_APLICAVEL) {
                        e.add("Informe se o estabelecimento é MATRIZ ou FILIAL.");
                    } else if (headquarters && c.establishment() == EstablishmentType.FILIAL) {
                        e.add("CNPJ com ordem 0001 é MATRIZ, mas o cadastro diz FILIAL.");
                    } else if (!headquarters && c.establishment() == EstablishmentType.MATRIZ) {
                        e.add("CNPJ com ordem diferente de 0001 é FILIAL, mas o cadastro diz MATRIZ.");
                    }
                }
                break;
            case CPF:
                if (!BrDocs.isValidCpf(c.document())) {
                    e.add("CPF inválido.");
                }
                break;
            default:
                if (c.document().length() != 14) {
                    e.add("CAEPF deve ter 14 dígitos.");
                }
        }
        if (c.legalName().isEmpty()) {
            e.add("Informe o nome empresarial (razão social).");
        }
        if (c.openingDate() == null) {
            e.add("Informe a data de abertura.");
        }
        if (c.legalNatureCode().isEmpty() || c.legalNatureDescription().isEmpty()) {
            e.add("Informe o código e a descrição da natureza jurídica.");
        }
        if (c.situationDate() == null) {
            e.add("Informe a data da situação cadastral.");
        }
        if (c.situation() != CadastralSituation.ATIVA && !c.situationReason().isPresent()) {
            e.add("Situação diferente de ATIVA: informe o motivo da situação cadastral.");
        }
        // endereço
        if (c.street().isEmpty()) e.add("Informe o logradouro.");
        if (c.number().isEmpty()) e.add("Informe o número (use S/N se não houver).");
        if (c.district().isEmpty()) e.add("Informe o bairro/distrito.");
        if (!BrDocs.isValidCep(c.zip())) e.add("CEP deve ter 8 dígitos.");
        if (c.city().isEmpty()) e.add("Informe o município.");
        if (!BrDocs.isValidUf(c.uf())) {
            e.add("UF inválida.");
        } else if (!BrDocs.isValidIbge(c.ibgeCode(), c.uf())) {
            e.add("Código IBGE do município deve ter 7 dígitos e começar pelo código da UF.");
        }
        if (c.email().isPresent() && !c.email().get().matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            e.add("E-mail inválido.");
        }
        // atividades
        if (c.mainCnae() == null || BrDocs.digits(c.mainCnae().code()).length() != 7 || c.mainCnae().description().isEmpty()) {
            e.add("Informe a atividade econômica principal (CNAE de 7 dígitos e descrição).");
        }
        Set<String> seen = new HashSet<String>();
        if (c.mainCnae() != null) {
            seen.add(c.mainCnae().code());
        }
        for (Cnae n : c.secondaryCnaes()) {
            if (n.code().length() != 7 || n.description().isEmpty()) {
                e.add("CNAE secundário inválido: " + n.code());
            } else if (!seen.add(n.code())) {
                e.add("CNAE repetido: " + n.formattedCode());
            }
        }
        // inscrições
        if (c.stateRegistrations().isEmpty()) {
            e.add("Informe a situação da inscrição estadual (use NÃO CONTRIBUINTE se a empresa não possui).");
        }
        Set<String> ufs = new HashSet<String>();
        for (StateRegistration s : c.stateRegistrations()) {
            if (!BrDocs.isValidUf(s.uf())) {
                e.add("Inscrição estadual com UF inválida.");
            } else if (!ufs.add(s.uf())) {
                e.add("Mais de uma inscrição estadual para a UF " + s.uf() + ".");
            }
            if (s.situation() == IeSituation.INSCRITA && s.number().length() < 2) {
                e.add("Inscrição estadual INSCRITA exige o número.");
            }
        }
        // regimes
        if (c.regimePeriods().isEmpty()) {
            e.add("Informe ao menos um período de regime tributário.");
        }
        int open = 0;
        List<RegimePeriod> sorted = new ArrayList<RegimePeriod>(c.regimePeriods());
        java.util.Collections.sort(sorted, new java.util.Comparator<RegimePeriod>() {
            public int compare(RegimePeriod a, RegimePeriod b) {
                return a.start().compareTo(b.start());
            }
        });
        LocalDate previousEnd = null;
        for (RegimePeriod p : sorted) {
            if (p.isOpen()) {
                open++;
            } else if (p.end().get().isBefore(p.start())) {
                e.add("Regime " + p.regime() + ": data final anterior à inicial.");
            }
            if (previousEnd != null && !p.start().isAfter(previousEnd)) {
                e.add("Períodos de regime sobrepostos em " + p.start() + ".");
            }
            previousEnd = p.isOpen() ? LocalDate.MAX : p.end().get();
        }
        if (open > 1) {
            e.add("Só pode haver um regime em aberto (sem data final).");
        }
        return e;
    }
}
