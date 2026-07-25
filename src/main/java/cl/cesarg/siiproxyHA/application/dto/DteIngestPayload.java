package cl.cesarg.siiproxyHA.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

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
        public Integer nroLinDet;
        public String nmbItem;
        public String dscItem;
        public Double qtyItem;
        public String unmdItem;
        public Double prcItem;
        public Long montoItem;
        public String indExe;
    }

    public static class Receptor {
        @NotBlank
        @Size(max = 12)
        public String rutReceptor;

        @NotBlank
        @Size(max = 100)
        public String razonSocial;

        @NotBlank
        @Size(max = 40)
        public String giro;

        @NotBlank
        @Email
        @Size(max = 80)
        public String email;

        @NotBlank
        @Size(max = 20)
        public String telefono;

        @NotBlank
        @Size(max = 70)
        public String direccion;

        @NotBlank
        @Size(max = 20)
        public String comuna;

        @NotBlank
        @Size(max = 20)
        public String ciudad;
    }

    public String id;

    @NotBlank
    public String tenantId;

    public String tenantCode;

    @NotBlank
    @Size(max = 12)
    public String rutEnvia;

    public Integer tipoDte;
    public Long folio;

    @NotBlank
    public String fchEmis;

    @Valid
    @NotNull
    public Receptor receptor;

    public List<Reference> references;
    public List<Item> items;
    public Long mntNeto;
    public Long iva;

    @NotNull
    public Long mntTotal;

    public DteIngestPayload() {}
}
