# Histórico de alterações — Elevar Fiscal

## v0.1.5 — Revisão de nomes de banco (23/10/2026)

- Banco principal: `elevar-fiscal.db` (era `facilita-sig.db`).
- Banco de referências: `elevar-fiscal-ref.db` (era `facilita-sig-ref.db`).
- Código compilado atualizado para utilizar os novos nomes.
- Se os arquivos novos ainda não existirem, o aplicativo copia os bancos antigos para os novos nomes, mantendo os originais intactos.
- Os diretórios de dados anteriores permanecem por compatibilidade; não é criada uma segunda instalação vazia.
- Incluída uma cópia do banco enviado pelo usuário em `banco-fornecido/elevar-fiscal.db`.
- Estrutura e registros do banco fornecido não foram modificados.

## v0.1.5 — Alteração de marca (23/10/2026)

- Nome público do aplicativo: **Elevar Fiscal**, produto da **Elevar Labs**.
- Renomeado o JAR de distribuição e o artefato Maven para `elevar-fiscal`.
- Atualizados os nomes no título das janelas, na caixa Sobre, no ícone EF, nos scripts de inicialização e no README.
- Preservados os pacotes Java `br.com.facilitasig`, os bancos `facilita-sig.db`/`facilita-sig-ref.db` e o caminho legado para evitar perda de cadastros na atualização.
- Adicionados os aliases de configuração `-Delevarfiscal.home` e `ELEVAR_FISCAL_HOME`.
- Regras fiscais e esquemas do banco **sem alterações**.

## v0.1.5 — Versão técnica original Facilita.sig

- Obrigações acessórias LP/LR, tabelas de referência, importação e análise de NF-e/NFS-e, cadastro de empresas e demais funcionalidades conforme documentação em `docs/`.
