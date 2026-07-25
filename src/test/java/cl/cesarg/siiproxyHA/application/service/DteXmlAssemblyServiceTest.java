package cl.cesarg.siiproxyHA.application.service;

import cl.cesarg.siiproxyHA.domain.model.Caf;
import cl.cesarg.siiproxyHA.domain.model.Dte;
import cl.cesarg.siiproxyHA.domain.model.DteItem;
import cl.cesarg.siiproxyHA.domain.model.FolioAssignment;
import cl.cesarg.siiproxyHA.domain.model.FolioPool;
import cl.cesarg.siiproxyHA.domain.model.Tenant;
import cl.cesarg.siiproxyHA.domain.port.DteXmlBuilderPort;
import cl.cesarg.siiproxyHA.domain.port.TedGeneratorPort;
import cl.cesarg.siiproxyHA.domain.port.XmlSignerPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DteXmlAssemblyServiceTest {

    @Mock
    private TedGeneratorPort tedGenerator;
    @Mock
    private DteXmlBuilderPort xmlBuilder;
    @Mock
    private DteXmlSigningService xmlSigning;

    private DteXmlAssemblyService service;

    @BeforeEach
    void setUp() {
        service = new DteXmlAssemblyService(tedGenerator, xmlBuilder, xmlSigning);
    }

    @Test
    void mapsAssignedCafAndPassesGeneratedTedToDomBuilder() {
        Dte dte = dte();
        UUID cafId = dte.getFolioAssignment().getFolioPool().getCaf().getId();
        TedGeneratorPort.GeneratedTed ted = new TedGeneratorPort.GeneratedTed(
                ("<TED version=\"1.0\"><DD></DD>"
                        + "<FRMT algoritmo=\"SHA1withRSA\">signed</FRMT></TED>")
                        .getBytes(StandardCharsets.ISO_8859_1),
                "<DD></DD>".getBytes(StandardCharsets.ISO_8859_1),
                LocalDateTime.of(2026, 7, 24, 12, 30, 45),
                cafId
        );
        DteXmlBuilderPort.BuiltDteXml expected = new DteXmlBuilderPort.BuiltDteXml(
                "<EnvioDTE/>".getBytes(StandardCharsets.ISO_8859_1),
                "DTE-105",
                "SetDTE-" + dte.getId(),
                "ISO-8859-1"
        );
        ArgumentCaptor<TedGeneratorPort.TedRequest> tedCaptor =
                ArgumentCaptor.forClass(TedGeneratorPort.TedRequest.class);
        ArgumentCaptor<DteXmlBuilderPort.BuildRequest> buildCaptor =
                ArgumentCaptor.forClass(DteXmlBuilderPort.BuildRequest.class);
        when(tedGenerator.generate(tedCaptor.capture())).thenReturn(ted);
        when(xmlBuilder.build(buildCaptor.capture())).thenReturn(expected);
        when(xmlSigning.signAll(
                expected,
                dte.getTenant().getId(),
                dte.getRutEnvia()
        )).thenReturn(new XmlSignerPort.SignedXml(
                expected.xml(),
                "#" + expected.setDteId(),
                XmlSignerPort.SignatureTarget.SET_DTE,
                UUID.randomUUID(),
                XmlSignerPort.SignatureProfile.SII_LEGACY_RSA_SHA1
        ));

        DteXmlBuilderPort.BuiltDteXml result = service.build(dte);

        assertArrayEquals(expected.xml(), result.xml());
        assertEquals(expected.documentoId(), result.documentoId());
        assertEquals(expected.setDteId(), result.setDteId());
        assertEquals(expected.encoding(), result.encoding());
        assertEquals(cafId, tedCaptor.getValue().assignedCafId());
        assertEquals(105, tedCaptor.getValue().folio());
        assertEquals(2, tedCaptor.getValue().puntoVenta());
        assertEquals(ted, buildCaptor.getValue().ted());
        assertEquals("Producto principal", buildCaptor.getValue().items().getFirst().name());
        verify(tedGenerator).generate(tedCaptor.getValue());
        verify(xmlBuilder).build(buildCaptor.getValue());
        verify(xmlSigning).signAll(
                expected,
                dte.getTenant().getId(),
                dte.getRutEnvia()
        );
    }

    private Dte dte() {
        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setRutEmisor("76184688-4");
        tenant.setRazonSocial("Empresa");
        tenant.setGiro("Servicios");
        tenant.setActeco("726000");
        tenant.setDireccion("Origen");
        tenant.setComuna("Valparaíso");
        tenant.setFchResol(LocalDate.of(2014, 8, 22));
        tenant.setNroResol(80);

        Caf caf = new Caf();
        caf.setId(UUID.randomUUID());
        FolioPool pool = new FolioPool();
        pool.setCaf(caf);
        FolioAssignment assignment = new FolioAssignment();
        assignment.setPuntoVenta(2);
        assignment.setFolioPool(pool);

        DteItem item = new DteItem();
        item.setNroLinDet(1);
        item.setNmbItem("Producto principal");
        item.setQtyItem(1.0);
        item.setPrcItem(100_000.0);
        item.setMontoItem(100_000L);

        Dte dte = new Dte();
        dte.setId(UUID.randomUUID());
        dte.setTenant(tenant);
        dte.setRutEnvia("10438332-7");
        dte.setTipoDte(33);
        dte.setFolio(105L);
        dte.setFchEmis(LocalDate.of(2026, 7, 24));
        dte.setRutRecep("60803000-K");
        dte.setRznSocRecep("SII");
        dte.setGiroRecep("Gobierno");
        dte.setDirRecep("Santiago");
        dte.setCmnaRecep("Santiago");
        dte.setMntNeto(100_000L);
        dte.setIva(19_000L);
        dte.setMntTotal(119_000L);
        dte.setFolioAssignment(assignment);
        dte.setItems(List.of(item));
        return dte;
    }
}
