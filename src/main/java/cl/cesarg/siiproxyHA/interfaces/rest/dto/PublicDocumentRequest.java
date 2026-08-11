package cl.cesarg.siiproxyHA.interfaces.rest.dto;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class PublicDocumentRequest {

    @NotBlank
    private String type;

    @NotNull
    @Valid
    private Issuer issuer;

    @NotNull
    @Valid
    private Receiver receiver;

    @NotNull
    private LocalDate issueDate;

    @NotNull
    @Valid
    private List<Item> items = new ArrayList<>();

    @NotNull
    @Valid
    private Totals totals;

    private List<Object> references = new ArrayList<>();

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Issuer getIssuer() { return issuer; }
    public void setIssuer(Issuer issuer) { this.issuer = issuer; }
    public Receiver getReceiver() { return receiver; }
    public void setReceiver(Receiver receiver) { this.receiver = receiver; }
    public LocalDate getIssueDate() { return issueDate; }
    public void setIssueDate(LocalDate issueDate) { this.issueDate = issueDate; }
    public List<Item> getItems() { return items; }
    public void setItems(List<Item> items) { this.items = items; }
    public Totals getTotals() { return totals; }
    public void setTotals(Totals totals) { this.totals = totals; }
    public List<Object> getReferences() { return references; }
    public void setReferences(List<Object> references) { this.references = references; }

    public static class Issuer {
        @NotBlank
        @Size(max = 12)
        private String rutEnvia;

        public String getRutEnvia() { return rutEnvia; }
        public void setRutEnvia(String rutEnvia) { this.rutEnvia = rutEnvia; }
    }

    public static class Receiver {
        @NotBlank
        @Size(max = 12)
        private String rut;

        @NotBlank
        @Size(max = 100)
        private String businessName;

        @NotBlank
        @Size(max = 40)
        private String businessActivity;

        @NotBlank
        @Size(max = 70)
        private String address;

        @NotBlank
        @Size(max = 20)
        private String commune;

        @NotBlank
        @Size(max = 20)
        private String city;

        @NotBlank
        private String email;

        public String getRut() { return rut; }
        public void setRut(String rut) { this.rut = rut; }
        public String getBusinessName() { return businessName; }
        public void setBusinessName(String businessName) { this.businessName = businessName; }
        public String getBusinessActivity() { return businessActivity; }
        public void setBusinessActivity(String businessActivity) { this.businessActivity = businessActivity; }
        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }
        public String getCommune() { return commune; }
        public void setCommune(String commune) { this.commune = commune; }
        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
    }

    public static class Item {
        @NotNull
        private Integer line;

        @NotBlank
        private String name;

        private String description;

        @NotNull
        private Integer quantity;

        @NotBlank
        private String unit;

        @NotNull
        private Long unitPrice;

        @NotNull
        private Long amount;

        public Integer getLine() { return line; }
        public void setLine(Integer line) { this.line = line; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
        public String getUnit() { return unit; }
        public void setUnit(String unit) { this.unit = unit; }
        public Long getUnitPrice() { return unitPrice; }
        public void setUnitPrice(Long unitPrice) { this.unitPrice = unitPrice; }
        public Long getAmount() { return amount; }
        public void setAmount(Long amount) { this.amount = amount; }
    }

    public static class Totals {
        @NotNull
        private Long net;

        @NotNull
        private Long vat;

        @NotNull
        private Long total;

        public Long getNet() { return net; }
        public void setNet(Long net) { this.net = net; }
        public Long getVat() { return vat; }
        public void setVat(Long vat) { this.vat = vat; }
        public Long getTotal() { return total; }
        public void setTotal(Long total) { this.total = total; }
    }
}
