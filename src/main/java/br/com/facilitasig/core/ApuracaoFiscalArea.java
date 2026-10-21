package br.com.facilitasig.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Área de Trabalho "Apuração Fiscal": a rotina linear de conferência e
 * apuração, do cadastro da empresa até os relatórios finais.
 *
 * <p>Versão 0.0.1: apenas a estrutura (menus, botões e etapas). As descrições
 * indicam o que cada funcionalidade fará, conforme a documentação do projeto.
 */
public final class ApuracaoFiscalArea {

    private ApuracaoFiscalArea() {
    }

    private static Feature f(String id, String title, String description, String glyph, Tone tone) {
        return new Feature(id, title, title, description, glyph, tone);
    }

    private static final Feature SEP = Feature.SEPARATOR;

    public static WorkArea create() {
        // ---- Cadastros
        Feature empresa = f("cad.empresa", "Empresas e Estabelecimentos",
                "Cadastro da empresa e de seus estabelecimentos: CNPJ, inscrições estadual e municipal, "
                        + "CNAE, UF e município. É o ponto de partida da apuração: os dados daqui definem "
                        + "quais regras fiscais se aplicam.", "E", Tone.BLUE).withShortTitle("Empresa");
        Feature regime = f("cad.regime", "Regime Tributário e Parâmetros Fiscais",
                "Regime da empresa (MEI, Simples Nacional, Lucro Presumido ou Lucro Real), "
                        + "opção pelo regime regular de IBS/CBS, atividades e percentuais de presunção, "
                        + "e demais parâmetros que condicionam as regras.", "R", Tone.BLUE).withShortTitle("Parâmetros");
        Feature participantes = f("cad.participantes", "Participantes (Clientes e Fornecedores)",
                "Cadastro de participantes das operações, com CNPJ/CPF, inscrição estadual, "
                        + "município e indicador de contribuinte.", "P", Tone.BLUE);
        Feature itens = f("cad.itens", "Itens (Produtos e Serviços)",
                "Cadastro de itens com NCM, CEST, unidade e classificação tributária "
                        + "(inclusive CST e cClassTrib de IBS/CBS).", "I", Tone.BLUE);
        Feature naturezas = f("cad.naturezas", "Naturezas de Operação (CFOP)",
                "Tabela de CFOP e naturezas de operação, com o tratamento fiscal de cada uma.", "N", Tone.BLUE);

        // ---- Movimentos
        Feature importar = f("mov.importar", "Documentos Fiscais do Período",
                "Resumo das notas de entrada e de saída importadas para a empresa ativa, com acesso aos "
                        + "dois módulos de importação. A etapa só conclui sem notas com erro.", "XML", Tone.GREEN)
                .withShortTitle("Documentos");
        Feature entradas = f("mov.entradas", "Importar Notas de Entrada",
                "Importa em lote os XMLs de NF-e e NFS-e de ENTRADA (a empresa é o destinatário/tomador), "
                        + "valida cada nota e permite corrigir e revalidar.", "E", Tone.GREEN);
        Feature saidas = f("mov.saidas", "Importar Notas de Saída",
                "Importa em lote os XMLs de NF-e e NFS-e de SAÍDA (a empresa é o emitente/prestador), "
                        + "valida cada nota e permite corrigir e revalidar.", "S", Tone.GREEN);
        Feature eventos = f("mov.eventos", "Eventos de Documentos",
                "Cancelamentos, cartas de correção, manifestações do destinatário e demais eventos "
                        + "que alteram a situação de um documento.", "Ev", Tone.GREEN);

        // ---- Conferência
        Feature conferencia = f("conf.autonoma", "Conferência Autônoma de Documentos",
                "O sistema confronta cada documento com as regras fiscais do regime, da localização "
                        + "e do período, e registra o resultado de cada verificação.", "✓", Tone.ORANGE)
                .withShortTitle("Conferência");
        Feature achados = f("conf.achados", "Achados e Pendências",
                "Lista de divergências encontradas, com severidade, documento, campo e situação de tratamento.",
                "!", Tone.ORANGE);
        Feature regras = f("conf.regras", "Regras Aplicadas e Base Legal",
                "Para cada achado, a regra aplicada, a norma de origem (lei, artigo, vigência) e a "
                        + "explicação fundamentada.", "§", Tone.ORANGE);

        // ---- Impostos
        Feature icms = f("imp.icms", "ICMS",
                "Apuração do ICMS: débitos, créditos, substituição tributária, DIFAL, ajustes e CIAP.", "ICMS", Tone.PURPLE);
        Feature ipi = f("imp.ipi", "IPI",
                "Apuração do IPI de estabelecimentos industriais e equiparados.", "IPI", Tone.PURPLE);
        Feature pisCofins = f("imp.piscofins", "PIS e Cofins",
                "Apuração cumulativa e não cumulativa, créditos e regimes especiais. "
                        + "Extintos a partir de 2027, com a CBS no lugar.", "PC", Tone.PURPLE);
        Feature irpjCsll = f("imp.irpjcsll", "IRPJ e CSLL",
                "Apuração pelo lucro presumido ou real, com adicional e retenções.", "IR", Tone.PURPLE);
        Feature iss = f("imp.iss", "ISS",
                "Apuração do ISS por município, com retenções e regimes especiais.", "ISS", Tone.PURPLE);
        Feature simples = f("imp.simples", "Simples Nacional (DAS)",
                "Apuração do DAS por anexo e faixa, com segregação de receitas (ST, monofásico, exportação).",
                "DAS", Tone.PURPLE);
        Feature ibsCbs = f("imp.ibscbs", "IBS e CBS (Reforma Tributária)",
                "Apuração de IBS e CBS conforme o cronograma da reforma, com créditos e split payment.",
                "IBS", Tone.PURPLE);
        Feature apuracao = f("imp.apuracao", "Apuração Consolidada",
                "Visão única dos tributos do período por regime, com valores, créditos, ajustes e "
                        + "o caminho até os documentos que originaram cada número.", "Σ", Tone.PURPLE)
                .withShortTitle("Apuração");

        // ---- Obrigações acessórias
        Feature central = f("oa.central", "Central de Obrigações Acessórias",
                "Obrigações aplicáveis à empresa no período, conforme o regime, com prazo, situação "
                        + "e acesso à geração de cada uma.", "OA", Tone.TEAL).withShortTitle("Obrigações");
        Feature efdIcms = f("oa.efdicms", "EFD ICMS/IPI",
                "Geração e validação do SPED Fiscal a partir dos documentos conferidos.", "EFD", Tone.TEAL);
        Feature efdContrib = f("oa.efdcontrib", "EFD-Contribuições",
                "Geração e validação da escrituração de PIS e Cofins.", "EFC", Tone.TEAL);
        Feature simplesOa = f("oa.simples", "PGDAS-D, DeSTDA, DEFIS e DASN-SIMEI",
                "Declarações do Simples Nacional e do MEI.", "SN", Tone.TEAL);
        Feature ecdEcf = f("oa.ecdecf", "ECD e ECF",
                "Escrituração contábil e fiscal digitais, anuais.", "ECD", Tone.TEAL);
        Feature reinf = f("oa.reinf", "EFD-Reinf e eSocial",
                "Retenções e eventos trabalhistas e previdenciários.", "RF", Tone.TEAL);
        Feature dctf = f("oa.dctfweb", "MIT e DCTFWeb",
                "Confissão dos débitos federais do período.", "DC", Tone.TEAL);

        // ---- Consultas
        Feature docChave = f("cons.chave", "Documento por Chave de Acesso",
                "Localiza um documento pela chave de 44 dígitos e mostra seus dados, itens e achados.", "K", Tone.GRAY);
        Feature calendario = f("cons.calendario", "Calendário de Obrigações",
                "Prazos das obrigações acessórias por regime, com alertas de vencimento.", "Cal", Tone.GRAY);
        Feature legislacao = f("cons.legislacao", "Legislação e Base de Regras",
                "Consulta às regras tributárias cadastradas, com norma, vigência e condições.", "Lei", Tone.GRAY);

        // ---- Utilitários
        Feature validaChave = f("util.chave", "Validador de Chave de Acesso",
                "Confere a estrutura e o dígito verificador da chave de 44 dígitos.", "K", Tone.GRAY);
        Feature visualizaXml = f("util.xml", "Visualizador de XML",
                "Exibe um XML fiscal em árvore, com a descrição de cada campo.", "XML", Tone.GRAY);

        // ---- Configurações
        Feature cfgSistema = f("cfg.sistema", "Parâmetros do Sistema",
                "Preferências gerais do sistema.", "⚙", Tone.GRAY);
        Feature cfgPastas = f("cfg.pastas", "Pastas de Trabalho",
                "Pastas de entrada de XMLs e de saída de resultados no computador.", "Pas", Tone.GRAY);

        // ---- Relatórios
        Feature relConferencia = f("rel.conferencia", "Relatório de Conferência",
                "Resultado da conferência por documento, regra e severidade.", "RC", Tone.RED);
        Feature relApuracao = f("rel.apuracao", "Relatórios de Apuração",
                "Demonstrativos da apuração e fechamento do período.", "RA", Tone.RED)
                .withShortTitle("Relatórios");
        Feature relOa = f("rel.obrigacoes", "Controle de Obrigações Acessórias",
                "Situação e entrega das obrigações por período.", "RO", Tone.RED);
        Feature relRetidos = f("rel.retidos", "Relação de Impostos Retidos",
                "Impostos retidos nos documentos de saída e de entrada.", "RR", Tone.RED);

        // ---- Arquivos
        Feature arqImportar = f("arq.importar", "Importar Pacote de Documentos",
                "Importa um pacote (ZIP) de XMLs para o período.", "Imp", Tone.GRAY);
        Feature arqExportar = f("arq.exportar", "Exportar Resultados",
                "Exporta achados, apurações e relatórios em planilha ou PDF.", "Exp", Tone.GRAY);

        List<MenuGroup> menus = Arrays.asList(
                new MenuGroup("Cadastros", Arrays.asList(empresa, regime, SEP, participantes, itens, naturezas)),
                new MenuGroup("Movimentos", Arrays.asList(importar, SEP, entradas, saidas, eventos)),
                new MenuGroup("Conferência", Arrays.asList(conferencia, achados, regras)),
                new MenuGroup("Impostos", Arrays.asList(apuracao, SEP, icms, ipi, pisCofins, irpjCsll, iss, simples, ibsCbs)),
                new MenuGroup("Obrigações", Arrays.asList(central, SEP, efdIcms, efdContrib, simplesOa, ecdEcf, reinf, dctf)),
                new MenuGroup("Consultas", Arrays.asList(docChave, calendario, legislacao)),
                new MenuGroup("Utilitários", Arrays.asList(validaChave, visualizaXml)),
                new MenuGroup("Configurações", Arrays.asList(cfgSistema, cfgPastas)),
                new MenuGroup("Relatórios", Arrays.asList(relApuracao, relConferencia, relOa, relRetidos)),
                new MenuGroup("Arquivos", Arrays.asList(arqImportar, arqExportar)));

        List<Step> steps = Arrays.asList(
                new Step("Empresa e período",
                        "Empresa, estabelecimento e competência selecionados.", empresa),
                new Step("Parâmetros fiscais",
                        "Regime, atividades e opções que definem o conjunto de regras aplicáveis.", regime),
                new Step("Documentos do período",
                        "XMLs importados e situação de cada documento (autorizado, cancelado, denegado).", importar),
                new Step("Conferência",
                        "Todos os documentos conferidos, com achados tratados ou justificados.", conferencia),
                new Step("Apuração de impostos",
                        "Tributos do período apurados a partir dos documentos conferidos.", apuracao),
                new Step("Obrigações acessórias",
                        "Obrigações do período geradas, validadas e prontas para entrega.", central),
                new Step("Relatórios e fechamento",
                        "Demonstrativos emitidos e período encerrado.", relApuracao));

        List<Feature> toolbar = new ArrayList<>();
        for (Step step : steps) {
            toolbar.add(step.feature());
        }

        return new WorkArea("apuracao-fiscal", "Apuração Fiscal",
                "Rotina linear de conferência de documentos, apuração de impostos e obrigações acessórias.",
                menus, toolbar, steps);
    }
}
