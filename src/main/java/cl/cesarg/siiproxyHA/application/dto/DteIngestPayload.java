package cl.cesarg.siiproxyHA.application.dto;

import java.util.List;

public class DteIngestPayload {

    public static class Reference {
        public Integer nroLinRef;
        public String tpoDocRef;
        public String folioRef;
        public String fchRef;
        public String codRef;
        public String razonRef;
    }

    public static class Item {
        public String id;
        public Integer nroLinDet;
        public String nmbItem;
        public String dscItem;
        public Double qtyItem;
        public String unmdItem;
        public Double prcItem;
        public Long montoItem;
        public String indExe;
    }

    public String id;
    public String tenantId;
    public String tenantCode;
    public Integer tipoDte;
    public Long folio;
    public String fchEmis;
    public String receptorId;
    public List<Reference> references;
    public List<Item> items;
    public Long mntNeto;
    public Long iva;
    public Long mntTotal;

    public DteIngestPayload() {}
}
