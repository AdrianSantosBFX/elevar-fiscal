package br.com.facilitasig.fiscal;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.crypto.AlgorithmMethod;
import javax.xml.crypto.KeySelector;
import javax.xml.crypto.KeySelectorException;
import javax.xml.crypto.KeySelectorResult;
import javax.xml.crypto.XMLCryptoContext;
import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.KeyValue;
import javax.xml.crypto.dsig.keyinfo.X509Data;
import java.security.Key;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

/**
 * ERR-1.6: confere a integridade das assinaturas XML (XMLDSig) do documento, usando a chave
 * pública que vem no próprio XML (certificado X.509 ou KeyValue).
 *
 * <p>Prova que o conteúdo assinado não foi alterado depois da assinatura. NÃO valida a cadeia
 * ICP-Brasil nem a revogação do certificado (isso exige fonte externa).
 */
final class SignatureCheck {

    enum Outcome { VALIDA, AUSENTE, ALTERADA, INVALIDA, NAO_VERIFICADA }

    static final class Result {
        final Outcome outcome;
        final String detail;

        Result(Outcome outcome, String detail) {
            this.outcome = outcome;
            this.detail = detail;
        }
    }

    private SignatureCheck() {
    }

    static Result verify(Document doc) {
        NodeList sigs = doc.getElementsByTagNameNS(XMLSignature.XMLNS, "Signature");
        if (sigs.getLength() == 0) {
            return new Result(Outcome.AUSENTE, "XML sem assinatura digital.");
        }
        markIds(doc.getDocumentElement());
        XMLSignatureFactory fac = XMLSignatureFactory.getInstance("DOM");
        List<String> problems = new ArrayList<String>();
        Outcome worst = Outcome.VALIDA;
        for (int i = 0; i < sigs.getLength(); i++) {
            Element sigEl = (Element) sigs.item(i);
            String where = "assinatura " + (i + 1) + " (em <" + X.name((Element) sigEl.getParentNode()) + ">)";
            try {
                DOMValidateContext ctx = new DOMValidateContext(new EmbeddedKeySelector(), sigEl);
                // NF-e usa RSA-SHA1; nas versões novas do Java a validação "segura" recusa SHA-1.
                ctx.setProperty("org.jcp.xml.dsig.secureValidation", Boolean.FALSE);
                XMLSignature sig = fac.unmarshalXMLSignature(ctx);
                if (sig.validate(ctx)) {
                    continue;
                }
                boolean refsOk = true;
                for (Object o : sig.getSignedInfo().getReferences()) {
                    if (!((Reference) o).validate(ctx)) {
                        refsOk = false;
                    }
                }
                if (!refsOk) {
                    problems.add(where + ": o conteúdo assinado foi alterado depois da assinatura (resumo não confere)");
                    worst = Outcome.ALTERADA;
                } else {
                    problems.add(where + ": valor da assinatura não confere com o certificado");
                    if (worst != Outcome.ALTERADA) {
                        worst = Outcome.INVALIDA;
                    }
                }
            } catch (Exception e) {
                problems.add(where + ": não foi possível verificar (" + e.getClass().getSimpleName()
                        + (e.getMessage() == null ? "" : ": " + e.getMessage()) + ")");
                if (worst == Outcome.VALIDA) {
                    worst = Outcome.NAO_VERIFICADA;
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        for (String p : problems) {
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(p);
        }
        return new Result(worst, sb.toString());
    }

    /** Marca os atributos "Id" como identificadores, para as referências "#Id" resolverem. */
    private static void markIds(Element e) {
        if (e.hasAttribute("Id")) {
            e.setIdAttribute("Id", true);
        }
        for (Element k : X.kids(e)) {
            markIds(k);
        }
    }

    /** Usa a chave pública do certificado ou do KeyValue que acompanham a assinatura. */
    private static final class EmbeddedKeySelector extends KeySelector {
        @Override
        public KeySelectorResult select(KeyInfo keyInfo, Purpose purpose, AlgorithmMethod method, XMLCryptoContext context)
                throws KeySelectorException {
            if (keyInfo == null) {
                throw new KeySelectorException("assinatura sem KeyInfo");
            }
            for (Object o : keyInfo.getContent()) {
                if (o instanceof X509Data) {
                    for (Object c : ((X509Data) o).getContent()) {
                        if (c instanceof X509Certificate) {
                            return result(((X509Certificate) c).getPublicKey());
                        }
                    }
                } else if (o instanceof KeyValue) {
                    try {
                        return result(((KeyValue) o).getPublicKey());
                    } catch (java.security.KeyException e) {
                        throw new KeySelectorException(e);
                    }
                }
            }
            throw new KeySelectorException("certificado não encontrado na assinatura");
        }

        private static KeySelectorResult result(final PublicKey key) {
            return new KeySelectorResult() {
                @Override
                public Key getKey() {
                    return key;
                }
            };
        }
    }
}
