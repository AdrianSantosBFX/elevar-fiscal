package br.com.facilitasig.company;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Empresas que o sistema cadastra sozinho, uma única vez, na abertura (pedido do usuário: empresas reais usadas
 * como base de testes). Cada uma é marcada em {@code app_setting} ao ser cadastrada: se o usuário excluir o
 * cadastro depois, ela não volta. As demais empresas de exemplo continuam no botão "Cadastrar exemplo...".
 */
public final class CompanySeeds {

    private CompanySeeds() {
    }

    /** Empresas semeadas automaticamente. */
    static List<Company> seeds() {
        return Arrays.asList(SampleCompanies.sintepro());
    }

    static String key(Company c) {
        return "seed_company_" + c.document();
    }

    /** Cadastra as empresas ainda não semeadas; devolve as que foram cadastradas agora. */
    public static List<Company> apply(CompanyRepository repository) {
        List<Company> added = new ArrayList<Company>();
        for (Company c : seeds()) {
            if (repository.setting(key(c)) != null) {
                continue;
            }
            if (repository.findByDocument(c.idType(), c.document()) == null) {
                added.add(repository.save(c));
            }
            repository.putSetting(key(c), "1");
        }
        return added;
    }
}
