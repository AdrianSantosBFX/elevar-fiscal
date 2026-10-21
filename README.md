# Facilita.sig

Aplicativo desktop em desenvolvimento para **organização e conferência de documentos fiscais**, com cadastro de empresas, importação de NF-e/NFS-e e armazenamento local em SQLite.

**Tecnologias:** Java 8 · Swing · SQLite · Maven · JUnit 5 · FlatLaf.

## Funcionalidades implementadas

- Cadastro de empresas e estabelecimentos com identificação, endereço, CNAEs, inscrições e períodos de regime tributário.
- Banco SQLite local e persistência explícita de informações não aplicáveis como `NÃO POSSUI`.
- Importação de XML de NF-e e NFS-e no leiaute nacional suportado pelo código.
- Separação entre notas de entrada e saída, detecção de duplicidades e classificação de ocorrências.
- Revisão de campos em camada separada, preservando o XML original.
- Navegação por áreas de trabalho e roteiro de apuração em etapas.

## Executar

Requisitos: **JDK 8 ou superior** e **Maven 3.9 ou superior**.

```sh
mvn clean package
java -jar target/facilita-sig.jar
```

O Maven executa os testes e gera um JAR com as dependências. Após compilar, também é possível usar `executar.bat` no Windows ou `sh executar.sh` no Linux/macOS. Na IDE, importe o `pom.xml` e execute `br.com.facilitasig.App`.

## Testes

```sh
mvn test
```

Os testes cobrem o cadastro de empresas, a persistência, a importação de documentos e o estado do roteiro de apuração.

## Organização

| Pacote | Responsabilidade |
| --- | --- |
| `company` | Cadastro de empresas e validações |
| `fiscal` | Leitura, armazenamento e revisão de documentos |
| `db` | Banco SQLite, esquema e diretório de dados |
| `core` | Áreas de trabalho, menus e roteiro de apuração |
| `ui` | Interface Swing |

## Dados de demonstração

O botão **Cadastrar empresa fictícia** cria um cadastro sintético. Os identificadores dos testes são apenas exemplos com formato e dígitos verificadores adequados; não representam uma empresa real.

O banco do usuário fica em `%APPDATA%/FacilitaSig` no Windows ou `~/.facilita-sig` nos demais sistemas. Para mudar o diretório, use `-Dfacilitasig.home=...` ou `FACILITASIG_HOME`. Bancos locais, arquivos de configuração e binários não são versionados.

## Estado do projeto

Projeto em desenvolvimento. O roteiro contém etapas e telas ainda provisórias; a estrutura de navegação não significa que todos os cálculos e obrigações estejam implementados. A importação de NFS-e está limitada ao leiaute nacional previsto no código.

O aplicativo apoia a organização e revisão de dados e não substitui validação fiscal oficial ou revisão profissional.
