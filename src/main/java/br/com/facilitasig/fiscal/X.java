package br.com.facilitasig.fiscal;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.List;

/** Navegação simples em XML (DOM) pelo nome local, sem depender de namespace. */
final class X {

    private X() {
    }

    static List<Element> kids(Element e) {
        List<Element> out = new ArrayList<Element>();
        if (e == null) {
            return out;
        }
        for (Node n = e.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeType() == Node.ELEMENT_NODE) {
                out.add((Element) n);
            }
        }
        return out;
    }

    static String name(Element e) {
        return e.getLocalName() != null ? e.getLocalName() : e.getNodeName();
    }

    static Element child(Element e, String name) {
        for (Element k : kids(e)) {
            if (name.equals(name(k))) {
                return k;
            }
        }
        return null;
    }

    static List<Element> children(Element e, String name) {
        List<Element> out = new ArrayList<Element>();
        for (Element k : kids(e)) {
            if (name.equals(name(k))) {
                out.add(k);
            }
        }
        return out;
    }

    static Element path(Element e, String... names) {
        Element cur = e;
        for (String n : names) {
            if (cur == null) {
                return null;
            }
            cur = child(cur, n);
        }
        return cur;
    }

    static Element first(Element e) {
        List<Element> k = kids(e);
        return k.isEmpty() ? null : k.get(0);
    }

    /** Texto do elemento no caminho, ou "NÃO POSSUI" se não existir ou estiver vazio. */
    static String text(Element e, String... names) {
        Element t = path(e, names);
        if (t == null) {
            return Fields.NA;
        }
        String s = t.getTextContent() == null ? "" : t.getTextContent().trim();
        return s.isEmpty() ? Fields.NA : s;
    }
}
