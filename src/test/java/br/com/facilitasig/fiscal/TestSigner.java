package br.com.facilitasig.fiscal;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.crypto.dsig.CanonicalizationMethod;
import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.SignatureMethod;
import javax.xml.crypto.dsig.SignedInfo;
import javax.xml.crypto.dsig.Transform;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.KeyInfoFactory;
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec;
import javax.xml.crypto.dsig.spec.TransformParameterSpec;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Assina XMLs de teste como a SEFAZ/ADN: assinatura envelopada do elemento infNFe (ou infNFSe),
 * RSA-SHA1, C14N. A chave pública vai em KeyValue (os testes não têm certificado ICP-Brasil).
 */
final class TestSigner {

    private static KeyPair keys;

    private TestSigner() {
    }

    private static synchronized KeyPair keys() throws Exception {
        if (keys == null) {
            KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
            g.initialize(2048);
            keys = g.generateKeyPair();
        }
        return keys;
    }

    static String sign(String xml) {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            Document doc = f.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            Element target = find(doc.getDocumentElement());
            if (target == null) {
                throw new IllegalArgumentException("sem infNFe/infNFSe");
            }
            target.setIdAttribute("Id", true);
            XMLSignatureFactory fac = XMLSignatureFactory.getInstance("DOM");
            List<Transform> tr = new ArrayList<Transform>();
            tr.add(fac.newTransform(Transform.ENVELOPED, (TransformParameterSpec) null));
            tr.add(fac.newTransform(CanonicalizationMethod.INCLUSIVE, (TransformParameterSpec) null));
            Reference ref = fac.newReference("#" + target.getAttribute("Id"), fac.newDigestMethod(DigestMethod.SHA1, null), tr, null, null);
            SignedInfo si = fac.newSignedInfo(fac.newCanonicalizationMethod(CanonicalizationMethod.INCLUSIVE, (C14NMethodParameterSpec) null),
                    fac.newSignatureMethod(SignatureMethod.RSA_SHA1, null), Collections.singletonList(ref));
            KeyInfoFactory kif = fac.getKeyInfoFactory();
            KeyInfo ki = kif.newKeyInfo(Collections.singletonList(kif.newKeyValue(keys().getPublic())));
            Node parent = target.getParentNode();
            Node next = target.getNextSibling();
            DOMSignContext ctx = next == null ? new DOMSignContext(keys().getPrivate(), parent)
                    : new DOMSignContext(keys().getPrivate(), parent, next);
            fac.newXMLSignature(si, ki).sign(ctx);
            StringWriter sw = new StringWriter();
            javax.xml.transform.Transformer t = TransformerFactory.newInstance().newTransformer();
            t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            t.transform(new DOMSource(doc), new StreamResult(sw));
            return sw.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Element find(Element e) {
        String n = e.getLocalName();
        if ("infNFe".equals(n) || "infNFSe".equals(n)) {
            return e;
        }
        for (Node c = e.getFirstChild(); c != null; c = c.getNextSibling()) {
            if (c instanceof Element) {
                Element r = find((Element) c);
                if (r != null) {
                    return r;
                }
            }
        }
        return null;
    }
}
