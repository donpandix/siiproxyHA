package cl.cesarg.siiproxyHA.application.service;

import cl.cesarg.siiproxyHA.application.dto.DteRequest;
import cl.cesarg.siiproxyHA.domain.model.Dte;
import cl.cesarg.siiproxyHA.domain.model.DocumentMetadata;
import cl.cesarg.siiproxyHA.domain.model.DocumentStatus;
import cl.cesarg.siiproxyHA.domain.model.Tenant;
import cl.cesarg.siiproxyHA.domain.port.DocumentoRepositoryPort;
import cl.cesarg.siiproxyHA.domain.port.StoragePort;
import cl.cesarg.siiproxyHA.infrastructure.persistence.DteRepository;
import cl.cesarg.siiproxyHA.infrastructure.persistence.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Service
public class DteServiceImpl implements DteService {

    private final DocumentoRepositoryPort documentoRepository;
    private final StoragePort storagePort;
    private final DteRepository dteRepository;
    private final TenantRepository tenantRepository;
    private final CafService cafService;

    public DteServiceImpl(DocumentoRepositoryPort documentoRepository,
                          StoragePort storagePort,
                          DteRepository dteRepository,
                          TenantRepository tenantRepository,
                          CafService cafService) {
        this.documentoRepository = documentoRepository;
        this.storagePort = storagePort;
        this.dteRepository = dteRepository;
        this.tenantRepository = tenantRepository;
        this.cafService = cafService;
    }

    @Override
    @Transactional
    public DocumentMetadata ingest(DteRequest request) throws Exception {
        // Idempotency: if documentId present and exists, return existing
        if (request.getDocumentId() != null) {
            var existing = documentoRepository.findByDocumentId(request.getDocumentId());
            if (existing.isPresent()) return existing.get();
        }

        // Basic business validation (more rules in domain)
        if (request.getEmitterRUT() == null || request.getReceiverRUT() == null) {
            throw new IllegalArgumentException("emitterRUT and receiverRUT are required");
        }

        String documentId = request.getDocumentId();
        Dte dte = null;

        if (Boolean.TRUE.equals(request.getAssignFolio())) {
            Tenant tenant = tenantRepository.findByRutEmisor(request.getEmitterRUT())
                    .orElseThrow(() -> new IllegalArgumentException("Tenant not found for emitterRUT"));

            dte = new Dte();
            dte.setId(UUID.randomUUID());
            dte.setTenant(tenant);
            dte.setTipoDte(request.getTipoDte() == null ? 33 : request.getTipoDte());
            dte.setFolio(0L);
            dte.setFchEmis(LocalDate.now());
            dte.setRutRecep(request.getReceiverRUT());
            dte.setRznSocRecep(request.getReceiverRUT());
            dte.setMntTotal(0L);
            dte.setCreatedAt(Instant.now());
            dte.setUpdatedAt(Instant.now());
            dte = dteRepository.save(dte);

            String assignmentRequestId = request.getRequestId();
            if (assignmentRequestId == null || assignmentRequestId.isBlank()) {
                assignmentRequestId = request.getDocumentId();
            }

            cafService.assignFolioToDte(
                    tenant.getId(),
                    dte.getId(),
                    request.getPuntoVenta(),
                    assignmentRequestId,
                    request.getAssignedTo()
            );

            dte = dteRepository.findById(dte.getId())
                    .orElseThrow(() -> new IllegalStateException("DTE not found after folio assignment"));

            if (documentId == null || documentId.isBlank()) {
                documentId = dte.getId().toString();
            }
        }

        DocumentMetadata meta = new DocumentMetadata(documentId, DocumentStatus.RECEIVED);
        if (dte != null && dte.getFolio() != null) {
            meta.setFolio(String.valueOf(dte.getFolio()));
        }

        // If XML provided, store it in storage. Otherwise, generate XML from DTE with assigned folio.
        if (request.getXmlBase64() != null && !request.getXmlBase64().isBlank()) {
            byte[] bytes = Base64.getDecoder().decode(request.getXmlBase64());
            String key = String.format("dte/%s.xml", documentId == null || documentId.isBlank() ? UUID.randomUUID() : documentId);
            try (var in = new ByteArrayInputStream(bytes)) {
                String objectKey = storagePort.store(key, in, bytes.length, "application/xml");
                meta.setObjectKey(objectKey);
                meta.setStatus(DocumentStatus.STORED);
            }
        } else if (dte != null) {
            String xml = generateXmlFromDte(dte);
            byte[] bytes = xml.getBytes(StandardCharsets.UTF_8);
            String key = String.format("dte/%s.xml", documentId == null || documentId.isBlank() ? dte.getId() : documentId);
            try (var in = new ByteArrayInputStream(bytes)) {
                String objectKey = storagePort.store(key, in, bytes.length, "application/xml");
                meta.setObjectKey(objectKey);
                meta.setStatus(DocumentStatus.STORED);
            }
        }

        if (meta.getDocumentId() == null || meta.getDocumentId().isBlank()) {
            meta.setDocumentId(UUID.randomUUID().toString());
        }

        meta = documentoRepository.save(meta);

        return meta;
    }

    @Override
    public DocumentMetadata getStatus(String documentId) throws Exception {
        Optional<DocumentMetadata> meta = documentoRepository.findByDocumentId(documentId);
        return meta.orElseThrow(() -> new IllegalArgumentException("Document not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public cl.cesarg.siiproxyHA.application.dto.DteXmlResponse getXml(String documentId, boolean presigned, int expiryMinutes) throws Exception {
        Optional<DocumentMetadata> metaOpt = documentoRepository.findByDocumentId(documentId);
        if (metaOpt.isEmpty()) {
            Optional<Dte> dteOpt = findDteByIdIfUuid(documentId);
            if (dteOpt.isPresent()) {
                String xml = generateXmlFromDte(dteOpt.get());
                String xmlBase64 = Base64.getEncoder().encodeToString(xml.getBytes(StandardCharsets.UTF_8));
                return new cl.cesarg.siiproxyHA.application.dto.DteXmlResponse(documentId, xmlBase64, null);
            }
            throw new IllegalArgumentException("Document not found");
        }

        DocumentMetadata meta = metaOpt.get();

        String objectKey = meta.getObjectKey();
        if (objectKey == null) {
            Optional<Dte> dteOpt = findDteByIdIfUuid(documentId);
            if (dteOpt.isPresent()) {
                String xml = generateXmlFromDte(dteOpt.get());
                String xmlBase64 = Base64.getEncoder().encodeToString(xml.getBytes(StandardCharsets.UTF_8));
                return new cl.cesarg.siiproxyHA.application.dto.DteXmlResponse(documentId, xmlBase64, null);
            }
            throw new IllegalArgumentException("No object stored for document");
        }

        if (presigned) {
            String url = storagePort.presignedUrl(objectKey, expiryMinutes);
            return new cl.cesarg.siiproxyHA.application.dto.DteXmlResponse(documentId, null, url);
        } else {
            byte[] data = storagePort.get(objectKey);
            String xmlBase64 = Base64.getEncoder().encodeToString(data);
            return new cl.cesarg.siiproxyHA.application.dto.DteXmlResponse(documentId, xmlBase64, null);
        }
    }

    private Optional<Dte> findDteByIdIfUuid(String documentId) {
        try {
            return dteRepository.findById(UUID.fromString(documentId));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private String generateXmlFromDte(Dte dte) {
        LocalDate fchEmis = dte.getFchEmis();
        String fchEmisStr = fchEmis == null ? "" : fchEmis.toString();

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n");
        xml.append("<DTE xmlns=\"http://www.sii.cl/SiiDte\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" version=\"1.0\">\n");
        xml.append("  <Documento ID=\"DTE-").append(nullSafe(dte.getFolio())).append("\">\n");
        xml.append("    <Encabezado>\n");
        xml.append("      <IdDoc>\n");
        xml.append("        <TipoDTE>").append(nullSafe(dte.getTipoDte())).append("</TipoDTE>\n");
        xml.append("        <Folio>").append(nullSafe(dte.getFolio())).append("</Folio>\n");
        xml.append("        <FchEmis>").append(escapeXml(fchEmisStr)).append("</FchEmis>\n");
        xml.append("      </IdDoc>\n");
        xml.append("      <Emisor>\n");
        xml.append("        <RUTEmisor>").append(escapeXml(dte.getTenant() == null ? "" : dte.getTenant().getRutEmisor())).append("</RUTEmisor>\n");
        xml.append("        <RznSoc>").append(escapeXml(dte.getTenant() == null ? "" : dte.getTenant().getRazonSocial())).append("</RznSoc>\n");
        xml.append("        <GiroEmis>").append(escapeXml(dte.getTenant() == null ? "" : dte.getTenant().getGiro())).append("</GiroEmis>\n");
        xml.append("        <Acteco>").append(escapeXml(dte.getTenant() == null ? "" : dte.getTenant().getActeco())).append("</Acteco>\n");
        xml.append("        <DirOrigen>").append(escapeXml(dte.getTenant() == null ? "" : dte.getTenant().getDireccion())).append("</DirOrigen>\n");
        xml.append("        <CmnaOrigen>").append(escapeXml(dte.getTenant() == null ? "" : dte.getTenant().getComuna())).append("</CmnaOrigen>\n");
        xml.append("      </Emisor>\n");
        xml.append("      <Receptor>\n");
        xml.append("        <RUTRecep>").append(escapeXml(dte.getRutRecep())).append("</RUTRecep>\n");
        xml.append("        <RznSocRecep>").append(escapeXml(dte.getRznSocRecep())).append("</RznSocRecep>\n");
        xml.append("        <GiroRecep>").append(escapeXml(dte.getReceptor() == null ? "" : dte.getReceptor().getGiro())).append("</GiroRecep>\n");
        xml.append("        <DirRecep>").append(escapeXml(dte.getDirRecep() != null ? dte.getDirRecep() : (dte.getReceptor() == null ? "" : dte.getReceptor().getDireccion()))).append("</DirRecep>\n");
        xml.append("        <CmnaRecep>").append(escapeXml(dte.getCmnaRecep() != null ? dte.getCmnaRecep() : (dte.getReceptor() == null ? "" : dte.getReceptor().getComuna()))).append("</CmnaRecep>\n");
        xml.append("      </Receptor>\n");
        xml.append("      <Totales>\n");
        xml.append("        <MntNeto>").append(nullSafe(dte.getMntNeto())).append("</MntNeto>\n");
        xml.append("        <TasaIVA>").append(dte.getTasaIva() == null ? "19" : normalizeNumber(dte.getTasaIva())).append("</TasaIVA>\n");
        xml.append("        <IVA>").append(nullSafe(dte.getIva())).append("</IVA>\n");
        xml.append("        <MntTotal>").append(nullSafe(dte.getMntTotal())).append("</MntTotal>\n");
        xml.append("      </Totales>\n");
        xml.append("    </Encabezado>\n");

        if (dte.getItems() != null) {
            dte.getItems().forEach(item -> {
                xml.append("    <Detalle>\n");
                xml.append("      <NroLinDet>").append(nullSafe(item.getNroLinDet())).append("</NroLinDet>\n");
                xml.append("      <NmbItem>").append(escapeXml(item.getNmbItem())).append("</NmbItem>\n");
                if (item.getDscItem() != null) {
                    xml.append("      <DscItem>").append(escapeXml(item.getDscItem())).append("</DscItem>\n");
                }
                if (item.getQtyItem() != null) {
                    xml.append("      <QtyItem>").append(normalizeNumber(BigDecimal.valueOf(item.getQtyItem()))).append("</QtyItem>\n");
                }
                if (item.getPrcItem() != null) {
                    xml.append("      <PrcItem>").append(normalizeNumber(BigDecimal.valueOf(item.getPrcItem()))).append("</PrcItem>\n");
                }
                xml.append("      <MontoItem>").append(nullSafe(item.getMontoItem())).append("</MontoItem>\n");
                xml.append("    </Detalle>\n");
            });
        }

        if (dte.getReferences() != null) {
            dte.getReferences().forEach(ref -> {
                xml.append("    <Referencia>\n");
                xml.append("      <NroLinRef>").append(nullSafe(ref.getNroLinRef())).append("</NroLinRef>\n");
                xml.append("      <TpoDocRef>").append(escapeXml(ref.getTpoDocRef())).append("</TpoDocRef>\n");
                if (ref.getFolioRef() != null) {
                    xml.append("      <FolioRef>").append(escapeXml(ref.getFolioRef())).append("</FolioRef>\n");
                }
                if (ref.getFchRef() != null) {
                    xml.append("      <FchRef>").append(ref.getFchRef()).append("</FchRef>\n");
                }
                if (ref.getCodRef() != null) {
                    xml.append("      <CodRef>").append(escapeXml(ref.getCodRef())).append("</CodRef>\n");
                }
                if (ref.getRazonRef() != null) {
                    xml.append("      <RazonRef>").append(escapeXml(ref.getRazonRef())).append("</RazonRef>\n");
                }
                xml.append("    </Referencia>\n");
            });
        }

        xml.append("    <TED version=\"1.0\">\n");
        xml.append("      <DD>\n");
        xml.append("        <RE>").append(escapeXml(dte.getTenant() == null ? "" : dte.getTenant().getRutEmisor())).append("</RE>\n");
        xml.append("        <TD>").append(nullSafe(dte.getTipoDte())).append("</TD>\n");
        xml.append("        <F>").append(nullSafe(dte.getFolio())).append("</F>\n");
        xml.append("        <FE>").append(escapeXml(fchEmisStr)).append("</FE>\n");
        xml.append("        <RR>").append(escapeXml(dte.getRutRecep())).append("</RR>\n");
        xml.append("        <RSR>").append(escapeXml(dte.getRznSocRecep())).append("</RSR>\n");
        xml.append("        <MNT>").append(nullSafe(dte.getMntTotal())).append("</MNT>\n");
        xml.append("        <IT1>").append(escapeXml((dte.getItems() == null || dte.getItems().isEmpty()) ? "ITEM" : dte.getItems().get(0).getNmbItem())).append("</IT1>\n");
        xml.append("        <TSTED>").append(escapeXml(fchEmisStr)).append("T00:00:00</TSTED>\n");
        xml.append("      </DD>\n");
        xml.append("      <FRMT algoritmo=\"SHA1withRSA\">PLACEHOLDER</FRMT>\n");
        xml.append("    </TED>\n");
        xml.append("    <TmstFirma>").append(escapeXml(fchEmisStr)).append("T00:00:00</TmstFirma>\n");
        xml.append("  </Documento>\n");
        xml.append("</DTE>\n");

        return xml.toString();
    }

    private String nullSafe(Object value) {
        return value == null ? "" : escapeXml(String.valueOf(value));
    }

    private String normalizeNumber(BigDecimal value) {
        if (value == null) {
            return "";
        }
        return value.stripTrailingZeros().toPlainString();
    }

    private String escapeXml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
