package cl.cesarg.siiproxyHA.application.service;

import cl.cesarg.siiproxyHA.domain.model.Dte;
import cl.cesarg.siiproxyHA.domain.port.DteXmlBuilderPort;
import cl.cesarg.siiproxyHA.domain.port.TedGeneratorPort;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * Maps a persisted DTE snapshot and coordinates TED and DOM construction.
 */
@Service
public class DteXmlAssemblyService {

    private final TedGeneratorPort tedGenerator;
    private final DteXmlBuilderPort xmlBuilder;
    private final DteDocumentSigningService documentSigning;

    public DteXmlAssemblyService(
            TedGeneratorPort tedGenerator,
            DteXmlBuilderPort xmlBuilder,
            DteDocumentSigningService documentSigning
    ) {
        this.tedGenerator = tedGenerator;
        this.xmlBuilder = xmlBuilder;
        this.documentSigning = documentSigning;
    }

    /**
     * Builds one EnvioDTE with signed TED and Documento.
     */
    public DteXmlBuilderPort.BuiltDteXml build(Dte dte) {
        validateAssignment(dte);
        TedGeneratorPort.GeneratedTed ted = tedGenerator.generate(tedRequest(dte));
        DteXmlBuilderPort.BuiltDteXml unsigned = xmlBuilder.build(buildRequest(dte, ted));
        byte[] signedXml = documentSigning.sign(
                unsigned,
                dte.getTenant().getId(),
                dte.getRutEnvia()
        ).xml();
        return new DteXmlBuilderPort.BuiltDteXml(
                signedXml,
                unsigned.documentoId(),
                unsigned.setDteId(),
                unsigned.encoding()
        );
    }

    private DteXmlBuilderPort.BuildRequest buildRequest(
            Dte dte,
            TedGeneratorPort.GeneratedTed ted
    ) {
        LocalDate resolutionDate = dte.getTenant().getFchResol() == null
                ? dte.getFchEmis()
                : dte.getTenant().getFchResol();
        int resolutionNumber = dte.getTenant().getNroResol() == null
                ? 0
                : dte.getTenant().getNroResol();
        String receiverAddress = dte.getDirRecep() != null
                ? dte.getDirRecep()
                : dte.getReceptor() == null ? "" : dte.getReceptor().getDireccion();
        String receiverCommune = dte.getCmnaRecep() != null
                ? dte.getCmnaRecep()
                : dte.getReceptor() == null ? "" : dte.getReceptor().getComuna();

        return new DteXmlBuilderPort.BuildRequest(
                dte.getId(),
                new DteXmlBuilderPort.IssuerData(
                        dte.getTenant().getRutEmisor(),
                        dte.getRutEnvia(),
                        dte.getTenant().getRazonSocial(),
                        dte.getTenant().getGiro(),
                        dte.getTenant().getActeco(),
                        dte.getTenant().getDireccion(),
                        dte.getTenant().getComuna(),
                        resolutionDate,
                        resolutionNumber
                ),
                new DteXmlBuilderPort.ReceiverData(
                        dte.getRutRecep(),
                        dte.getRznSocRecep(),
                        dte.getGiroRecep(),
                        receiverAddress,
                        receiverCommune
                ),
                new DteXmlBuilderPort.DocumentData(
                        dte.getTipoDte(),
                        dte.getFolio(),
                        dte.getFchEmis(),
                        dte.getMntNeto(),
                        dte.getTasaIva(),
                        dte.getIva(),
                        dte.getMntTotal()
                ),
                itemData(dte),
                referenceData(dte),
                ted
        );
    }

    private List<DteXmlBuilderPort.ItemData> itemData(Dte dte) {
        if (dte.getItems() == null) {
            return List.of();
        }
        return dte.getItems().stream()
                .map(item -> new DteXmlBuilderPort.ItemData(
                        item.getNroLinDet(),
                        item.getNmbItem(),
                        item.getDscItem(),
                        item.getQtyItem(),
                        item.getPrcItem(),
                        item.getMontoItem()
                ))
                .toList();
    }

    private List<DteXmlBuilderPort.ReferenceData> referenceData(Dte dte) {
        if (dte.getReferences() == null) {
            return List.of();
        }
        return dte.getReferences().stream()
                .map(reference -> new DteXmlBuilderPort.ReferenceData(
                        reference.getNroLinRef(),
                        reference.getTpoDocRef(),
                        reference.getFolioRef(),
                        reference.getFchRef(),
                        reference.getCodRef(),
                        reference.getRazonRef()
                ))
                .toList();
    }

    private TedGeneratorPort.TedRequest tedRequest(Dte dte) {
        String firstItem = dte.getItems() == null || dte.getItems().isEmpty()
                ? "ITEM"
                : dte.getItems().getFirst().getNmbItem();
        return new TedGeneratorPort.TedRequest(
                dte.getTenant().getId(),
                dte.getTenant().getRutEmisor(),
                dte.getTipoDte(),
                dte.getFolioAssignment().getPuntoVenta(),
                dte.getFolio(),
                dte.getFolioAssignment().getFolioPool().getCaf().getId(),
                dte.getFchEmis(),
                dte.getRutRecep(),
                dte.getRznSocRecep(),
                dte.getMntTotal(),
                firstItem
        );
    }

    private void validateAssignment(Dte dte) {
        if (dte == null || dte.getId() == null) {
            throw new IllegalStateException("DTE identity is required for XML construction");
        }
        if (dte.getTenant() == null || dte.getTenant().getId() == null) {
            throw new IllegalStateException("DTE tenant is required for XML construction");
        }
        if (dte.getFolioAssignment() == null
                || dte.getFolioAssignment().getFolioPool() == null
                || dte.getFolioAssignment().getFolioPool().getCaf() == null
                || dte.getFolioAssignment().getFolioPool().getCaf().getId() == null) {
            throw new IllegalStateException(
                    "DTE folio assignment with CAF is required for XML construction"
            );
        }
    }
}
