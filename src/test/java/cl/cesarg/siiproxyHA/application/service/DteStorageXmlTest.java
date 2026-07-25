package cl.cesarg.siiproxyHA.application.service;

import cl.cesarg.siiproxyHA.domain.model.DocumentMetadata;
import cl.cesarg.siiproxyHA.domain.model.DocumentStatus;
import cl.cesarg.siiproxyHA.domain.model.Caf;
import cl.cesarg.siiproxyHA.domain.model.Dte;
import cl.cesarg.siiproxyHA.domain.model.FolioAssignment;
import cl.cesarg.siiproxyHA.domain.model.FolioPool;
import cl.cesarg.siiproxyHA.domain.model.Tenant;
import cl.cesarg.siiproxyHA.domain.port.DocumentoRepositoryPort;
import cl.cesarg.siiproxyHA.domain.port.StoragePort;
import cl.cesarg.siiproxyHA.domain.port.TedGeneratorPort;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DteStorageXmlTest {

    @Test
    void storesEnvioDteWithEmitterAndAuthorizedSenderSnapshots() throws Exception {
        InMemoryDocumentRepository documentRepository = new InMemoryDocumentRepository();
        InMemoryStorage storage = new InMemoryStorage();
        UUID cafId = UUID.randomUUID();
        TedGeneratorPort tedGenerator = request -> new TedGeneratorPort.GeneratedTed(
                ("<TED version=\"1.0\"><DD/><FRMT algoritmo=\"SHA1withRSA\">"
                        + "signed</FRMT></TED>").getBytes(StandardCharsets.ISO_8859_1),
                "<DD/>".getBytes(StandardCharsets.ISO_8859_1),
                LocalDateTime.of(2026, 2, 15, 10, 30, 45),
                cafId
        );
        DteServiceImpl service = new DteServiceImpl(
                documentRepository,
                storage,
                null,
                null,
                null,
                tedGenerator
        );

        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setRutEmisor("76184688-4");
        tenant.setRazonSocial("REYES Y LARENAS LIMITADA");
        tenant.setGiro("VENTA AL POR MENOR");
        tenant.setActeco("726000");
        tenant.setDireccion("Viña del Mar");
        tenant.setComuna("V Región");
        tenant.setFchResol(LocalDate.of(2014, 8, 22));
        tenant.setNroResol(80);

        Dte dte = new Dte();
        dte.setId(UUID.randomUUID());
        dte.setTenant(tenant);
        dte.setRutEnvia("10438332-7");
        dte.setTipoDte(33);
        dte.setFolio(182L);
        dte.setFchEmis(LocalDate.of(2026, 2, 15));
        dte.setRutRecep("60803000-K");
        dte.setRznSocRecep("SERVICIO DE IMPUESTOS INTERNOS");
        dte.setMntNeto(7000L);
        dte.setIva(1330L);
        dte.setMntTotal(8330L);
        Caf caf = new Caf();
        caf.setId(cafId);
        FolioPool pool = new FolioPool();
        pool.setCaf(caf);
        FolioAssignment assignment = new FolioAssignment();
        assignment.setPuntoVenta(1);
        assignment.setFolioPool(pool);
        dte.setFolioAssignment(assignment);

        DocumentMetadata result = service.store(dte);
        String xml = new String(storage.bytes, StandardCharsets.ISO_8859_1);

        assertEquals(DocumentStatus.STORED, result.getStatus());
        assertTrue(xml.contains("<EnvioDTE"));
        assertTrue(xml.contains("<RutEmisor>76184688-4</RutEmisor>"));
        assertTrue(xml.contains("<RutEnvia>10438332-7</RutEnvia>"));
        assertTrue(xml.contains("<FchResol>2014-08-22</FchResol>"));
        assertTrue(xml.contains("<NroResol>80</NroResol>"));
        assertTrue(xml.contains("<FRMT algoritmo=\"SHA1withRSA\">signed</FRMT>"));
    }

    private static class InMemoryStorage implements StoragePort {
        private byte[] bytes;

        @Override
        public String store(String key, InputStream content, long size, String contentType) throws Exception {
            bytes = content.readAllBytes();
            return key;
        }

        @Override
        public byte[] get(String key) {
            return bytes;
        }

        @Override
        public String presignedUrl(String key, int minutes) {
            return key;
        }
    }

    private static class InMemoryDocumentRepository implements DocumentoRepositoryPort {
        private DocumentMetadata metadata;

        @Override
        public DocumentMetadata save(DocumentMetadata meta) {
            metadata = meta;
            return meta;
        }

        @Override
        public Optional<DocumentMetadata> findByDocumentId(String documentId) {
            return metadata == null || !documentId.equals(metadata.getDocumentId())
                    ? Optional.empty()
                    : Optional.of(metadata);
        }
    }
}
