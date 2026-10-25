package br.com.facilitasig.ref;

import java.math.BigDecimal;

/** Alíquota de ISS de um município para um código de serviço, com a vigência informada no Portal NFS-e. */
public final class IssRate {

    private final Municipality municipality;
    private final int service;
    private final String subitemDescription;
    private final BigDecimal rate;
    private final Validity validity;

    IssRate(Municipality municipality, int service, String subitemDescription, BigDecimal rate, Validity validity) {
        this.municipality = municipality;
        this.service = service;
        this.subitemDescription = subitemDescription == null ? "" : subitemDescription;
        this.rate = rate;
        this.validity = validity;
    }

    public Municipality municipality() {
        return municipality;
    }

    /** Código com 9 dígitos (item, subitem, desdobro nacional e complemento), ex.: 01.01.01.000. */
    public String serviceFormatted() {
        return IssImporter.formatService(service);
    }

    /** Código de tributação nacional da NFS-e (cTribNac, 6 dígitos), ex.: 010101. */
    public String cTribNac() {
        return String.format("%09d", service).substring(0, 6);
    }

    /** Subitem da lista da LC 116/2003, ex.: 01.01. */
    public String subitem() {
        return serviceFormatted().substring(0, 5);
    }

    public String subitemDescription() {
        return subitemDescription;
    }

    /** Alíquota em % (ex.: 2, 2.5, 5). */
    public BigDecimal rate() {
        return rate;
    }

    public String rateText() {
        return Rates.pct(rate) + "%";
    }

    public Validity validity() {
        return validity;
    }

    /** Dentro da faixa da LC 116/2003 (mínimo 2%, art. 8º-A; máximo 5%, art. 8º, II)? */
    public boolean withinLegalRange() {
        return rate.compareTo(new BigDecimal("2")) >= 0 && rate.compareTo(new BigDecimal("5")) <= 0;
    }
}
